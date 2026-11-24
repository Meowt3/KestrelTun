package com.flclash.nativevpn.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flclash.nativevpn.model.AppThemeMode
import com.flclash.nativevpn.model.AppThemePalette
import com.flclash.nativevpn.ui.MainViewModel
import com.flclash.nativevpn.ui.components.cardBorderStroke
import com.flclash.nativevpn.ui.components.formatBytes
import com.flclash.nativevpn.ui.components.formatExpireDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val themeConfig by viewModel.themeConfig.collectAsState()
    val routing by viewModel.routingConfig.collectAsState()
    val autoPing by viewModel.autoPingConfig.collectAsState()
    val customGroups by viewModel.customGroups.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val logs by viewModel.logs.collectAsState()

    var showAddSubDialog by remember { mutableStateOf(false) }
    var showAdvancedAuto by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Subscriptions Card
        Card(
            shape = RoundedCornerShape(20.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Подписки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (subscriptions.any { it.url.startsWith("http", ignoreCase = true) }) {
                            OutlinedButton(
                                onClick = { viewModel.refreshAllSubscriptions(silent = false) },
                                enabled = !isImporting,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isImporting) "..." else "Обновить")
                            }
                        }
                        FilledTonalButton(
                            onClick = { showAddSubDialog = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Добавить")
                        }
                    }
                }

                if (subscriptions.isEmpty()) {
                    Text(
                        text = "Поддерживаются Clash YAML, VLESS, VMess, Hysteria/2, Trojan, TUIC, SS/SSR, WireGuard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                                    Text(profile.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    val exp = formatExpireDate(profile.expireEpochSeconds)
                                    Text(
                                        text = "Серверов: ${profile.nodeCount}" + if (exp.isNotEmpty()) " • до $exp" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    if (profile.url.startsWith("http", ignoreCase = true)) {
                                        IconButton(
                                            onClick = { viewModel.refreshSubscription(profile) },
                                            enabled = !isImporting
                                        ) {
                                            Icon(Icons.Rounded.Refresh, contentDescription = "Обновить")
                                        }
                                    }
                                    IconButton(onClick = { viewModel.deleteSubscription(profile.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Удалить")
                                    }
                                }
                            }
                            if (profile.totalBytes > 0L) {
                                val progress = (profile.usedBytes.toFloat() / profile.totalBytes.toFloat()).coerceIn(0f, 1f)
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                Text(
                                    text = "${formatBytes(profile.usedBytes)} / ${formatBytes(profile.totalBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    SettingSwitchRow(
                        title = "Автообновление при запуске",
                        checked = routing.autoUpdateSubscriptionsOnStart,
                        onCheckedChange = { checked ->
                            viewModel.updateRoutingFields { it.copy(autoUpdateSubscriptionsOnStart = checked) }
                        }
                    )
                }
            }
        }

        // 2. Eco Auto-Mode Card
        Card(
            shape = RoundedCornerShape(20.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingSwitchRow(
                    title = "Авто-режим (Эко)",
                    checked = autoPing.enabled,
                    onCheckedChange = { checked -> viewModel.updateAutoPing { it.copy(enabled = checked) } }
                )

                if (autoPing.enabled) {
                    Text("Группа для авто-режима", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = autoPing.autoModeGroupId == null,
                            onClick = { viewModel.updateAutoPing { it.copy(autoModeGroupId = null) } },
                            label = { Text("Активная") }
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
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 10, 15, 30, 60).forEach { minutes ->
                            FilterChip(
                                selected = autoPing.intervalMinutes == minutes,
                                onClick = { viewModel.updateAutoPing { it.copy(intervalMinutes = minutes) } },
                                label = { Text("$minutes мин") }
                            )
                        }
                    }
                }

                TextButton(
                    onClick = { showAdvancedAuto = !showAdvancedAuto },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(if (showAdvancedAuto) "Скрыть тонкие настройки" else "Тонкие настройки пинга и батареи")
                }

                if (showAdvancedAuto) {
                    SettingSwitchRow(
                        title = "Пауза при выключенном экране",
                        checked = autoPing.pauseWhenScreenOff,
                        onCheckedChange = { checked -> viewModel.updateAutoPing { it.copy(pauseWhenScreenOff = checked) } }
                    )
                    SettingSwitchRow(
                        title = "Сначала проверять только текущий узел",
                        checked = autoPing.smartCheckCurrentFirst,
                        onCheckedChange = { checked -> viewModel.updateAutoPing { it.copy(smartCheckCurrentFirst = checked) } }
                    )
                    SettingSwitchRow(
                        title = "Сортировать прямо во время пинга",
                        checked = autoPing.sortLiveDuringTest,
                        onCheckedChange = { checked -> viewModel.updateAutoPing { it.copy(sortLiveDuringTest = checked) } }
                    )
                    SettingSwitchRow(
                        title = "Сброс соединений при смене сервера",
                        checked = autoPing.closeConnectionsOnSwitch,
                        onCheckedChange = { checked -> viewModel.updateAutoPing { it.copy(closeConnectionsOnSwitch = checked) } }
                    )
                    Text(
                        text = "Порог переключения: ${autoPing.toleranceMs} мс",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = autoPing.toleranceMs.toFloat(),
                        onValueChange = { v -> viewModel.updateAutoPing { it.copy(toleranceMs = v.roundToInt()) } },
                        valueRange = 10f..150f
                    )
                    OutlinedTextField(
                        value = autoPing.testUrl,
                        onValueChange = { url -> viewModel.updateAutoPing { it.copy(testUrl = url) } },
                        label = { Text("URL теста задержки") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 3. Appearance Card
        Card(
            shape = RoundedCornerShape(20.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Оформление", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        AppThemeMode.SYSTEM to "Системная",
                        AppThemeMode.DARK to "Тёмная",
                        AppThemeMode.LIGHT to "Светлая"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = themeConfig.mode == mode,
                            onClick = { viewModel.updateTheme { it.copy(mode = mode) } },
                            label = { Text(label) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        AppThemePalette.DYNAMIC to "Material You",
                        AppThemePalette.AMOLED to "AMOLED",
                        AppThemePalette.OCEAN to "Океан",
                        AppThemePalette.EMERALD to "Изумруд",
                        AppThemePalette.VIOLET to "Фиолет"
                    ).forEach { (pal, label) ->
                        FilterChip(
                            selected = themeConfig.palette == pal,
                            onClick = { viewModel.updateTheme { it.copy(palette = pal) } },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        // 4. Core Logs Card + Direct Download
        OutlinedButton(
            onClick = { showLogsDialog = true },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Лог ядра mihomo (${logs.size})")
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showAddSubDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        ModalBottomSheet(
            onDismissRequest = { showAddSubDialog = false },
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
                    Text("Добавить подписку", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { showAddSubDialog = false }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название (опционально)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL подписки") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text.orEmpty()
                        viewModel.importRawText(clip, name.ifBlank { "Из буфера" })
                        showAddSubDialog = false
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Вставить ссылки / YAML из буфера")
                }
                FilledTonalButton(
                    enabled = url.isNotBlank() && !isImporting,
                    onClick = {
                        viewModel.importSubscriptionFromUrl(name, url)
                        showAddSubDialog = false
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text(if (isImporting) "Загрузка..." else "Импортировать", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showLogsDialog) {
        val logSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showLogsDialog = false },
            sheetState = logSheetState,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Лог ядра mihomo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { viewModel.clearLogs() }) { Text("Очистить") }
                        IconButton(onClick = { showLogsDialog = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.downloadLogsToDevice() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Скачать", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            val text = viewModel.buildDiagnosticLogText()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Kestrel VPN mihomo log")
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(sendIntent, "Отправить лог"))
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Скинуть", maxLines = 1)
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
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
