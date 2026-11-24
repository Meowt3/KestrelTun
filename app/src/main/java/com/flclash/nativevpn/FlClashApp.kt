package com.flclash.nativevpn

import android.app.Application
import com.flclash.nativevpn.core.AutoTestScheduler
import com.flclash.nativevpn.core.MihomoApiClient
import com.flclash.nativevpn.core.MihomoProcessManager
import com.flclash.nativevpn.core.PingEngine
import com.flclash.nativevpn.data.SettingsRepository
import com.flclash.nativevpn.vpn.FlClashVpnService

class FlClashApp : Application() {

    lateinit var repository: SettingsRepository
        private set

    lateinit var processManager: MihomoProcessManager
        private set

    lateinit var apiClient: MihomoApiClient
        private set

    lateinit var pingEngine: PingEngine
        private set

    lateinit var autoTestScheduler: AutoTestScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(this)
        processManager = MihomoProcessManager(this)
        apiClient = MihomoApiClient()
        pingEngine = PingEngine(apiClient, processManager, repository)
        autoTestScheduler = AutoTestScheduler(
            context = this,
            repository = repository,
            pingEngine = pingEngine,
            apiClient = apiClient,
            onAutoSelected = { node, latencyMs ->
                FlClashVpnService.updateActiveNode(node.name)
                processManager.appendLog("[AUTO] Выбран быстрейший сервер: ${node.name} (${latencyMs} ms)")
            },
            onStatusLog = { line ->
                processManager.appendLog(line)
            }
        )

        FlClashVpnService.updateBinaryAvailability(
            hasCore = processManager.findMihomoBinary() != null,
            hasTun = com.flclash.nativevpn.vpn.HevTunBridge.isAvailable() || processManager.findTunBinary() != null
        )
    }
}
