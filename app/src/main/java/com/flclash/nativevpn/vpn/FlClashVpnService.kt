package com.flclash.nativevpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import com.flclash.nativevpn.FlClashApp
import com.flclash.nativevpn.MainActivity
import com.flclash.nativevpn.core.MihomoConfigGenerator
import com.flclash.nativevpn.model.CoreEngineMode
import com.flclash.nativevpn.model.TrafficStats
import com.flclash.nativevpn.model.VpnRuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FlClashVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastTransportType: Int = -1
    private val underlyingNetworks = HashSet<Network>()
    @Volatile private var networkLostAtElapsedMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> {
                serviceScope.launch { reloadRunningCore() }
                return START_STICKY
            }
            else -> {
                startForegroundWithNotification("Подключение mihomo...")
                serviceScope.launch { startVpn() }
                return START_STICKY
            }
        }
    }

    private suspend fun startVpn() {
        val app = application as FlClashApp
        val repo = app.repository
        val pm = app.processManager
        val api = app.apiClient

        val hasCore = pm.findMihomoBinary() != null
        val hasTun = HevTunBridge.isAvailable()

        _runtimeState.update {
            it.copy(
                engineMode = CoreEngineMode.STARTING,
                coreBinaryFound = hasCore,
                tunBinaryFound = hasTun,
                statusMessage = "Запуск ядра mihomo..."
            )
        }

        if (!hasCore) {
            _runtimeState.update {
                it.copy(
                    isConnected = false,
                    engineMode = CoreEngineMode.MISSING_CORE_BINARY,
                    statusMessage = "Не найден libmihomo.so в jniLibs/arm64-v8a"
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val yaml = MihomoConfigGenerator.generate(
            allNodes = repo.nodes.value,
            customGroups = repo.customGroups.value,
            activeGroupId = repo.activeGroupId.value,
            routing = repo.routingConfig.value,
            autoPing = repo.autoPingConfig.value,
            repository = repo
        )

        val started = pm.startMihomo(yaml)
        if (!started) {
            _runtimeState.update {
                it.copy(
                    isConnected = false,
                    engineMode = CoreEngineMode.ERROR,
                    statusMessage = "Не удалось запустить процесс mihomo"
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        var coreReady = false
        repeat(20) {
            if (api.isCoreAlive()) {
                coreReady = true
                return@repeat
            }
            delay(150)
        }

        if (!coreReady) {
            pm.stopAll()
            _runtimeState.update {
                it.copy(
                    isConnected = false,
                    engineMode = CoreEngineMode.ERROR,
                    statusMessage = "Ядро mihomo не запустилось (см. лог в Настройках)"
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val exactName = MihomoConfigGenerator.resolveExactNameByIdOrName(
            selectedId = repo.selectedNodeId.value,
            rawName = repo.selectedNodeName.value,
            allNodes = repo.nodes.value
        )
        if (exactName != null) {
            api.selectProxy(MihomoConfigGenerator.MAIN_GROUP_NAME, exactName)
            api.selectProxy("GLOBAL", exactName)
            val actual = api.getSelectedProxy(MihomoConfigGenerator.MAIN_GROUP_NAME)
            pm.appendLog("[PROXY] Активный сервер в ядре: ${actual ?: exactName}")
        }

        HevTunBridge.stop()
        runCatching { vpnInterface?.close() }
        vpnInterface = null

        val builder = Builder()
            .setSession("KestrelTun")
            .setMtu(1500)
            .addAddress("10.0.0.2", 30)
            .addRoute("0.0.0.0", 0)
            .addAddress("fdfe:dcba:9876::1", 126)
            .addRoute("::", 0)
            .addDnsServer(if (hasTun) "198.18.0.2" else "1.1.1.1")

        val split = repo.splitTunnelConfig.value
        if (split.enabled && split.selectedPackages.isNotEmpty()) {
            if (split.bypassSelected) {
                runCatching { builder.addDisallowedApplication(packageName) }
                split.selectedPackages.forEach { pkg ->
                    runCatching { builder.addDisallowedApplication(pkg) }
                }
            } else {
                split.selectedPackages
                    .filter { it != packageName }
                    .forEach { pkg ->
                        runCatching { builder.addAllowedApplication(pkg) }
                    }
            }
        } else {
            runCatching { builder.addDisallowedApplication(packageName) }
        }

        if (!hasTun && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setHttpProxy(
                ProxyInfo.buildDirectProxy("127.0.0.1", MihomoConfigGenerator.MIXED_PORT)
            )
        }

        vpnInterface = builder.establish()
        val fd = vpnInterface?.fd

        val tunStarted = if (fd != null && hasTun) {
            HevTunBridge.start(pm.workDir, fd, MihomoConfigGenerator.MIXED_PORT)
                .onSuccess { pm.appendLog("[TUN] Full TUN запущен (hev-socks5-tunnel + mapdns)") }
                .onFailure { pm.appendLog("[TUN ERROR] ${it.message}") }
                .isSuccess
        } else {
            false
        }

        val finalMode = if (tunStarted) {
            CoreEngineMode.RUNNING_FULL_TUN
        } else {
            CoreEngineMode.RUNNING_PROXY_VPN
        }

        val modeLabel = if (tunStarted) "Подключено (Full TUN)" else "Подключено (Proxy-VPN)"
        val activeDisplay = repo.selectedNodeName.value ?: exactName ?: "PROXY"
        _runtimeState.update {
            it.copy(
                isConnected = true,
                engineMode = finalMode,
                activeNodeName = activeDisplay,
                statusMessage = modeLabel,
                coreBinaryFound = true,
                tunBinaryFound = tunStarted,
                connectedAtElapsedMs = SystemClock.elapsedRealtime()
            )
        }
        updateNotification("$modeLabel • $activeDisplay")

        registerNetworkChangeCallback()
        app.autoTestScheduler.startOrRestart(runImmediately = repo.autoPingConfig.value.testImmediatelyOnStart)
    }

    private suspend fun reloadRunningCore() {
        val app = application as FlClashApp
        val repo = app.repository
        val yaml = MihomoConfigGenerator.generate(
            allNodes = repo.nodes.value,
            customGroups = repo.customGroups.value,
            activeGroupId = repo.activeGroupId.value,
            routing = repo.routingConfig.value,
            autoPing = repo.autoPingConfig.value,
            repository = repo
        )
        val file = app.processManager.writeConfig(yaml)
        if (app.apiClient.isCoreAlive()) {
            app.apiClient.reloadConfig(file.absolutePath)
            val exactName = MihomoConfigGenerator.resolveExactNameByIdOrName(
                selectedId = repo.selectedNodeId.value,
                rawName = repo.selectedNodeName.value,
                allNodes = repo.nodes.value
            )
            if (exactName != null) {
                app.apiClient.selectProxy(MihomoConfigGenerator.MAIN_GROUP_NAME, exactName)
                app.apiClient.selectProxy("GLOBAL", exactName)
            }
            if (repo.autoPingConfig.value.closeConnectionsOnSwitch) {
                app.apiClient.closeAllConnections()
            }
            val actual = app.apiClient.getSelectedProxy(MihomoConfigGenerator.MAIN_GROUP_NAME)
            app.processManager.appendLog("[CORE] Конфиг обновлён на лету (активный: ${actual ?: exactName ?: "-"})")
        }
    }

    private fun stopVpn() {
        val app = application as FlClashApp
        unregisterNetworkChangeCallback()
        app.autoTestScheduler.stop()
        HevTunBridge.stop()
        runCatching { vpnInterface?.close() }
        vpnInterface = null
        app.processManager.stopAll()

        _runtimeState.update {
            it.copy(
                isConnected = false,
                engineMode = CoreEngineMode.STOPPED,
                statusMessage = "Отключено",
                connectedAtElapsedMs = 0L,
                traffic = TrafficStats()
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        unregisterNetworkChangeCallback()
        if (activeInstance === this) activeInstance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWithNotification(text: String) {
        ensureNotificationChannel()
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openPending = PendingIntent.getActivity(this, 0, openIntent, pendingFlags)

        val stopIntent = Intent(this, FlClashVpnService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 1, stopIntent, pendingFlags)

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("KestrelTun")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openPending)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Отключить", stopPending).build())
            .build()
    }


    private fun registerNetworkChangeCallback() {
        unregisterNetworkChangeCallback()
        runCatching {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val lostDurationMs = synchronized(underlyingNetworks) {
                        underlyingNetworks.add(network)
                        if (networkLostAtElapsedMs > 0L) {
                            val d = SystemClock.elapsedRealtime() - networkLostAtElapsedMs
                            networkLostAtElapsedMs = 0L
                            d
                        } else {
                            0L
                        }
                    }
                    if (lostDurationMs >= com.flclash.nativevpn.core.AutoTestScheduler.NETWORK_LOSS_MIN_MS &&
                        _runtimeState.value.isConnected
                    ) {
                        val app = application as FlClashApp
                        serviceScope.launch {
                            if (app.apiClient.isCoreAlive()) {
                                app.apiClient.closeAllConnections()
                                app.processManager.appendLog("[NET] Сеть восстановлена после отвала (${lostDurationMs / 1000}с)")
                            }
                            app.autoTestScheduler.onNetworkRecovered(
                                reason = "восстановление сети",
                                lostDurationMs = lostDurationMs,
                                transportChanged = false
                            )
                        }
                    }
                }

                override fun onLost(network: Network) {
                    synchronized(underlyingNetworks) {
                        underlyingNetworks.remove(network)
                        if (underlyingNetworks.isEmpty() && networkLostAtElapsedMs == 0L) {
                            networkLostAtElapsedMs = SystemClock.elapsedRealtime()
                        }
                    }
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val transport = when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkCapabilities.TRANSPORT_WIFI
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkCapabilities.TRANSPORT_CELLULAR
                        else -> return
                    }
                    if (lastTransportType != -1 && lastTransportType != transport && _runtimeState.value.isConnected) {
                        val app = application as FlClashApp
                        serviceScope.launch {
                            val label = if (transport == NetworkCapabilities.TRANSPORT_WIFI) "Wi-Fi" else "LTE/5G"
                            if (app.apiClient.isCoreAlive()) {
                                app.apiClient.closeAllConnections()
                                app.processManager.appendLog("[NET] Смена сети -> $label, сброшены зависшие соединения")
                            }
                            app.autoTestScheduler.onNetworkRecovered(
                                reason = "смена сети -> $label",
                                lostDurationMs = 0L,
                                transportChanged = true
                            )
                        }
                    }
                    lastTransportType = transport
                }
            }
            cm.registerNetworkCallback(req, cb)
            networkCallback = cb
        }
    }

    private fun unregisterNetworkChangeCallback() {
        val cb = networkCallback ?: return
        networkCallback = null
        lastTransportType = -1
        synchronized(underlyingNetworks) { underlyingNetworks.clear() }
        networkLostAtElapsedMs = 0L
        runCatching {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(cb)
        }
    }

    private fun ensureNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "KestrelTun Service",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.flclash.nativevpn.START"
        const val ACTION_STOP = "com.flclash.nativevpn.STOP"
        const val ACTION_RELOAD = "com.flclash.nativevpn.RELOAD"
        private const val CHANNEL_ID = "flclash_vpn_channel"
        private const val NOTIFICATION_ID = 1042

        @Volatile
        private var activeInstance: FlClashVpnService? = null

        private val _runtimeState = MutableStateFlow(VpnRuntimeState())
        val runtimeState: StateFlow<VpnRuntimeState> = _runtimeState.asStateFlow()

        fun updateActiveNode(nodeName: String) {
            _runtimeState.update { it.copy(activeNodeName = nodeName) }
            val svc = activeInstance
            val state = _runtimeState.value
            if (svc != null && state.isConnected) {
                runCatching { svc.updateNotification("${state.statusMessage} • $nodeName") }
            }
        }

        fun updateBinaryAvailability(hasCore: Boolean, hasTun: Boolean) {
            _runtimeState.update {
                it.copy(coreBinaryFound = hasCore, tunBinaryFound = hasTun)
            }
        }
    }
}
