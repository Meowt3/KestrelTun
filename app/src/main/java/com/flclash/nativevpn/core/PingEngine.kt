package com.flclash.nativevpn.core

import com.flclash.nativevpn.data.SettingsRepository
import com.flclash.nativevpn.model.AutoPingConfig
import com.flclash.nativevpn.model.CoreEngineMode
import com.flclash.nativevpn.model.PingState
import com.flclash.nativevpn.model.ProxyNode
import com.flclash.nativevpn.vpn.FlClashVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class PingEngine(
    private val apiClient: MihomoApiClient,
    private val processManager: MihomoProcessManager,
    private val repository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    private val _pingStates = MutableStateFlow<Map<String, PingState>>(emptyMap())
    val pingStates: StateFlow<Map<String, PingState>> = _pingStates.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _completedCount = MutableStateFlow(0)
    val completedCount: StateFlow<Int> = _completedCount.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _finishedVersion = MutableStateFlow(0)
    val finishedVersion: StateFlow<Int> = _finishedVersion.asStateFlow()

    fun testNodesLive(
        nodes: List<ProxyNode>,
        config: AutoPingConfig,
        onComplete: ((Map<String, PingState>) -> Unit)? = null
    ) {
        activeJob?.cancel()
        if (nodes.isEmpty()) {
            onComplete?.invoke(emptyMap())
            return
        }

        activeJob = scope.launch {
            _isTesting.value = true
            _completedCount.value = 0

            val previousStates = _pingStates.value
            val uniqueNodes = orderNodesForSmartQueue(
                nodes.distinctBy { MihomoConfigGenerator.resolveExactName(it) },
                previousStates
            )
            _totalCount.value = uniqueNodes.size

            val liveMap = ConcurrentHashMap<String, PingState>(previousStates)
            for (node in nodes) {
                liveMap[node.id] = PingState.Testing
            }
            _pingStates.value = HashMap(liveMap)

            // Ensure mihomo core is running for real HTTPS 204 URLTest (unified-delay: true)
            var startedTempCore = false
            var coreAlive = apiClient.isCoreAlive()
            if (!coreAlive && processManager.findMihomoBinary() != null) {
                val yaml = MihomoConfigGenerator.generate(
                    allNodes = repository.nodes.value,
                    customGroups = repository.customGroups.value,
                    activeGroupId = repository.activeGroupId.value,
                    routing = repository.routingConfig.value,
                    autoPing = config,
                    repository = repository
                )
                if (processManager.startMihomo(yaml)) {
                    startedTempCore = true
                    repeat(15) {
                        if (apiClient.isCoreAlive()) {
                            coreAlive = true
                            return@repeat
                        }
                        delay(150)
                    }
                }
            }

            // Safe fast ping: 64 concurrent slots + 3000ms timeout + smart queue (previously alive first)
            val testUrl = config.testUrl.trim().ifBlank { DEFAULT_TEST_URL }
            val timeoutMs = config.timeoutMs.coerceIn(2500, DEFAULT_TIMEOUT_MS)
            val concurrency = if (coreAlive) SAFE_FAST_CONCURRENCY else config.maxConcurrency.coerceIn(8, 24)
            val semaphore = Semaphore(concurrency)
            val doneCounter = AtomicInteger(0)
            val allNodesByExactName = repository.nodes.value.groupBy { MihomoConfigGenerator.resolveExactName(it) }

            val uiFlusher = launch {
                while (isActive) {
                    delay(180)
                    _pingStates.value = HashMap(liveMap)
                    _completedCount.value = doneCounter.get()
                }
            }

            val jobs = uniqueNodes.map { node ->
                async {
                    semaphore.withPermit {
                        val exactName = MihomoConfigGenerator.resolveExactName(node)
                        val latency = if (coreAlive) {
                            apiClient.testProxyDelay(exactName, testUrl, timeoutMs)
                        } else {
                            measureTcpPing(node.server, node.port, timeoutMs)
                        }
                        val state = if (latency != null && latency > 0) {
                            PingState.Success(latency)
                        } else {
                            PingState.Timeout
                        }
                        liveMap[node.id] = state
                        allNodesByExactName[exactName]?.forEach { sibling ->
                            liveMap[sibling.id] = state
                        }
                        doneCounter.incrementAndGet()
                    }
                }
            }

            jobs.awaitAll()
            uiFlusher.cancel()

            val finalSnapshot = HashMap(liveMap)
            _pingStates.value = finalSnapshot
            _completedCount.value = uniqueNodes.size

            val vpnState = FlClashVpnService.runtimeState.value
            if (startedTempCore && !vpnState.isConnected && vpnState.engineMode == CoreEngineMode.STOPPED) {
                processManager.stopAll()
            }
            _isTesting.value = false
            _finishedVersion.value += 1
            onComplete?.invoke(finalSnapshot)
        }
    }

    fun testSingleNode(node: ProxyNode, config: AutoPingConfig) {
        scope.launch {
            _pingStates.update { it + (node.id to PingState.Testing) }
            val coreAlive = apiClient.isCoreAlive()
            val testUrl = config.testUrl.trim().ifBlank { DEFAULT_TEST_URL }
            val exactName = MihomoConfigGenerator.resolveExactName(node)
            val latency = if (coreAlive) {
                apiClient.testProxyDelay(exactName, testUrl, DEFAULT_TIMEOUT_MS)
            } else {
                measureTcpPing(node.server, node.port, config.timeoutMs)
            }
            val result = if (latency != null && latency > 0) PingState.Success(latency) else PingState.Timeout
            val siblings = repository.nodes.value.filter { MihomoConfigGenerator.resolveExactName(it) == exactName }
            _pingStates.update { current ->
                val updated = current.toMutableMap()
                updated[node.id] = result
                siblings.forEach { updated[it.id] = result }
                updated
            }
        }
    }

    fun cancelTest() {
        activeJob?.cancel()
        _isTesting.value = false
    }

    private suspend fun measureTcpPing(host: String, port: Int, timeoutMs: Int): Int? =
        withContext(Dispatchers.IO) {
            runCatching {
                val start = System.currentTimeMillis()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs.coerceIn(500, 3500))
                }
                (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)
            }.getOrNull()
        }

    companion object {
        const val SAFE_FAST_CONCURRENCY = 64
        const val DEFAULT_TIMEOUT_MS = 3000
        const val DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204"

        /**
         * Smart queue order: previously working nodes run first (fastest first) so live results
         * refresh in <1s, while previously timed-out nodes run at the tail.
         */
        fun orderNodesForSmartQueue(
            nodes: List<ProxyNode>,
            previousStates: Map<String, PingState>
        ): List<ProxyNode> {
            if (previousStates.isEmpty()) return nodes
            return nodes.sortedWith(
                compareBy<ProxyNode> { node ->
                    when (previousStates[node.id]) {
                        is PingState.Success -> 0
                        is PingState.Idle, is PingState.Testing, null -> 1
                        is PingState.Timeout -> 2
                    }
                }.thenBy { node ->
                    (previousStates[node.id] as? PingState.Success)?.latencyMs ?: Int.MAX_VALUE
                }
            )
        }

        fun sortNodesByLivePing(
            nodes: List<ProxyNode>,
            pingStates: Map<String, PingState>,
            sortEnabled: Boolean
        ): List<ProxyNode> {
            if (!sortEnabled) return nodes
            return nodes.sortedWith(
                compareBy<ProxyNode> { node ->
                    when (val state = pingStates[node.id]) {
                        is PingState.Success -> 0
                        is PingState.Testing -> 1
                        is PingState.Idle, null -> 2
                        is PingState.Timeout -> 3
                    }
                }.thenBy { node ->
                    when (val state = pingStates[node.id]) {
                        is PingState.Success -> state.latencyMs
                        else -> Int.MAX_VALUE
                    }
                }.thenBy { it.name.lowercase() }
            )
        }
    }
}
