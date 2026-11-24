package com.flclash.nativevpn.vpn

import android.content.Intent
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.flclash.nativevpn.MainActivity

class FlClashTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val isConnected = FlClashVpnService.runtimeState.value.isConnected
        if (isConnected) {
            val stopIntent = Intent(this, FlClashVpnService::class.java).apply {
                action = FlClashVpnService.ACTION_STOP
            }
            startService(stopIntent)
            updateTileState(Tile.STATE_INACTIVE, "KestrelTun")
        } else {
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                val activityIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivityAndCollapse(activityIntent)
            } else {
                val startIntent = Intent(this, FlClashVpnService::class.java).apply {
                    action = FlClashVpnService.ACTION_START
                }
                startForegroundService(startIntent)
                updateTileState(Tile.STATE_ACTIVE, "Подключено")
            }
        }
    }

    private fun refreshTile() {
        val runtime = FlClashVpnService.runtimeState.value
        val state = if (runtime.isConnected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        val subtitle = if (runtime.isConnected) runtime.activeNodeName ?: "Активен" else "Отключён"
        updateTileState(state, subtitle)
    }

    private fun updateTileState(state: Int, label: String) {
        qsTile?.let { tile ->
            tile.state = state
            tile.label = label
            tile.updateTile()
        }
    }
}
