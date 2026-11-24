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
            _totalCount.value = nodes.size

            val liveMap = ConcurrentHashMap<String, PingState>(_pingStates.value)
            for (node in nodes) {
                liveMap[node.id] = PingState.Testing
            }
            _pingStates.value = HashMap(liveMap)

            // Ensure mihomo is running for real HTTP 204 URL latency tests (unified-delay: true)
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
                        delay(200)
                    }
                }
            }

            val timeoutMs = config.timeoutMs.coerceIn(1500, 3500)

            // FAST PATH (FlClashX style): single native Go batch call GET /group/{name}/delay
            // Runs all nodes concurrently inside mihomo goroutines (~2-3s for 571 servers, 0 UI churn!)
            var usedFastGroupPath = false
            if (coreAlive) {
                val activeGroupId = repository.activeGroupId.value
                val targetGroupName = if (activeGroupId == SettingsRepository.GROUP_ALL_ID) {
                    MihomoConfigGenerator.MAIN_GROUP_NAME
                } else {
                    repository.customGroups.value.firstOrNull { it.id == activeGroupId }?.name?.trim()
                        ?: MihomoConfigGenerator.MAIN_GROUP_NAME
                }

                val batchResult = apiClient.testGroupDelay(targetGroupName, config.testUrl, timeoutMs)
                    ?: if (targetGroupName != MihomoConfigGenerator.MAIN_GROUP_NAME) {
                        apiClient.testGroupDelay(MihomoConfigGenerator.MAIN_GROUP_NAME, config.testUrl, timeoutMs)
                    } else null

                if (batchResult != null) {
                    usedFastGroupPath = true
                    for (node in nodes) {
                        val exactName = MihomoConfigGenerator.resolveExactName(node)
                        val ms = batchResult[exactName] ?: batchResult[node.name.trim()]
                        liveMap[node.id] = if (ms != null && ms > 0) {
                            PingState.Success(ms)
                        } else {
                            PingState.Timeout
                        }
                    }
                }
            }

            // Fallback path if core is offline or group endpoint unavailable
            if (!usedFastGroupPath) {
                val concurrency = if (coreAlive) 32 else config.maxConcurrency.coerceIn(8, 24)
                val semaphore = Semaphore(concurrency)
                val doneCounter = AtomicInteger(0)

                val uiFlusher = launch {
                    while (isActive) {
                        delay(400)
                        _pingStates.value = HashMap(liveMap)
                        _completedCount.value = doneCounter.get()
                    }
                }

                val jobs = nodes.map { node ->
                    async {
                        semaphore.withPermit {
                            val latency = if (coreAlive) {
                                apiClient.testProxyDelay(MihomoConfigGenerator.resolveExactName(node), config.testUrl, timeoutMs)
                            } else {
                                measureTcpPing(node.server, node.port, timeoutMs)
                            }
                            liveMap[node.id] = if (latency != null && latency > 0) {
                                PingState.Success(latency)
                            } else {
                                PingState.Timeout
                            }
                            doneCounter.incrementAndGet()
                        }
                    }
                }

                jobs.awaitAll()
                uiFlusher.cancel()
            }

            val finalSnapshot = HashMap(liveMap)
            _pingStates.value = finalSnapshot
            _completedCount.value = nodes.size

            val vpnState = FlClashVpnService.runtimeState.value
            if (startedTempCore && !vpnState.isConnected && vpnState.engineMode == CoreEngineMode.STOPPED) {
                processManager.stopAll()
            }
            _isTesting.value = false
            onComplete?.invoke(finalSnapshot)
        }
    }

    fun testSingleNode(node: ProxyNode, config: AutoPingConfig) {
        scope.launch {
            _pingStates.update { it + (node.id to PingState.Testing) }
            val coreAlive = apiClient.isCoreAlive()
            val latency = if (coreAlive) {
                apiClient.testProxyDelay(MihomoConfigGenerator.resolveExactName(node), config.testUrl, config.timeoutMs)
            } else {
                measureTcpPing(node.server, node.port, config.timeoutMs)
            }
            val result = if (latency != null && latency > 0) PingState.Success(latency) else PingState.Timeout
            _pingStates.update { it + (node.id to result) }
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
