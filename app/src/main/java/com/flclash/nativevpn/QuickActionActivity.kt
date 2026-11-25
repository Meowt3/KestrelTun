package com.flclash.nativevpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.core.content.pm.ShortcutManagerCompat
import com.flclash.nativevpn.model.CoreEngineMode
import com.flclash.nativevpn.vpn.FlClashVpnService

/**
 * Invisible, no-window Activity invoked by Android App Shortcuts and Samsung Modes & Routines.
 * Uses Theme.NoDisplay + empty taskAffinity + singleInstance so the currently active foreground app
 * is never minimized or kicked back to the home screen.
 */
class QuickActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        finishWithoutAnimation()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        finishWithoutAnimation()
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action == ACTION_TOGGLE || action == "${packageName}.action.TOGGLE") {
            runCatching { ShortcutManagerCompat.reportShortcutUsed(this, SHORTCUT_ID_TOGGLE) }
            toggleVpnInBackground()
        }
    }

    private fun toggleVpnInBackground() {
        val state = FlClashVpnService.runtimeState.value
        val isRunningOrStarting = state.isConnected || state.engineMode == CoreEngineMode.STARTING

        if (isRunningOrStarting) {
            val stopIntent = Intent(this, FlClashVpnService::class.java).apply {
                action = FlClashVpnService.ACTION_STOP
            }
            runCatching { startService(stopIntent) }
        } else {
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                // First-time VPN permission has not been granted yet -> open MainActivity once
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(mainIntent)
            } else {
                val startIntent = Intent(this, FlClashVpnService::class.java).apply {
                    action = FlClashVpnService.ACTION_START
                }
                startForegroundService(startIntent)
            }
        }
    }

    private fun finishWithoutAnimation() {
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        const val SHORTCUT_ID_TOGGLE = "toggle_vpn"
        const val ACTION_TOGGLE = "com.kestreltun.vpn.action.TOGGLE"
    }
}
