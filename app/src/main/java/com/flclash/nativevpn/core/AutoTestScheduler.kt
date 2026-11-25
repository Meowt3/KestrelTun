package com.flclash.nativevpn.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.PowerManager
import com.flclash.nativevpn.data.SettingsRepository
import com.flclash.nativevpn.model.PingState
import com.flclash.nativevpn.model.ProxyNode
import com.flclash.nativevpn.vpn.FlClashVpnService
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

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
    private var watchdogJob: Job? = null
    private var networkRecoveryJob: Job? = null
    private val recoveryMutex = Mutex()
    private val manualSelectionVersion = AtomicInteger(0)

    @Volatile
    private var lastEmergencyScanMillis: Long = 0L

    private val _lastAutoRunMillis = MutableStateFlow(0L)
    val lastAutoRunMillis: StateFlow<Long> = _lastAutoRunMillis.asStateFlow()

    private val _lastStatusText = MutableStateFlow("Ожидание")
    val lastStatusText: StateFlow<String> = _lastStatusText.asStateFlow()

    fun notifyManualSelection() {
        manualSelectionVersion.incrementAndGet()
    }

    fun startOrRestart(runImmediately: Boolean = false) {
        loopJob?.cancel()
        watchdogJob?.cancel()
        networkRecoveryJob?.cancel()

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

                val isScreenOn = isScreenInteractive()
                if (currentCfg.pauseWhenScreenOff && !isScreenOn) {
                    _lastStatusText.value = "Пауза (экран выключен)"
                    continue
                }

                cycleCounter += 1
                runBatterySmartCycle(forceFullScan = (cycleCounter % 3 == 0))
            }
        }

        startSmartWatchdog()
    }

    /**
     * Passive zero-traffic stall detector:
     * Reads OS TrafficStats counters every 12s while screen is ON.
     * - Idle (tx == 0, rx == 0) or screen OFF -> sleeps, zero radio wakeups.
     * - Downloading (rx >= 1 KB) -> tunnel alive, skips ping completely.
     * - Stalled (tx >= 2 KB, rx < 1 KB) -> runs 3-strike confirmation on current node before triggering recovery.
     */
    private fun startSmartWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            var prevTx = safeTotalTxBytes()
            var prevRx = safeTotalRxBytes()

            while (isActive) {
                delay(WATCHDOG_POLL_INTERVAL_MS)
                val currentGroupId = repository.activeGroupId.value
                if (!repository.isGroupAuto(currentGroupId)) break

                val nowTx = safeTotalTxBytes()
                val nowRx = safeTotalRxBytes()
                val deltaTx = if (prevTx >= 0L && nowTx >= prevTx) nowTx - prevTx else 0L
                val deltaRx = if (prevRx >= 0L && nowRx >= prevRx) nowRx - prevRx else 0L
                prevTx = nowTx
                prevRx = nowRx

                val cfg = repository.autoPingConfig.value
                if (!cfg.autoRecoverOnDrop) continue
                if (!FlClashVpnService.runtimeState.value.isConnected) continue
                if (pingEngine.isTesting.value) continue
                if (!isScreenInteractive()) continue

                if (isTrafficStalled(deltaTx, deltaRx)) {
                    verifyAndRecoverIfNodeDead(
                        reason = "зависание трафика",
                        requiredStrikes = CONFIRM_STRIKES_REQUIRED,
                        bypassCooldown = false
                    )
                }
            }
        }
    }

    /**
     * Triggered by FlClashVpnService ConnectivityManager callback on transport switch (Wi-Fi <-> LTE)
     * or real network recovery after an outage >= 5s (micro-drops < 5s are ignored).
     */
    fun onNetworkRecovered(
        reason: String,
        lostDurationMs: Long,
        transportChanged: Boolean
    ) {
        val cfg = repository.autoPingConfig.value
        if (!cfg.autoRecoverOnDrop) return
        if (!repository.isGroupAuto(repository.activeGroupId.value)) return
        if (!shouldTriggerNetworkRecovery(lostDurationMs, transportChanged)) return

        networkRecoveryJob?.cancel()
        networkRecoveryJob = scope.launch {
            delay(NETWORK_RECOVER_DEBOUNCE_MS)
            if (!FlClashVpnService.runtimeState.value.isConnected) return@launch
            if (!repository.isGroupAuto(repository.activeGroupId.value)) return@launch
            if (!apiClient.isCoreAlive()) return@launch

            // Real network restoration can bypass previous outage cooldown
            val bypass = lostDurationMs >= NETWORK_LOSS_MIN_MS
            verifyAndRecoverIfNodeDead(
                reason = reason,
                requiredStrikes = NETWORK_SWITCH_STRIKES,
                bypassCooldown = bypass
            )
        }
    }

    private suspend fun verifyAndRecoverIfNodeDead(
        reason: String,
        requiredStrikes: Int,
        bypassCooldown: Boolean
    ) {
        recoveryMutex.withLock {
            val nowMs = System.currentTimeMillis()
            if (!bypassCooldown && isRecoveryCooldownActive(lastEmergencyScanMillis, nowMs)) return
            if (pingEngine.isTesting.value) return
            if (!FlClashVpnService.runtimeState.value.isConnected) return

            val targetGroupId = repository.activeGroupId.value
            if (!repository.isGroupAuto(targetGroupId)) return
            val targetNodes = repository.resolveNodesForGroup(targetGroupId)
            if (targetNodes.isEmpty()) return
            if (!apiClient.isCoreAlive()) return

            // Do not waste scans when device physically has no validated mobile/Wi-Fi internet
            if (!isUnderlyingNetworkValidated()) return

            val startManualVer = manualSelectionVersion.get()
            val cfg = repository.autoPingConfig.value
            val currentId = repository.selectedNodeId.value
            val currentName = repository.selectedNodeName.value
            val currentNode = targetNodes.firstOrNull { it.id == currentId }
                ?: targetNodes.firstOrNull { it.name == currentName }

            if (currentNode != null) {
                val exactName = MihomoConfigGenerator.resolveExactName(currentNode)
                var failures = 0
                for (attempt in 1..requiredStrikes) {
                    if (shouldAbortRecovery(startManualVer)) return
                    if (pingEngine.isTesting.value) return
                    if (!isUnderlyingNetworkValidated()) return

                    val delayMs = apiClient.testProxyDelay(exactName, cfg.testUrl, cfg.timeoutMs)
                    if (delayMs != null && delayMs > 0) {
                        // Node responded -> was only a micro-glitch, no full scan needed
                        return
                    }
                    failures += 1
                    if (attempt < requiredStrikes) {
                        delay(CONFIRM_RETRY_DELAY_MS)
                    }
                }
                if (failures < requiredStrikes) return
            }

            // Confirmed dead while underlying network is validated -> run verified recovery with top-3 fallback & bounded rescans
            lastEmergencyScanMillis = System.currentTimeMillis()
            onStatusLog("[AUTO] Подтверждён обрыв узла ($reason, $requiredStrikes/$requiredStrikes) — запуск восстановления")
            runVerifiedRecoveryLoop(startManualVer)
        }
    }

    /**
     * Two-stage verified recovery:
     * 1) Runs full group scan, then verifies top-3 ranked candidates in-place (1.5s settle + real delay check)
     *    without re-running a heavy full scan.
     * 2) Only if none of top-3 responds (or 0 alive), checks underlying Wi-Fi/mobile network and retries
     *    up to MAX_RECOVERY_RESCANS (2) with backoff (10s -> 25s), then enters 2-minute cooldown.
     */
    private suspend fun runVerifiedRecoveryLoop(startManualVer: Int) {
        val totalAttempts = 1 + MAX_RECOVERY_RESCANS
        for (attempt in 0 until totalAttempts) {
            if (shouldAbortRecovery(startManualVer)) return
            if (!isUnderlyingNetworkValidated()) {
                _lastStatusText.value = "Нет базовой сети"
                onStatusLog("[AUTO] Восстановление остановлено: мобильные данные/Wi-Fi недоступны")
                return
            }

            val targetGroupId = repository.activeGroupId.value
            val targetNodes = repository.resolveNodesForGroup(targetGroupId)
            if (targetNodes.isEmpty()) return
            val cfg = repository.autoPingConfig.value

            val label = if (attempt == 0) {
                "Обрыв: поиск сервера..."
            } else {
                "Повтор теста ($attempt/$MAX_RECOVERY_RESCANS)..."
            }
            _lastStatusText.value = label
            _lastAutoRunMillis.value = System.currentTimeMillis()

            val results = runFullScanAndAwait(targetNodes, cfg)
            if (shouldAbortRecovery(startManualVer)) return

            val ranked = rankAliveNodes(targetNodes, results)
            val candidates = ranked.take(POST_VERIFY_TOP_CANDIDATES)

            for ((idx, pair) in candidates.withIndex()) {
                val (candidate, measuredMs) = pair
                if (shouldAbortRecovery(startManualVer)) return
                if (!isUnderlyingNetworkValidated()) return

                applySelectionAndWait(candidate, measuredMs)
                delay(POST_VERIFY_SETTLE_MS)
                if (shouldAbortRecovery(startManualVer)) return

                val exactName = MihomoConfigGenerator.resolveExactName(candidate)
                val verifiedMs = apiClient.testProxyDelay(exactName, cfg.testUrl, cfg.timeoutMs)
                if (verifiedMs != null && verifiedMs > 0) {
                    _lastStatusText.value = "${candidate.name} (${verifiedMs} мс)"
                    onStatusLog("[AUTO] Соединение восстановлено: ${candidate.name} (${verifiedMs} ms, кандидат #${idx + 1})")
                    return
                } else {
                    onStatusLog("[AUTO] Кандидат #${idx + 1} (${candidate.name}) не прошёл пост-проверку")
                }
            }

            if (attempt < MAX_RECOVERY_RESCANS) {
                if (!isUnderlyingNetworkValidated()) {
                    _lastStatusText.value = "Нет базовой сети"
                    return
                }
                val backoffMs = backoffDelayForRetry(attempt)
                _lastStatusText.value = "Пауза ${backoffMs / 1000}с перед повтором..."
                onStatusLog("[AUTO] Топ-${candidates.size} не ответили — повтор через ${backoffMs / 1000}с")
                delay(backoffMs)
            }
        }

        lastEmergencyScanMillis = System.currentTimeMillis()
        _lastStatusText.value = "Нет рабочих серверов"
        onStatusLog("[AUTO] Попытки восстановления исчерпаны — пауза ${RECOVERY_COOLDOWN_MS / 1000}с")
    }

    private suspend fun runFullScanAndAwait(
        nodes: List<ProxyNode>,
        cfg: com.flclash.nativevpn.model.AutoPingConfig
    ): Map<String, PingState> {
        val deferred = CompletableDeferred<Map<String, PingState>>()
        pingEngine.testNodesLive(nodes, cfg) { results ->
            deferred.complete(results)
        }
        return deferred.await()
    }

    private fun shouldAbortRecovery(startManualVer: Int): Boolean {
        if (!FlClashVpnService.runtimeState.value.isConnected) return true
        if (!repository.isGroupAuto(repository.activeGroupId.value)) return true
        if (manualSelectionVersion.get() != startManualVer) return true
        val cfg = repository.autoPingConfig.value
        if (!cfg.autoRecoverOnDrop) return true
        if (cfg.pauseWhenScreenOff && !isScreenInteractive()) return true
        return false
    }

    private fun isScreenInteractive(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isInteractive != false
    }

    private fun isUnderlyingNetworkValidated(): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            @Suppress("DEPRECATION")
            val networks = cm.allNetworks
            if (networks.isEmpty()) return false
            networks.any { net ->
                val caps = cm.getNetworkCapabilities(net) ?: return@any false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }.getOrDefault(true)
    }

    private fun safeTotalTxBytes(): Long = runCatching {
        val v = TrafficStats.getTotalTxBytes()
        if (v == TrafficStats.UNSUPPORTED.toLong()) -1L else v
    }.getOrDefault(-1L)

    private fun safeTotalRxBytes(): Long = runCatching {
        val v = TrafficStats.getTotalRxBytes()
        if (v == TrafficStats.UNSUPPORTED.toLong()) -1L else v
    }.getOrDefault(-1L)

    fun markSelected(node: ProxyNode, latencyMs: Int) {
        _lastStatusText.value = "${node.name} (${latencyMs} мс)"
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        networkRecoveryJob?.cancel()
        networkRecoveryJob = null
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
        val best = rankAliveNodes(activeNodes, results).firstOrNull() ?: return null

        applySelection(best.first, best.second)
        return best
    }

    private fun rankAliveNodes(
        nodes: List<ProxyNode>,
        results: Map<String, PingState>
    ): List<Pair<ProxyNode, Int>> = nodes.mapNotNull { node ->
        val state = results[node.id] as? PingState.Success ?: return@mapNotNull null
        node to state.latencyMs
    }.sortedWith(compareBy<Pair<ProxyNode, Int>> { it.second }.thenBy { it.first.name.lowercase() })

    private fun selectBestNode(
        nodes: List<ProxyNode>,
        results: Map<String, PingState>,
        toleranceMs: Int
    ) {
        val ranked = rankAliveNodes(nodes, results)
        val best = ranked.firstOrNull() ?: run {
            _lastStatusText.value = "Нет рабочих серверов"
            return
        }
        val currentId = repository.selectedNodeId.value
        val currentName = repository.selectedNodeName.value
        val currentNode = nodes.firstOrNull { it.id == currentId }
            ?: nodes.firstOrNull { it.name == currentName }

        // Always switch to the #1 fastest server whenever #1 changes (or current is unset/not in group)
        val isDifferentBest = currentNode == null || best.first.id != currentNode.id
        if (isDifferentBest) {
            applySelection(best.first, best.second)
        }
        _lastStatusText.value = "${best.first.name} (${best.second} мс)"
    }

    private suspend fun applySelectionAndWait(node: ProxyNode, latencyMs: Int) {
        repository.setSelectedNode(node)
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
        onAutoSelected(node, latencyMs)
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

    companion object {
        const val WATCHDOG_POLL_INTERVAL_MS = 12_000L
        const val STALL_MIN_TX_BYTES = 2_048L
        const val STALL_MAX_RX_BYTES = 1_024L
        const val CONFIRM_STRIKES_REQUIRED = 3
        const val NETWORK_SWITCH_STRIKES = 2
        const val CONFIRM_RETRY_DELAY_MS = 3_500L
        const val POST_VERIFY_SETTLE_MS = 1_500L
        const val POST_VERIFY_TOP_CANDIDATES = 3
        const val MAX_RECOVERY_RESCANS = 2
        const val RECOVERY_COOLDOWN_MS = 120_000L
        const val NETWORK_LOSS_MIN_MS = 5_000L
        const val NETWORK_RECOVER_DEBOUNCE_MS = 3_000L

        private val RECOVERY_BACKOFF_MS = longArrayOf(10_000L, 25_000L)

        fun backoffDelayForRetry(retryIndex: Int): Long =
            RECOVERY_BACKOFF_MS[retryIndex.coerceIn(0, RECOVERY_BACKOFF_MS.lastIndex)]

        fun isTrafficStalled(deltaTxBytes: Long, deltaRxBytes: Long): Boolean =
            deltaTxBytes >= STALL_MIN_TX_BYTES && deltaRxBytes < STALL_MAX_RX_BYTES

        fun shouldTriggerNetworkRecovery(lostDurationMs: Long, transportChanged: Boolean): Boolean =
            transportChanged || lostDurationMs >= NETWORK_LOSS_MIN_MS

        fun isRecoveryCooldownActive(lastRecoveryMs: Long, nowMs: Long): Boolean =
            lastRecoveryMs > 0L && (nowMs - lastRecoveryMs) in 0L until RECOVERY_COOLDOWN_MS
    }
}
