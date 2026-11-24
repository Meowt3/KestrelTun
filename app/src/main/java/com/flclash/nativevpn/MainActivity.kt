package com.flclash.nativevpn

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.flclash.nativevpn.ui.MainViewModel
import com.flclash.nativevpn.ui.screens.DashboardScreen
import com.flclash.nativevpn.ui.screens.ProxiesScreen
import com.flclash.nativevpn.ui.screens.RulesScreen
import com.flclash.nativevpn.ui.screens.SettingsScreen
import com.flclash.nativevpn.ui.theme.FlClashTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpnService()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional */ }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enableHighestRefreshRate()
        requestNotificationPermissionIfNeeded()

        setContent {
            val themeConfig by viewModel.themeConfig.collectAsState()
            val runtimeState by viewModel.runtimeState.collectAsState()

            FlClashTheme(themeMode = themeConfig.mode, palette = themeConfig.palette) {
                var selectedTab by remember { mutableIntStateOf(0) }
                val snackbarHostState = remember { SnackbarHostState() }
                val uiMessage by viewModel.uiMessage.collectAsState()

                LaunchedEffect(uiMessage) {
                    uiMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.consumeMessage()
                    }
                }

                val tabs = remember {
                    listOf(
                        NavTab("Панель", Icons.Rounded.Dashboard),
                        NavTab("Прокси", Icons.Rounded.Dns),
                        NavTab("Сеть", Icons.Rounded.Tune),
                        NavTab("Настройки", Icons.Rounded.Settings)
                    )
                }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = tabs[selectedTab].label,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 21.sp
                                )
                            },
                            actions = {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (runtimeState.isConnected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                )
                                        )
                                        Text(
                                            text = if (runtimeState.isConnected) "Full TUN" else "Ожидание",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (selectedTab == 0) {
                                    IconButton(onClick = { viewModel.selectFastestNow() }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Bolt,
                                            contentDescription = "Быстрейший сервер",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    },
                    floatingActionButton = {
                        if (selectedTab == 0) {
                            FloatingActionButton(
                                onClick = ::toggleVpn,
                                shape = RoundedCornerShape(17.dp),
                                containerColor = if (runtimeState.isConnected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = if (runtimeState.isConnected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (runtimeState.isConnected) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    contentDescription = if (runtimeState.isConnected) "Остановить VPN" else "Запустить VPN",
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 0.dp
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    val proxiesGridState = rememberLazyGridState()
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        when (selectedTab) {
                            0 -> DashboardScreen(viewModel = viewModel, isVisible = true)
                            1 -> ProxiesScreen(viewModel = viewModel, gridState = proxiesGridState)
                            2 -> RulesScreen(viewModel = viewModel)
                            3 -> SettingsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    private fun toggleVpn() {
        if (viewModel.runtimeState.value.isConnected) {
            viewModel.stopVpnService()
            return
        }
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            viewModel.startVpnService()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableHighestRefreshRate()
    }

    @Suppress("DEPRECATION")
    private fun enableHighestRefreshRate() {
        runCatching {
            val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                windowManager.defaultDisplay
            } ?: return
            val active = currentDisplay.mode ?: return
            val best = currentDisplay.supportedModes
                .filter { it.physicalWidth == active.physicalWidth && it.physicalHeight == active.physicalHeight }
                .maxByOrNull { it.refreshRate }
                ?: return
            val lp = window.attributes
            if (lp.preferredDisplayModeId != best.modeId || lp.preferredRefreshRate != best.refreshRate) {
                lp.preferredDisplayModeId = best.modeId
                lp.preferredRefreshRate = best.refreshRate
                window.attributes = lp
            }
        }
    }

    private data class NavTab(val label: String, val icon: ImageVector)
}
