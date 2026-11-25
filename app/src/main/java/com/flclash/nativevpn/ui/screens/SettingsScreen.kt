package com.flclash.nativevpn.ui.screens

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flclash.nativevpn.model.AppThemeMode
import com.flclash.nativevpn.model.AppThemePalette
import com.flclash.nativevpn.model.GeminiBypassMode
import com.flclash.nativevpn.ui.MainViewModel
import com.flclash.nativevpn.ui.components.cardBorderStroke
import com.flclash.nativevpn.ui.components.formatBytes
import kotlin.math.roundToInt

private data class PastelSwatch(
    val palette: AppThemePalette,
    val color: Color,
    val isDynamic: Boolean = false
)

@Composable
private fun SettingsIconBadge(icon: ImageVector, active: Boolean = true) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    var settingsTab by remember { mutableIntStateOf(0) } // 0 = Маршруты, 1 = Приложения, 2 = Авто, 3 = Тема

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // M3 Expressive Sliding-Pill Sub-Tab Switcher
        val subTabs = remember { listOf("Маршруты", "Прил.", "Авто", "Тема") }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = cardBorderStroke(),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                val segWidth = maxWidth / subTabs.size
                val segOffset by animateDpAsState(
                    targetValue = segWidth * settingsTab,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "settings_pill_offset"
                )
                Box(
                    modifier = Modifier
                        .offset(x = segOffset)
                        .width(segWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
                Row(modifier = Modifier.fillMaxSize()) {
                    subTabs.forEachIndexed { idx, label ->
                        val selected = settingsTab == idx
                        val labelColor by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(200),
                            label = "set_tab_color_$idx"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { settingsTab = idx },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = labelColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        AnimatedContent(
            targetState = settingsTab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (fadeIn(tween(220)) + slideInHorizontally(spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) { dir * it / 8 }) togetherWith
                    (fadeOut(tween(150)) + slideOutHorizontally(tween(160)) { -dir * it / 12 })
            },
            label = "settings_tab_transition",
            modifier = Modifier.weight(1f)
        ) { tab ->
            when (tab) {
                0 -> RoutesSettingsTab(viewModel)
                1 -> AppsSettingsTab(viewModel)
                2 -> AutoSettingsTab(viewModel)
                else -> ThemeSettingsTab(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutesSettingsTab(viewModel: MainViewModel) {
    val routing by viewModel.routingConfig.collectAsState()
    val connections by viewModel.connections.collectAsState()
    var showRuDnsField by remember { mutableStateOf(false) }
    var showCustomDomains by remember { mutableStateOf(routing.customProxyDomains.isNotEmpty() || routing.customDirectDomains.isNotEmpty()) }
    var showYamlSheet by remember { mutableStateOf(false) }
    var showConnectionsSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // AdBlock
        Card(
            shape = RoundedCornerShape(28.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsIconBadge(Icons.Rounded.Block, routing.adBlockEnabled)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Блокировка рекламы", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Скрывает баннеры и трекеры", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = routing.adBlockEnabled, onCheckedChange = viewModel::toggleAdBlock)
                }
                AnimatedVisibility(visible = routing.adBlockEnabled, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        SettingSwitchRow(
                            title = "Расширенные базы (.mrs)",
                            subtitle = "Полные списки фильтров MetaCubeX",
                            checked = routing.useRuleProviders,
                            onCheckedChange = { enabled -> viewModel.updateRoutingFields { it.copy(useRuleProviders = enabled) } }
                        )
                    }
                }
            }
        }

        // Gemini & AI Bypass
        Card(
            shape = RoundedCornerShape(28.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsIconBadge(Icons.Rounded.AutoAwesome, routing.geminiBypassEnabled)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Обход для Gemini и AI", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Стабильная работа Gemini и AI Studio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = routing.geminiBypassEnabled, onCheckedChange = viewModel::toggleGeminiBypass)
                }
                AnimatedVisibility(visible = routing.geminiBypassEnabled, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = routing.geminiBypassMode == GeminiBypassMode.PROXY_DOH,
                                onClick = { viewModel.setGeminiBypassMode(GeminiBypassMode.PROXY_DOH) },
                                label = { Text("Через VPN (рек.)") }
                            )
                            FilterChip(
                                selected = routing.geminiBypassMode == GeminiBypassMode.CUSTOM_DNS,
                                onClick = { viewModel.setGeminiBypassMode(GeminiBypassMode.CUSTOM_DNS) },
                                label = { Text("Свой DNS") }
                            )
                        }
                        if (routing.geminiBypassMode == GeminiBypassMode.CUSTOM_DNS) {
                            OutlinedTextField(
                                value = routing.geminiCustomDns,
                                onValueChange = { dns -> viewModel.updateRoutingFields { it.copy(geminiCustomDns = dns) } },
                                label = { Text("Адрес DNS / DoH для Gemini") },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Russian Sites Direct Bypass
        Card(
            shape = RoundedCornerShape(28.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsIconBadge(Icons.Rounded.Public, routing.ruBypassEnabled)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Российские сайты напрямую", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(".ru, банки, Госуслуги и маркетплейсы без VPN", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = routing.ruBypassEnabled, onCheckedChange = viewModel::toggleRuBypass)
                }
                if (routing.ruBypassEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showRuDnsField = !showRuDnsField }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Прямой DNS: ${routing.directDns}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Icon(if (showRuDnsField) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AnimatedVisibility(visible = showRuDnsField) {
                        OutlinedTextField(
                            value = routing.directDns,
                            onValueChange = { dns -> viewModel.updateRoutingFields { it.copy(directDns = dns) } },
                            label = { Text("DNS для прямых сайтов") },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Custom Domains
        Card(
            shape = RoundedCornerShape(28.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        ) {
            var proxyText by remember(routing.customProxyDomains) { mutableStateOf(routing.customProxyDomains.joinToString(", ")) }
            var directText by remember(routing.customDirectDomains) { mutableStateOf(routing.customDirectDomains.joinToString(", ")) }
            val totalCustom = routing.customProxyDomains.size + routing.customDirectDomains.size

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCustomDomains = !showCustomDomains },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsIconBadge(Icons.Rounded.Dns, totalCustom > 0)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Свои правила для сайтов", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (totalCustom > 0) "Добавлено доменов: $totalCustom"
                            else "Сайты всегда через VPN или напрямую",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(if (showCustomDomains) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedVisibility(visible = showCustomDomains) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = proxyText,
                            onValueChange = { proxyText = it },
                            label = { Text("Всегда через VPN") },
                            placeholder = { Text("youtube.com, openai.com") },
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = directText,
                            onValueChange = { directText = it },
                            label = { Text("Всегда напрямую") },
                            placeholder = { Text("sberbank.ru, gosuslugi.ru") },
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { viewModel.saveCustomDomains(proxyText, directText) },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Сохранить правила", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Connections & YAML row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    viewModel.refreshConnections()
                    showConnectionsSheet = true
                },
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Lan, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Соединения", maxLines = 1)
            }
            OutlinedButton(
                onClick = { showYamlSheet = true },
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("config.yaml", maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showConnectionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showConnectionsSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 380.dp, max = 620.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Активные соединения (${connections.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { showConnectionsSheet = false }) { Icon(Icons.Rounded.Close, contentDescription = "Закрыть") }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { viewModel.refreshConnections() }, shape = RoundedCornerShape(999.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Обновить")
                    }
                    OutlinedButton(onClick = { viewModel.closeConnectionsNow() }, shape = RoundedCornerShape(999.dp), modifier = Modifier.weight(1f)) {
                        Text("Сбросить все")
                    }
                }
                if (connections.isEmpty()) {
                    Text("Активных соединений нет (или VPN отключён).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        items(connections, key = { it.id }) { conn ->
                            Card(shape = RoundedCornerShape(20.dp), border = cardBorderStroke(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(conn.host, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${conn.network} • ${conn.chain}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("↓${formatBytes(conn.downloadBytes)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showYamlSheet) {
        val yaml = remember { viewModel.generatePreviewYaml() }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showYamlSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 420.dp, max = 660.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("config.yaml (mihomo)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { showYamlSheet = false }) { Icon(Icons.Rounded.Close, contentDescription = "Закрыть") }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(yaml.take(12000), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AppsSettingsTab(viewModel: MainViewModel) {
    val splitTunnel by viewModel.splitTunnelConfig.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    var search by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadInstalledApps() }

    val selectedSet = remember(splitTunnel.selectedPackages) { splitTunnel.selectedPackages.toSet() }
    val filtered = remember(installedApps, search, selectedSet) {
        val q = search.trim()
        val base = if (q.isEmpty()) installedApps
        else installedApps.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
        base.sortedWith(compareByDescending<com.flclash.nativevpn.model.InstalledAppItem> { it.packageName in selectedSet }.thenBy { it.label.lowercase() })
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            shape = RoundedCornerShape(28.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.animateContentSize()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsIconBadge(Icons.Rounded.Apps, splitTunnel.enabled)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Разделение по приложениям", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (splitTunnel.enabled) "Выбрано: ${splitTunnel.selectedPackages.size} (${if (splitTunnel.bypassSelected) "в обход VPN" else "только VPN"})"
                            else "Пускайте банки и нужные программы в обход VPN",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = splitTunnel.enabled, onCheckedChange = { enabled -> viewModel.updateSplitTunnel { it.copy(enabled = enabled) } })
                }

                Button(
                    onClick = { viewModel.smartSelectRuApps() },
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Автовыбор РФ-приложений", fontWeight = FontWeight.SemiBold)
                }

                if (splitTunnel.enabled) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = splitTunnel.bypassSelected,
                            onClick = { viewModel.updateSplitTunnel { it.copy(bypassSelected = true) } },
                            label = { Text("В обход VPN") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !splitTunnel.bypassSelected,
                            onClick = { viewModel.updateSplitTunnel { it.copy(bypassSelected = false) } },
                            label = { Text("Только выбранные") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (splitTunnel.enabled) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Поиск приложения...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Очистить") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth()
            )
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered, key = { it.packageName }) { appItem ->
                    val checked = appItem.packageName in selectedSet
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = if (checked) selectedSet - appItem.packageName else selectedSet + appItem.packageName
                                viewModel.updateSplitTunnel { it.copy(selectedPackages = next.toList()) }
                            },
                        shape = RoundedCornerShape(20.dp),
                        border = cardBorderStroke(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    val next = if (isChecked) selectedSet + appItem.packageName else selectedSet - appItem.packageName
                                    viewModel.updateSplitTunnel { it.copy(selectedPackages = next.toList()) }
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(appItem.label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(appItem.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoSettingsTab(viewModel: MainViewModel) {
    val autoPing by viewModel.autoPingConfig.collectAsState()
    val customGroups by viewModel.customGroups.collectAsState()
    val logs by viewModel.logs.collectAsState()
    var showAdvancedAuto by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsIconBadge(Icons.Rounded.Bolt, autoPing.enabled)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Автотуннелинг", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Автоматически выбирает самый быстрый сервер", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = autoPing.enabled, onCheckedChange = { checked -> viewModel.updateAutoPing { it.copy(enabled = checked) } })
                }

                if (autoPing.enabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    Text("Откуда выбирать сервер", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = autoPing.autoModeGroupId == null,
                            onClick = { viewModel.updateAutoPing { it.copy(autoModeGroupId = null) } },
                            label = { Text("Активная вкладка") }
                        )
                        customGroups.forEach { group ->
                            FilterChip(
                                selected = autoPing.autoModeGroupId == group.id,
                                onClick = { viewModel.updateAutoPing { it.copy(autoModeGroupId = group.id) } },
                                label = { Text(group.name) }
                            )
                        }
                    }

                    Text("Интервал проверки", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 30, 60).forEach { minutes ->
                            FilterChip(
                                selected = autoPing.intervalMinutes == minutes,
                                onClick = { viewModel.updateAutoPing { it.copy(intervalMinutes = minutes) } },
                                label = { Text("$minutes мин") }
                            )
                        }
                    }
                }

                TextButton(onClick = { showAdvancedAuto = !showAdvancedAuto }, modifier = Modifier.align(Alignment.End)) {
                    Text(if (showAdvancedAuto) "Скрыть тонкие настройки" else "Тонкие настройки пинга и батареи")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(if (showAdvancedAuto) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                }

                AnimatedVisibility(visible = showAdvancedAuto) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingSwitchRow(
                            title = "Пауза при выключенном экране",
                            subtitle = "Экономит батарею в режиме ожидания",
                            checked = autoPing.pauseWhenScreenOff,
                            onCheckedChange = { checked -> viewModel.updateAutoPing { it.copy(pauseWhenScreenOff = checked) } }
                        )
                        SettingSwitchRow(
                            title = "Сначала проверять текущий сервер",
                            subtitle = "Если он быстрый — пропускает полный перебор",
                            checked = autoPing.smartCheckCurrentFirst,
                            onCheckedChange = { checked -> viewModel.updateAutoPing { it.copy(smartCheckCurrentFirst = checked) } }
                        )
                        SettingSwitchRow(
                            title = "Сортировать во время пинга",
                            subtitle = "Поднимает быстрые серверы наверх в реальном времени",
                            checked = autoPing.sortLiveDuringTest,
                            onCheckedChange = { checked -> viewModel.updateAutoPing { it.copy(sortLiveDuringTest = checked) } }
                        )
                        SettingSwitchRow(
                            title = "Сброс соединений при смене сервера",
                            subtitle = "Мгновенно переводит открытые приложения",
                            checked = autoPing.closeConnectionsOnSwitch,
                            onCheckedChange = { checked -> viewModel.updateAutoPing { it.copy(closeConnectionsOnSwitch = checked) } }
                        )
                        Text("Порог переключения: ${autoPing.toleranceMs} мс", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(value = autoPing.toleranceMs.toFloat(), onValueChange = { v -> viewModel.updateAutoPing { it.copy(toleranceMs = v.roundToInt()) } }, valueRange = 10f..150f)
                        OutlinedTextField(
                            value = autoPing.testUrl,
                            onValueChange = { url -> viewModel.updateAutoPing { it.copy(testUrl = url) } },
                            label = { Text("URL проверки задержки (HTTPS 204)") },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { showLogsDialog = true },
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Журнал ядра mihomo (${logs.size})")
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showLogsDialog) {
        val logSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showLogsDialog = false },
            sheetState = logSheetState,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 420.dp, max = 660.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Журнал ядра mihomo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { viewModel.clearLogs() }) { Text("Очистить") }
                        IconButton(onClick = { showLogsDialog = false }) { Icon(Icons.Rounded.Close, contentDescription = "Закрыть") }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { viewModel.downloadLogsToDevice() }, shape = RoundedCornerShape(999.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Скачать", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            val text = viewModel.buildDiagnosticLogText()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "KestrelTun mihomo log")
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            runCatching { context.startActivity(Intent.createChooser(sendIntent, "Отправить лог")) }
                        },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Поделиться", maxLines = 1)
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (logs.isEmpty()) "Логов пока нет." else logs.takeLast(120).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeSettingsTab(viewModel: MainViewModel) {
    val themeConfig by viewModel.themeConfig.collectAsState()

    val swatches = remember {
        listOf(
            PastelSwatch(AppThemePalette.DYNAMIC, Color(0xFFA8C7FA), isDynamic = true),
            PastelSwatch(AppThemePalette.OCEAN, Color(0xFFA8C7FA)),
            PastelSwatch(AppThemePalette.EMERALD, Color(0xFFA2DEC0)),
            PastelSwatch(AppThemePalette.TEAL, Color(0xFF9ADCD7)),
            PastelSwatch(AppThemePalette.VIOLET, Color(0xFFC9B8FA)),
            PastelSwatch(AppThemePalette.CRIMSON, Color(0xFFF8C4A6)),
            PastelSwatch(AppThemePalette.ROSE, Color(0xFFF4B6D0)),
            PastelSwatch(AppThemePalette.AMBER, Color(0xFFF2DCA2)),
            PastelSwatch(AppThemePalette.MONOCHROME, Color(0xFFD6DCE5))
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Theme Mode Card
        Card(
            shape = RoundedCornerShape(28.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsIconBadge(Icons.Rounded.Palette, true)
                    Text("Режим темы", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        AppThemeMode.SYSTEM to "Системная",
                        AppThemeMode.LIGHT to "Светлая",
                        AppThemeMode.DARK to "Тёмная",
                        AppThemeMode.AMOLED to "AMOLED"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = themeConfig.mode == mode,
                            onClick = { viewModel.updateTheme { it.copy(mode = mode) } },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        // Clean Visual Pastel Swatches Card — NO subtitle and NO labels under circles!
        Card(
            shape = RoundedCornerShape(28.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Цветовые гаммы", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                // 2 clean rows of pastel circles
                val rows = swatches.chunked(5)
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        rowItems.forEach { item ->
                            val selected = themeConfig.palette == item.palette ||
                                (themeConfig.palette == AppThemePalette.AMOLED && item.palette == AppThemePalette.OCEAN)
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (item.isDynamic) {
                                            Modifier.background(
                                                Brush.sweepGradient(
                                                    listOf(
                                                        Color(0xFFA8C7FA),
                                                        Color(0xFFC9B8FA),
                                                        Color(0xFFF4B6D0),
                                                        Color(0xFFA2DEC0),
                                                        Color(0xFFA8C7FA)
                                                    )
                                                )
                                            )
                                        } else {
                                            Modifier.background(item.color)
                                        }
                                    )
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.updateTheme { it.copy(palette = item.palette) } },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Выбрано",
                                        tint = Color(0xFF13171F),
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else if (item.isDynamic) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFF13171F),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
