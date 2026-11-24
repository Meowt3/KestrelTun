package com.flclash.nativevpn.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.flclash.nativevpn.model.AppThemeMode
import com.flclash.nativevpn.model.AppThemePalette
import com.flclash.nativevpn.ui.MainViewModel
import com.flclash.nativevpn.ui.components.cardBorderStroke
import kotlin.math.roundToInt

private data class PaletteOption(
    val palette: AppThemePalette,
    val label: String,
    val swatchColor: Color,
    val isDynamic: Boolean = false
)

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsIconBadge(icon: ImageVector, active: Boolean = true) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(11.dp))
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
            modifier = Modifier.size(19.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val themeConfig by viewModel.themeConfig.collectAsState()
    val autoPing by viewModel.autoPingConfig.collectAsState()
    val customGroups by viewModel.customGroups.collectAsState()
    val logs by viewModel.logs.collectAsState()

    var showAdvancedAuto by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val paletteOptions = remember {
        listOf(
            PaletteOption(AppThemePalette.DYNAMIC, "Системная", Color(0xFF7AB3E8), isDynamic = true),
            PaletteOption(AppThemePalette.OCEAN, "Океан", Color(0xFF4E9AF1)),
            PaletteOption(AppThemePalette.EMERALD, "Изумруд", Color(0xFF4CC38A)),
            PaletteOption(AppThemePalette.TEAL, "Бирюза", Color(0xFF38BDB4)),
            PaletteOption(AppThemePalette.VIOLET, "Фиолет", Color(0xFF9D86F0)),
            PaletteOption(AppThemePalette.CRIMSON, "Рубин", Color(0xFFE86A7A)),
            PaletteOption(AppThemePalette.AMBER, "Янтарь", Color(0xFFE8A849)),
            PaletteOption(AppThemePalette.ROSE, "Сакура", Color(0xFFE57EB5)),
            PaletteOption(AppThemePalette.MONOCHROME, "Графит", Color(0xFF9AA5B1))
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsSectionHeader("Автотуннелинг и пинг")

        // 1. Auto-Tunneling Card
        Card(
            shape = RoundedCornerShape(20.dp),
            border = cardBorderStroke(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsIconBadge(Icons.Rounded.Bolt, autoPing.enabled)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Автотуннелинг", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Проверяет серверы и автоматически выбирает самый быстрый",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoPing.enabled,
                        onCheckedChange = { checked -> viewModel.updateAutoPing { it.copy(enabled = checked) } }
                    )
                }

                if (autoPing.enabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Text("Откуда выбирать сервер", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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

                    Text("Как часто проверять", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (showAdvancedAuto) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (showAdvancedAuto) {
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
                        title = "Сортировать прямо во время пинга",
                        subtitle = "Поднимает быстрые серверы наверх в реальном времени",
                        checked = autoPing.sortLiveDuringTest,
                        onCheckedChange = { checked -> viewModel.updateAutoPing { it.copy(sortLiveDuringTest = checked) } }
                    )
                    SettingSwitchRow(
                        title = "Сброс соединений при смене сервера",
                        subtitle = "Мгновенно переводит открытые приложения на новый узел",
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
                        label = { Text("URL проверки задержки (HTTPS 204)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        SettingsSectionHeader("Внешний вид и диагностика")

        // 2. Appearance Card (AMOLED in Theme Mode + Visual Color Swatch Icons!)
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsIconBadge(Icons.Rounded.Palette, true)
                    Column {
                        Text("Оформление", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Режим темы и цветовой акцент интерфейса", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Theme Mode row: Системная / Светлая / Тёмная / AMOLED
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Режим темы", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                // Visual Color Swatch Icons row
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Цветовая гамма", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        paletteOptions.forEach { opt ->
                            val selected = themeConfig.palette == opt.palette ||
                                (themeConfig.palette == AppThemePalette.AMOLED && opt.palette == AppThemePalette.OCEAN)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .width(58.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.updateTheme { it.copy(palette = opt.palette) } }
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .then(
                                            if (opt.isDynamic) {
                                                Modifier.background(
                                                    Brush.sweepGradient(
                                                        listOf(
                                                            Color(0xFF4E9AF1),
                                                            Color(0xFF9D86F0),
                                                            Color(0xFFE57EB5),
                                                            Color(0xFF4CC38A),
                                                            Color(0xFF4E9AF1)
                                                        )
                                                    )
                                                )
                                            } else {
                                                Modifier.background(opt.swatchColor)
                                            }
                                        )
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Выбрано",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    } else if (opt.isDynamic) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = opt.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Core Logs Card
        OutlinedButton(
            onClick = { showLogsDialog = true },
            shape = RoundedCornerShape(16.dp),
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
                    Text("Журнал ядра mihomo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
                                putExtra(Intent.EXTRA_SUBJECT, "KestrelTun mihomo log")
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
                        Text("Поделиться", maxLines = 1)
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
