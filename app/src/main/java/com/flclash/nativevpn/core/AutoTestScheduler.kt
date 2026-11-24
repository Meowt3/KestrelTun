package com.flclash.nativevpn.core

import android.content.Context
import android.os.PowerManager
import com.flclash.nativevpn.data.SettingsRepository
import com.flclash.nativevpn.model.PingState
import com.flclash.nativevpn.model.ProxyNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AutoTestScheduler(
    private val context: Context,
    private val repository: SettingsRepository,
    private val pingEngine: PingEngine,
    private val apiClient: MihomoApiClient,
    private val onAutoSelected: (ProxyNode, Int) -> Unit,
    private val onStatusLog: (String) -> Unit = {}
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    private val _lastAutoRunMillis = MutableStateFlow(0L)
    val lastAutoRunMillis: StateFlow<Long> = _lastAutoRunMillis.asStateFlow()

    private val _lastStatusText = MutableStateFlow("Ожидание")
    val lastStatusText: StateFlow<String> = _lastStatusText.asStateFlow()

    fun startOrRestart(runImmediately: Boolean = false) {
        loopJob?.cancel()
        val activeGroupId = repository.activeGroupId.value
        val isAutoGroup = repository.isGroupAuto(activeGroupId)
        if (!isAutoGroup) {
            _lastStatusText.value = "Ручной режим"
            return
        }

        loopJob = scope.launch {
            var cycleCounter = 0
            if (runImmediately) {
                runBatterySmartCycle(forceFullScan = true)
            }
            while (isActive) {
                val currentGroupId = repository.activeGroupId.value
                if (!repository.isGroupAuto(currentGroupId)) break

                val currentCfg = repository.autoPingConfig.value
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isBatterySaver = powerManager?.isPowerSaveMode == true

                val baseMinutes = repository.getGroupAutoIntervalMinutes(currentGroupId).coerceAtLeast(1)
                val effectiveMinutes = if (currentCfg.respectBatterySaver && isBatterySaver) {
                    baseMinutes * 2
                } else {
                    baseMinutes
                }

                delay(effectiveMinutes * 60_000L)
                if (!repository.isGroupAuto(repository.activeGroupId.value)) break

                val isScreenOn = powerManager?.isInteractive != false
                if (currentCfg.pauseWhenScreenOff && !isScreenOn) {
                    _lastStatusText.value = "Пауза (экран выключен)"
                    continue
                }

                cycleCounter += 1
                runBatterySmartCycle(forceFullScan = (cycleCounter % 3 == 0))
            }
        }
    }

    fun markSelected(node: ProxyNode, latencyMs: Int) {
        _lastStatusText.value = "${node.name} (${latencyMs} мс)"
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        _lastStatusText.value = "Остановлен"
    }

    private suspend fun runBatterySmartCycle(forceFullScan: Boolean) {
        val cfg = repository.autoPingConfig.value
        val targetGroupId = repository.activeGroupId.value
        if (!repository.isGroupAuto(targetGroupId)) return

        val targetNodes = repository.resolveNodesForGroup(targetGroupId)
        if (targetNodes.isEmpty()) return

        _lastAutoRunMillis.value = System.currentTimeMillis()

        if (cfg.smartCheckCurrentFirst && !forceFullScan) {
            val currentId = repository.selectedNodeId.value
            val currentName = repository.selectedNodeName.value
            val currentNode = targetNodes.firstOrNull { it.id == currentId }
                ?: targetNodes.firstOrNull { it.name == currentName }
            if (currentNode != null && apiClient.isCoreAlive()) {
                val delayMs = apiClient.testProxyDelay(
                    MihomoConfigGenerator.resolveExactName(currentNode),
                    cfg.testUrl,
                    cfg.timeoutMs
                )
                if (delayMs != null && delayMs in 1..cfg.goodLatencyThresholdMs) {
                    _lastStatusText.value = "Ок: ${currentNode.name} (${delayMs} мс)"
                    onStatusLog("[AUTO] Текущий узел ${currentNode.name} в норме (${delayMs} ms)")
                    return
                }
            }
        }

        _lastStatusText.value = "Авто-тест (${targetNodes.size})..."
        pingEngine.testNodesLive(targetNodes, cfg) { results ->
            selectBestNode(targetNodes, results, if (forceFullScan) 0 else cfg.toleranceMs)
        }
    }

    fun selectFastestFromCurrentResults(): Pair<ProxyNode, Int>? {
        val targetGroupId = repository.activeGroupId.value
        val activeNodes = repository.resolveNodesForGroup(targetGroupId)
        val results = pingEngine.pingStates.value
        val best = activeNodes
            .mapNotNull { node ->
                val state = results[node.id] as? PingState.Success ?: return@mapNotNull null
                node to state.latencyMs
            }
            .minWithOrNull(compareBy<Pair<ProxyNode, Int>> { it.second }.thenBy { it.first.name.lowercase() }) ?: return null

        applySelection(best.first, best.second)
        return best
    }

    private fun selectBestNode(
        nodes: List<ProxyNode>,
        results: Map<String, PingState>,
        toleranceMs: Int
    ) {
        val ranked = nodes.mapNotNull { node ->
            val state = results[node.id] as? PingState.Success ?: return@mapNotNull null
            node to state.latencyMs
        }.sortedWith(compareBy<Pair<ProxyNode, Int>> { it.second }.thenBy { it.first.name.lowercase() })

        val best = ranked.firstOrNull() ?: run {
            _lastStatusText.value = "Нет рабочих серверов"
            return
        }
        val currentId = repository.selectedNodeId.value
        val currentName = repository.selectedNodeName.value
        val currentNode = nodes.firstOrNull { it.id == currentId }
            ?: nodes.firstOrNull { it.name == currentName }
        val currentLatency = currentNode?.let { (results[it.id] as? PingState.Success)?.latencyMs }

        // Always switch to the #1 fastest server whenever #1 changes (or current is unset/not in group)
        val isDifferentBest = currentNode == null || best.first.id != currentNode.id
        if (isDifferentBest) {
            applySelection(best.first, best.second)
        }
        _lastStatusText.value = "${best.first.name} (${best.second} мс)" 
    }

    private fun applySelection(node: ProxyNode, latencyMs: Int) {
        repository.setSelectedNode(node)
        scope.launch {
            if (apiClient.isCoreAlive()) {
                val exactName = MihomoConfigGenerator.resolveExactName(node)
                val ok = apiClient.selectProxy(MihomoConfigGenerator.MAIN_GROUP_NAME, exactName)
                apiClient.selectProxy("GLOBAL", exactName)
                if (repository.autoPingConfig.value.closeConnectionsOnSwitch) {
                    apiClient.closeAllConnections()
                }
                val actual = apiClient.getSelectedProxy(MihomoConfigGenerator.MAIN_GROUP_NAME)
                onStatusLog("[AUTO] Выбран узел: $exactName (ok=$ok, в ядре: ${actual ?: "-"})")
            }
        }
        onAutoSelected(node, latencyMs)
    }
}
