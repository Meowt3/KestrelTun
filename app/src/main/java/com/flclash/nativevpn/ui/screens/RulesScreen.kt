package com.flclash.nativevpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flclash.nativevpn.core.MihomoConfigGenerator
import com.flclash.nativevpn.model.GeminiBypassMode
import com.flclash.nativevpn.ui.MainViewModel
import com.flclash.nativevpn.ui.components.cardBorderStroke
import com.flclash.nativevpn.ui.components.formatBytes

@Composable
fun RulesScreen(viewModel: MainViewModel) {
    var subTab by remember { mutableIntStateOf(0) } // 0 = Правила, 1 = Соединения, 2 = Приложения

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Clean 3-pill sub-navigation bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Правила", "Соединения", "Приложения").forEachIndexed { idx, label ->
                FilterChip(
                    selected = subTab == idx,
                    onClick = { subTab = idx },
                    label = { Text(label, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when (subTab) {
            0 -> RoutingRulesSection(viewModel)
            1 -> ConnectionsSection(viewModel)
            2 -> SplitTunnelSection(viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutingRulesSection(viewModel: MainViewModel) {
    val routing by viewModel.routingConfig.collectAsState()
    var showYamlDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // AdBlock
        Card(
            shape = RoundedCornerShape(22.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AdBlock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Блокировка рекламы и трекеров", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = routing.adBlockEnabled, onCheckedChange = viewModel::toggleAdBlock)
                }
                if (routing.adBlockEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Полные базы MetaCubeX (.mrs)", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = routing.useRuleProviders,
                            onCheckedChange = { enabled -> viewModel.updateRoutingFields { it.copy(useRuleProviders = enabled) } }
                        )
                    }
                }
            }
        }

        // Gemini
        Card(
            shape = RoundedCornerShape(22.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Обход для Gemini", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Приложение Gemini, веб и AI Studio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = routing.geminiBypassEnabled, onCheckedChange = viewModel::toggleGeminiBypass)
                }

                if (routing.geminiBypassEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = routing.geminiBypassMode == GeminiBypassMode.PROXY_DOH,
                            onClick = { viewModel.setGeminiBypassMode(GeminiBypassMode.PROXY_DOH) },
                            label = { Text("Через VPN + DoH") }
                        )
                        FilterChip(
                            selected = routing.geminiBypassMode == GeminiBypassMode.CUSTOM_DNS,
                            onClick = { viewModel.setGeminiBypassMode(GeminiBypassMode.CUSTOM_DNS) },
                            label = { Text("Кастомный DNS") }
                        )
                    }
                    if (routing.geminiBypassMode == GeminiBypassMode.CUSTOM_DNS) {
                        OutlinedTextField(
                            value = routing.geminiCustomDns,
                            onValueChange = { dns -> viewModel.updateRoutingFields { it.copy(geminiCustomDns = dns) } },
                            label = { Text("DNS / DoH для Gemini") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // RU Bypass
        Card(
            shape = RoundedCornerShape(22.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Обход РФ (DIRECT)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(".ru / .рф, банки, Госуслуги, маркетплейсы напрямую", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = routing.ruBypassEnabled, onCheckedChange = viewModel::toggleRuBypass)
                }
                if (routing.ruBypassEnabled) {
                    OutlinedTextField(
                        value = routing.directDns,
                        onValueChange = { dns -> viewModel.updateRoutingFields { it.copy(directDns = dns) } },
                        label = { Text("Прямой DNS для РФ") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Allow LAN
        Card(
            shape = RoundedCornerShape(22.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Раздача по Wi-Fi (Allow LAN)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (routing.allowLan) "${viewModel.getLocalLanIp()}:${MihomoConfigGenerator.MIXED_PORT}"
                        else "Открыть прокси-порт для ПК и других устройств",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (routing.allowLan) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = routing.allowLan,
                    onCheckedChange = { enabled -> viewModel.updateRoutingFields { it.copy(allowLan = enabled) } }
                )
            }
        }

        // Custom Domains (Always VPN / Always Direct)
        Card(
            shape = RoundedCornerShape(22.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            var proxyText by remember(routing.customProxyDomains) {
                mutableStateOf(routing.customProxyDomains.joinToString(", "))
            }
            var directText by remember(routing.customDirectDomains) {
                mutableStateOf(routing.customDirectDomains.joinToString(", "))
            }
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Свои домены", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Через запятую или с новой строки (имеют высший приоритет)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = proxyText,
                    onValueChange = { proxyText = it },
                    label = { Text("Всегда через VPN") },
                    placeholder = { Text("example.com, api.site.org") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = directText,
                    onValueChange = { directText = it },
                    label = { Text("Всегда напрямую") },
                    placeholder = { Text("bank.ru, local.net") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                FilledTonalButton(
                    onClick = { viewModel.saveCustomDomains(proxyText, directText) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Сохранить домены")
                }
            }
        }

        OutlinedButton(
            onClick = { showYamlDialog = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Посмотреть config.yaml")
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showYamlDialog) {
        val yaml = remember { viewModel.generatePreviewYaml() }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showYamlDialog = false },
            sheetState = sheetState,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("config.yaml (mihomo)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { showYamlDialog = false }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = yaml.take(12000),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionsSection(viewModel: MainViewModel) {
    val connections by viewModel.connections.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshConnections()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = { viewModel.refreshConnections() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Обновить (${connections.size})") }
            OutlinedButton(
                onClick = { viewModel.closeConnectionsNow() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Сбросить все") }
        }

        if (connections.isEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                border = cardBorderStroke(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Text(
                    text = "Активных соединений нет (или VPN отключён).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(connections, key = { it.id }) { conn ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        border = cardBorderStroke(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = conn.host,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${conn.network} • ${conn.chain}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "↓${formatBytes(conn.downloadBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitTunnelSection(viewModel: MainViewModel) {
    val splitTunnel by viewModel.splitTunnelConfig.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    var search by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadInstalledApps()
    }

    val filtered = remember(installedApps, search) {
        val q = search.trim()
        if (q.isEmpty()) installedApps
        else installedApps.filter {
            it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Split Tunneling", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Выбрано приложений: ${splitTunnel.selectedPackages.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = splitTunnel.enabled,
                        onCheckedChange = { enabled -> viewModel.updateSplitTunnel { it.copy(enabled = enabled) } }
                    )
                }

                FilledTonalButton(
                    onClick = { viewModel.smartSelectRuApps() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Умный выбор РФ-приложений (банки, Госуслуги, маркетплейсы)")
                }

                if (splitTunnel.enabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = splitTunnel.bypassSelected,
                            onClick = { viewModel.updateSplitTunnel { it.copy(bypassSelected = true) } },
                            label = { Text("Исключить выбранные") }
                        )
                        FilterChip(
                            selected = !splitTunnel.bypassSelected,
                            onClick = { viewModel.updateSplitTunnel { it.copy(bypassSelected = false) } },
                            label = { Text("Только выбранные") }
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
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            val selectedSet = splitTunnel.selectedPackages.toSet()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.packageName }) { appItem ->
                    val checked = appItem.packageName in selectedSet
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = if (checked) selectedSet - appItem.packageName else selectedSet + appItem.packageName
                                viewModel.updateSplitTunnel { it.copy(selectedPackages = next.toList()) }
                            },
                        shape = RoundedCornerShape(14.dp),
                        border = cardBorderStroke(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    val next = if (isChecked) selectedSet + appItem.packageName else selectedSet - appItem.packageName
                                    viewModel.updateSplitTunnel { it.copy(selectedPackages = next.toList()) }
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(appItem.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(appItem.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
