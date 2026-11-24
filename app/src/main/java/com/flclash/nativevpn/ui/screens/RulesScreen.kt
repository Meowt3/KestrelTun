package com.flclash.nativevpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WifiTethering
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flclash.nativevpn.core.MihomoConfigGenerator
import com.flclash.nativevpn.model.GeminiBypassMode
import com.flclash.nativevpn.ui.MainViewModel
import com.flclash.nativevpn.ui.components.cardBorderStroke
import com.flclash.nativevpn.ui.components.formatBytes
import com.flclash.nativevpn.ui.components.formatExpireDate

@Composable
fun RulesScreen(viewModel: MainViewModel) {
    var subTab by remember { mutableIntStateOf(0) } // 0 = Правила и подписки, 1 = Приложения, 2 = Соединения

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Clean segmented pill switcher
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = cardBorderStroke(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Подписки и правила", "Приложения", "Трафик").forEachIndexed { idx, label ->
                    val selected = subTab == idx
                    Surface(
                        onClick = { subTab = idx },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.weight(if (idx == 0) 1.35f else 0.9f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 9.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        when (subTab) {
            0 -> RoutingRulesSection(viewModel)
            1 -> SplitTunnelSection(viewModel)
            2 -> ConnectionsSection(viewModel)
        }
    }
}

@Composable
private fun NetworkSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun RuleIconBadge(icon: ImageVector, active: Boolean) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutingRulesSection(viewModel: MainViewModel) {
    val routing by viewModel.routingConfig.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val clipboard = LocalClipboardManager.current

    var showAddSubDialog by remember { mutableStateOf(false) }
    var showYamlDialog by remember { mutableStateOf(false) }
    var showRuDnsField by remember { mutableStateOf(false) }
    var showCustomDomains by remember { mutableStateOf(routing.customProxyDomains.isNotEmpty() || routing.customDirectDomains.isNotEmpty()) }

    fun smartImportFromClipboard() {
        val clip = clipboard.getText()?.text?.trim().orEmpty()
        if (clip.isEmpty()) {
            showAddSubDialog = true
            return
        }
        if ((clip.startsWith("http://", ignoreCase = true) || clip.startsWith("https://", ignoreCase = true)) && !clip.contains("\n")) {
            viewModel.importSubscriptionFromUrl("", clip)
        } else {
            viewModel.importRawText(clip, "Из буфера")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NetworkSectionHeader("Подписки и серверы")

        // 0. Subscriptions Card (Moved to Network tab!)
        Card(
            shape = RoundedCornerShape(20.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RuleIconBadge(Icons.Rounded.VpnKey, subscriptions.isNotEmpty())
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Мои подписки", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (subscriptions.isEmpty()) "Добавьте ссылку от VPN-провайдера или ключ"
                            else "Активных профилей: ${subscriptions.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (subscriptions.any { it.url.startsWith("http", ignoreCase = true) }) {
                        IconButton(
                            onClick = { viewModel.refreshAllSubscriptions(silent = false) },
                            enabled = !isImporting
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Обновить все")
                        }
                    }
                }

                if (subscriptions.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Как подключиться за 10 секунд:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "1. Скопируйте ссылку подписки или ключ (VLESS, Hysteria2, Trojan, WireGuard, Clash YAML)\n2. Нажмите «Из буфера» или «Добавить» ниже",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    subscriptions.forEach { profile ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        profile.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val exp = formatExpireDate(profile.expireEpochSeconds)
                                    Text(
                                        text = "Серверов: ${profile.nodeCount}" + if (exp.isNotEmpty()) " • до $exp" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    if (profile.url.startsWith("http", ignoreCase = true)) {
                                        IconButton(onClick = { clipboard.setText(AnnotatedString(profile.url)) }) {
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Копировать ссылку", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = { viewModel.refreshSubscription(profile) },
                                            enabled = !isImporting
                                        ) {
                                            Icon(Icons.Rounded.Refresh, contentDescription = "Обновить", modifier = Modifier.size(19.dp))
                                        }
                                    }
                                    IconButton(onClick = { viewModel.deleteSubscription(profile.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Удалить", modifier = Modifier.size(19.dp))
                                    }
                                }
                            }
                            if (profile.totalBytes > 0L) {
                                val progress = (profile.usedBytes.toFloat() / profile.totalBytes.toFloat()).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Text(
                                    text = "Использовано: ${formatBytes(profile.usedBytes)} из ${formatBytes(profile.totalBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showAddSubDialog = true },
                        enabled = !isImporting,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isImporting) "Загрузка..." else "Добавить", maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = { smartImportFromClipboard() },
                        enabled = !isImporting,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Из буфера", maxLines = 1)
                    }
                }

                if (subscriptions.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Автообновление при запуске", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text(
                                "Синхронизирует список серверов при открытии приложения",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = routing.autoUpdateSubscriptionsOnStart,
                            onCheckedChange = { checked ->
                                viewModel.updateRoutingFields { it.copy(autoUpdateSubscriptionsOnStart = checked) }
                            }
                        )
                    }
                }
            }
        }

        NetworkSectionHeader("Защита и умный обход")

        // 1. AdBlock
        Card(
            shape = RoundedCornerShape(20.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RuleIconBadge(Icons.Rounded.Block, routing.adBlockEnabled)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Блокировка рекламы", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Скрывает баннеры и трекеры в браузере и приложениях",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = routing.adBlockEnabled, onCheckedChange = viewModel::toggleAdBlock)
                }
                if (routing.adBlockEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Расширенные базы фильтров (.mrs)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text(
                                "Подгружает полные списки рекламы MetaCubeX",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = routing.useRuleProviders,
                            onCheckedChange = { enabled -> viewModel.updateRoutingFields { it.copy(useRuleProviders = enabled) } }
                        )
                    }
                }
            }
        }

        // 2. Gemini & AI Bypass
        Card(
            shape = RoundedCornerShape(20.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RuleIconBadge(Icons.Rounded.AutoAwesome, routing.geminiBypassEnabled)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Обход для Gemini и AI", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Стабильная работа Gemini, AI Studio и веб-версии",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = routing.geminiBypassEnabled, onCheckedChange = viewModel::toggleGeminiBypass)
                }

                if (routing.geminiBypassEnabled) {
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
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // 3. Russian Sites Direct Bypass
        Card(
            shape = RoundedCornerShape(20.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RuleIconBadge(Icons.Rounded.Public, routing.ruBypassEnabled)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Российские сайты напрямую", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            ".ru / .рф, банки, Госуслуги и маркетплейсы работают без VPN",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = routing.ruBypassEnabled, onCheckedChange = viewModel::toggleRuBypass)
                }
                if (routing.ruBypassEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showRuDnsField = !showRuDnsField }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Прямой DNS: ${routing.directDns}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (showRuDnsField) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (showRuDnsField) {
                        OutlinedTextField(
                            value = routing.directDns,
                            onValueChange = { dns -> viewModel.updateRoutingFields { it.copy(directDns = dns) } },
                            label = { Text("DNS для прямых сайтов (по умолчанию 77.88.8.8)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        NetworkSectionHeader("Свои правила и локальная сеть")

        // 4. Custom Domains
        Card(
            shape = RoundedCornerShape(20.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            var proxyText by remember(routing.customProxyDomains) {
                mutableStateOf(routing.customProxyDomains.joinToString(", "))
            }
            var directText by remember(routing.customDirectDomains) {
                mutableStateOf(routing.customDirectDomains.joinToString(", "))
            }
            val totalCustom = routing.customProxyDomains.size + routing.customDirectDomains.size

            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCustomDomains = !showCustomDomains },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RuleIconBadge(Icons.Rounded.Dns, totalCustom > 0)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Свои правила для сайтов", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (totalCustom > 0) "Добавлено доменов: $totalCustom (высший приоритет)"
                            else "Укажите сайты, которые всегда идут через VPN или напрямую",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = if (showCustomDomains) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (showCustomDomains) {
                    OutlinedTextField(
                        value = proxyText,
                        onValueChange = { proxyText = it },
                        label = { Text("Всегда через VPN") },
                        placeholder = { Text("youtube.com, openai.com") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = directText,
                        onValueChange = { directText = it },
                        label = { Text("Всегда напрямую (без VPN)") },
                        placeholder = { Text("sberbank.ru, gosuslugi.ru") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    FilledTonalButton(
                        onClick = { viewModel.saveCustomDomains(proxyText, directText) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Сохранить правила")
                    }
                }
            }
        }

        // 5. Allow LAN
        Card(
            shape = RoundedCornerShape(20.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            val lanAddress = "${viewModel.getLocalLanIp()}:${MihomoConfigGenerator.MIXED_PORT}"
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RuleIconBadge(Icons.Rounded.WifiTethering, routing.allowLan)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Раздача VPN по Wi-Fi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Позволяет подключить ПК или ТВ через прокси этого телефона",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = routing.allowLan,
                        onCheckedChange = { enabled -> viewModel.updateRoutingFields { it.copy(allowLan = enabled) } }
                    )
                }
                if (routing.allowLan) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("HTTP / SOCKS5 адрес", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(lanAddress, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(lanAddress)) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Копировать", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // 6. Advanced YAML preview button
        OutlinedButton(
            onClick = { showYamlDialog = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Посмотреть конфиг ядра (config.yaml)")
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    // Add Subscription Bottom Sheet
    if (showAddSubDialog) {
        var importTab by remember { mutableIntStateOf(0) } // 0 = По ссылке, 1 = Ключ / YAML
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var rawText by remember { mutableStateOf("") }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showAddSubDialog = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Добавить серверы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { showAddSubDialog = false }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("По ссылке (подписка)" to Icons.Rounded.Link, "Ключ / YAML" to Icons.Rounded.VpnKey)
                            .forEachIndexed { idx, (label, icon) ->
                                val selected = importTab == idx
                                Surface(
                                    onClick = { importTab = idx },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 6.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                    }
                }

                if (importTab == 0) {
                    Text(
                        "Вставьте ссылку на подписку от провайдера — приложение само загрузит все серверы и будет обновлять их.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Ссылка подписки (https://...)") },
                        placeholder = { Text("https://example.com/sub/...") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                val clip = clipboard.getText()?.text?.trim().orEmpty()
                                if (clip.isNotEmpty()) {
                                    if (!clip.startsWith("http", ignoreCase = true) && (clip.contains("://") || clip.contains("proxies:"))) {
                                        rawText = clip
                                        importTab = 1
                                    } else {
                                        url = clip
                                    }
                                }
                            }) {
                                Icon(Icons.Rounded.ContentPaste, contentDescription = "Вставить")
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название (необязательно)") },
                        placeholder = { Text("Например: Основной VPN") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = url.isNotBlank() && !isImporting,
                        onClick = {
                            val trimmed = url.trim()
                            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                                viewModel.importSubscriptionFromUrl(name, trimmed)
                            } else {
                                viewModel.importRawText(trimmed, name.ifBlank { "Импорт" })
                            }
                            showAddSubDialog = false
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(if (isImporting) "Загрузка..." else "Добавить подписку", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Text(
                        "Вставьте один или несколько ключей (VLESS, Hysteria2, Trojan, VMess, WireGuard, SS) или текст Clash YAML.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        label = { Text("Ключи или YAML-конфиг") },
                        placeholder = { Text("vless://... или proxies:") },
                        minLines = 4,
                        maxLines = 7,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название профиля (необязательно)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clip = clipboard.getText()?.text.orEmpty()
                                if (clip.isNotBlank()) rawText = clip
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Вставить")
                        }
                        Button(
                            enabled = rawText.isNotBlank() && !isImporting,
                            onClick = {
                                viewModel.importRawText(rawText, name.ifBlank { "Ручной импорт" })
                                showAddSubDialog = false
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Text("Импортировать", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
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
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Обновить (${connections.size})")
            }
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
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Нет активных соединений", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Здесь отображаются сайты и приложения, которые прямо сейчас передают данные через VPN или напрямую.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

    val selectedSet = remember(splitTunnel.selectedPackages) { splitTunnel.selectedPackages.toSet() }
    val filtered = remember(installedApps, search, selectedSet) {
        val q = search.trim()
        val base = if (q.isEmpty()) installedApps
        else installedApps.filter {
            it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
        }
        base.sortedWith(compareByDescending<com.flclash.nativevpn.model.InstalledAppItem> { it.packageName in selectedSet }.thenBy { it.label.lowercase() })
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
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RuleIconBadge(Icons.Rounded.Apps, splitTunnel.enabled)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Разделение по приложениям", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (splitTunnel.enabled) {
                                val modeWord = if (splitTunnel.bypassSelected) "без VPN" else "только через VPN"
                                "Выбрано: ${splitTunnel.selectedPackages.size} ($modeWord)"
                            } else {
                                "Позволяет пускать банки и нужные приложения в обход VPN"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = splitTunnel.enabled,
                        onCheckedChange = { enabled -> viewModel.updateSplitTunnel { it.copy(enabled = enabled) } }
                    )
                }

                FilledTonalButton(
                    onClick = { viewModel.smartSelectRuApps() },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Автовыбор РФ-приложений (банки, Госуслуги, маркетплейсы)", style = MaterialTheme.typography.labelLarge)
                }

                if (splitTunnel.enabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

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
                        colors = CardDefaults.cardColors(
                            containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
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
