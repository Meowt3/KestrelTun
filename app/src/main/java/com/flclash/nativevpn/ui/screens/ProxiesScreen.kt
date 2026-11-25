package com.flclash.nativevpn.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flclash.nativevpn.data.SettingsRepository
import com.flclash.nativevpn.model.CustomProxyGroup
import com.flclash.nativevpn.model.PingState
import com.flclash.nativevpn.model.ProxyCardSize
import com.flclash.nativevpn.model.ProxyGroupType
import com.flclash.nativevpn.model.ProxyLayoutMode
import com.flclash.nativevpn.model.ProxyNode
import com.flclash.nativevpn.model.ProxySortMode
import com.flclash.nativevpn.ui.MainViewModel
import com.flclash.nativevpn.ui.components.PingBadge
import com.flclash.nativevpn.ui.components.cardBorderStroke
import kotlinx.coroutines.launch
import java.util.UUID

private val ProxyTileShape = RoundedCornerShape(24.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxiesScreen(viewModel: MainViewModel, gridState: LazyGridState = rememberLazyGridState()) {
    val displayedNodes by viewModel.displayedNodes.collectAsState()
    val allNodes by viewModel.allNodes.collectAsState()
    val customGroups by viewModel.customGroups.collectAsState()
    val activeGroupId by viewModel.activeGroupId.collectAsState()
    val selectedNodeId by viewModel.selectedNodeId.collectAsState()
    val selectedNodeName by viewModel.selectedNodeName.collectAsState()
    val pingStates by viewModel.pingStates.collectAsState()
    val autoPing by viewModel.autoPingConfig.collectAsState()
    val proxyView by viewModel.proxyViewConfig.collectAsState()
    val isTesting by viewModel.isTestingPing.collectAsState()
    val pingFinishedVersion by viewModel.pingFinishedVersion.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val autoStatusText by viewModel.autoStatusText.collectAsState()

    var editingGroup by remember { mutableStateOf<CustomProxyGroup?>(null) }
    var showProxySettingsSheet by remember { mutableStateOf(false) }
    var showBaseAutoTimerDialog by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(searchQuery.isNotEmpty()) }

    val coroutineScope = rememberCoroutineScope()

    val groupCounts = remember(customGroups, allNodes) {
        customGroups.associate { it.id to viewModel.repository.resolveNodesForCustomGroup(it, allNodes).size }
    }

    val topNodeId = displayedNodes.firstOrNull()?.id
    var userDraggedDuringTest by remember { mutableStateOf(false) }

    // Track if the user manually scrolls the grid during a test
    LaunchedEffect(gridState.isScrollInProgress, isTesting) {
        if (isTesting && gridState.isScrollInProgress) {
            userDraggedDuringTest = true
        }
    }

    // Reset manual-drag flag and snap to top when a new ping test starts
    LaunchedEffect(isTesting) {
        if (isTesting) {
            userDraggedDuringTest = false
            if (displayedNodes.isNotEmpty()) {
                gridState.scrollToItem(0, 0)
            }
        }
    }

    // Reset scroll to top when user switches group or sort mode
    LaunchedEffect(activeGroupId, proxyView.sortMode) {
        if (displayedNodes.isNotEmpty()) {
            gridState.scrollToItem(0, 0)
        }
    }

    // Defeat Compose LazyGridState key-anchoring teleport:
    // Whenever displayedNodes reorders during live ping (and user didn't manually scroll),
    // unconditionally snap back to item 0 after the frame commits.
    LaunchedEffect(displayedNodes, isTesting) {
        if (isTesting && autoPing.sortLiveDuringTest && !userDraggedDuringTest && displayedNodes.isNotEmpty()) {
            gridState.scrollToItem(0, 0)
        }
    }

    // Unconditionally snap to top AFTER ping finishes AND final sorted displayedNodes has rendered
    LaunchedEffect(pingFinishedVersion) {
        if (pingFinishedVersion > 0 && displayedNodes.isNotEmpty()) {
            kotlinx.coroutines.delay(90)
            gridState.scrollToItem(0, 0)
        }
    }

    val activeCustomGroup = remember(customGroups, activeGroupId) {
        customGroups.firstOrNull { it.id == activeGroupId }
    }
    val isBaseAuto = activeGroupId == SettingsRepository.GROUP_AUTO_ID
    val isActiveAuto = isBaseAuto || activeCustomGroup?.type == ProxyGroupType.AUTO
    val activeAutoInterval = if (isBaseAuto) {
        autoPing.intervalMinutes
    } else {
        activeCustomGroup?.autoIntervalMinutes ?: autoPing.intervalMinutes
    }

    val columnCount = when (proxyView.layoutMode) {
        ProxyLayoutMode.LOOSE -> 1
        ProxyLayoutMode.STANDARD -> 2
        ProxyLayoutMode.DENSE -> 3
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        // Group Tabs Row: Base "Все" (Manual) + Base "Авто" (Auto) + Custom Groups + "+ Группа"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = activeGroupId == SettingsRepository.GROUP_ALL_ID,
                onClick = { viewModel.setActiveGroup(SettingsRepository.GROUP_ALL_ID) },
                label = { Text("Все (${allNodes.size})") }
            )
            FilterChip(
                selected = isBaseAuto,
                onClick = { viewModel.setActiveGroup(SettingsRepository.GROUP_AUTO_ID) },
                leadingIcon = { Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(15.dp)) },
                label = { Text("Авто") }
            )
            customGroups.forEach { group ->
                val count = groupCounts[group.id] ?: 0
                FilterChip(
                    selected = activeGroupId == group.id,
                    onClick = { viewModel.setActiveGroup(group.id) },
                    leadingIcon = if (group.type == ProxyGroupType.AUTO) {
                        { Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    } else null,
                    label = { Text("${group.name} ($count)") }
                )
            }
            AssistChip(
                onClick = {
                    editingGroup = CustomProxyGroup(
                        id = UUID.randomUUID().toString(),
                        name = "",
                        selectedNodeKeys = emptyList(),
                        type = ProxyGroupType.MANUAL,
                        autoIntervalMinutes = 15
                    )
                },
                label = { Text("Группа") },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(15.dp)) }
            )
        }

        // Compact Status / Actions Strip for Auto-Tunneling or Custom Group
        if (isActiveAuto || activeCustomGroup != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isActiveAuto) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (isBaseAuto) showBaseAutoTimerDialog = true
                                else editingGroup = activeCustomGroup
                            }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Авто · каждые $activeAutoInterval мин · $autoStatusText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (activeCustomGroup != null) {
                    Text(
                        text = "Группа «${activeCustomGroup.name}»",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (activeCustomGroup != null) {
                    Row {
                        IconButton(onClick = { editingGroup = activeCustomGroup }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Редактировать", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { viewModel.deleteCustomGroup(activeCustomGroup.id) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Удалить", modifier = Modifier.size(16.dp))
                        }
                    }
                } else if (isBaseAuto) {
                    TextButton(
                        onClick = { showBaseAutoTimerDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Таймер", fontSize = 11.5.sp)
                    }
                }
            }
        }

        // Compact FlClash-style Toolbar (Ping, Fastest, Search toggle, Locate Active, Settings)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = { viewModel.startLivePingTest() },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(if (isTesting) "Тест..." else "Пинг", fontSize = 12.5.sp, maxLines = 1)
            }
            FilledTonalButton(
                onClick = { viewModel.selectFastestNow() },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("Быстрый", fontSize = 12.5.sp, maxLines = 1)
            }
            ToolbarIconButton(
                icon = Icons.Rounded.Search,
                contentDescription = "Поиск",
                active = showSearchBar || searchQuery.isNotEmpty(),
                onClick = {
                    showSearchBar = !showSearchBar
                    if (!showSearchBar && searchQuery.isNotEmpty()) {
                        viewModel.setSearchQuery("")
                    }
                }
            )
            ToolbarIconButton(
                icon = Icons.Rounded.GpsFixed,
                contentDescription = "К активному серверу",
                active = false,
                onClick = {
                    val idx = displayedNodes.indexOfFirst { node ->
                        if (!selectedNodeId.isNullOrBlank()) node.id == selectedNodeId
                        else node.name == selectedNodeName
                    }
                    if (idx >= 0) {
                        coroutineScope.launch { gridState.animateScrollToItem(idx) }
                    }
                }
            )
            ToolbarIconButton(
                icon = Icons.Rounded.Tune,
                contentDescription = "Настройки прокси",
                active = false,
                onClick = { showProxySettingsSheet = true }
            )
        }

        // Collapsible Search Bar (hidden by default so 7-8 server rows fit on screen!)
        if (showSearchBar) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("Поиск прокси...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Очистить", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )
        }

        if (isTesting) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )
        }

        // Pull-to-Refresh wraps the proxy grid for instant 1-swipe ping
        PullToRefreshBox(
            isRefreshing = isTesting,
            onRefresh = { viewModel.startLivePingTest() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (displayedNodes.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    border = cardBorderStroke(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (allNodes.isEmpty()) {
                            "Серверов пока нет. Добавьте подписку во вкладке «Настройки»."
                        } else {
                            "В этом фильтре нет серверов. Сбросьте поиск или фильтр Timeout."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                val onSelectNode = remember(viewModel) { { node: ProxyNode -> viewModel.selectNode(node) } }
                val onPingNode = remember(viewModel) { { node: ProxyNode -> viewModel.testSingleNode(node) } }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(if (proxyView.cardSize == ProxyCardSize.MINIMAL) 7.dp else 8.dp)
                ) {
                    items(
                        items = displayedNodes,
                        key = { it.id },
                        contentType = { "proxy_card" }
                    ) { node ->
                        val isSelected = if (!selectedNodeId.isNullOrBlank()) {
                            node.id == selectedNodeId
                        } else {
                            node.name == selectedNodeName
                        }
                        ProxyNodeTile(
                            node = node,
                            isSelected = isSelected,
                            pingState = pingStates[node.id],
                            cardSize = proxyView.cardSize,
                            isSingleColumn = columnCount == 1,
                            onSelect = onSelectNode,
                            onPingClick = onPingNode
                        )
                    }
                }
            }
        }
    }

    // FlClash-style Proxy Settings ModalBottomSheet
    if (showProxySettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProxySettingsSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Настройки",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { showProxySettingsSheet = false }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Сортировка", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            ProxySortMode.DEFAULT to "По умолчанию",
                            ProxySortMode.LATENCY to "Задержка",
                            ProxySortMode.NAME to "Название"
                        ).forEach { (mode, label) ->
                            SheetOptionButton(
                                label = label,
                                selected = proxyView.sortMode == mode,
                                onClick = { viewModel.updateProxyView { it.copy(sortMode = mode) } }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Макет", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            ProxyLayoutMode.LOOSE to "Свободный",
                            ProxyLayoutMode.STANDARD to "Стандартный",
                            ProxyLayoutMode.DENSE to "Плотный"
                        ).forEach { (layout, label) ->
                            SheetOptionButton(
                                label = label,
                                selected = proxyView.layoutMode == layout,
                                onClick = { viewModel.updateProxyView { it.copy(layoutMode = layout) } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Размер", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            ProxyCardSize.STANDARD to "Стандартный",
                            ProxyCardSize.COMPACT to "Компактный",
                            ProxyCardSize.MINIMAL to "Минимальный"
                        ).forEach { (size, label) ->
                            SheetOptionButton(
                                label = label,
                                selected = proxyView.cardSize == size,
                                onClick = { viewModel.updateProxyView { it.copy(cardSize = size) } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Скрыть недоступные (Timeout)", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = autoPing.hideTimeouts,
                        onCheckedChange = { checked -> viewModel.updateAutoPing { it.copy(hideTimeouts = checked) } }
                    )
                }

                if (isActiveAuto) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Тест сразу при старте", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Проверять серверы сразу при выборе автотуннелинга или подключении",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoPing.testImmediatelyOnStart,
                            onCheckedChange = { checked -> viewModel.updateAutoPing { it.copy(testImmediatelyOnStart = checked) } }
                        )
                    }
                }
            }
        }
    }

    // FlClash Bento Sheet for Base "Авто" Group Timer
    if (showBaseAutoTimerDialog) {
        var minutesText by remember { mutableStateOf(autoPing.intervalMinutes.toString()) }
        ModalBottomSheet(
            onDismissRequest = { showBaseAutoTimerDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Таймер автотуннелинга", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { showBaseAutoTimerDialog = false }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                    }
                }
                Text(
                    text = "Как часто проверять задержку и переключаться на быстрейший сервер (в минутах):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 3, 5, 10, 15, 30, 60).forEach { preset ->
                        SheetOptionButton(
                            label = "$preset м",
                            selected = minutesText == preset.toString(),
                            onClick = { minutesText = preset.toString() }
                        )
                    }
                }
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { input -> minutesText = input.filter { it.isDigit() }.take(4) },
                    label = { Text("Свой интервал (минуты)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                FilledTonalButton(
                    enabled = (minutesText.toIntOrNull() ?: 0) >= 1,
                    onClick = {
                        val v = minutesText.toIntOrNull() ?: 15
                        viewModel.setBaseAutoIntervalMinutes(v)
                        showBaseAutoTimerDialog = false
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text("Сохранить", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    editingGroup?.let { group ->
        CustomGroupEditorDialog(
            initialGroup = group,
            allNodes = allNodes,
            onDismiss = { editingGroup = null },
            onSave = { updated ->
                viewModel.saveCustomGroup(updated)
                viewModel.setActiveGroup(updated.id)
                editingGroup = null
            }
        )
    }
}

@Composable
private fun ToolbarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(13.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SheetOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProxyNodeTile(
    node: ProxyNode,
    isSelected: Boolean,
    pingState: PingState?,
    cardSize: ProxyCardSize,
    isSingleColumn: Boolean,
    onSelect: (ProxyNode) -> Unit,
    onPingClick: (ProxyNode) -> Unit
) {
    val isMinimal = cardSize == ProxyCardSize.MINIMAL
    val paddingV = when (cardSize) {
        ProxyCardSize.STANDARD -> 12.dp
        ProxyCardSize.COMPACT -> 9.dp
        ProxyCardSize.MINIMAL -> 7.dp
    }
    val paddingH = when {
        isSingleColumn -> 14.dp
        isMinimal -> 11.dp
        else -> 12.dp
    }

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor, ProxyTileShape)
            .border(1.dp, borderColor, ProxyTileShape)
            .clickable { onSelect(node) }
            .padding(horizontal = paddingH, vertical = paddingV)
    ) {
        if (isSingleColumn) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.name,
                        fontSize = if (isMinimal) 12.5.sp else 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!isMinimal) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${node.type} · ${node.server}:${node.port}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                PingBadge(
                    state = pingState,
                    onClick = { onPingClick(node) },
                    compact = isMinimal,
                    plainText = isMinimal
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (isMinimal) 3.dp else 6.dp)
            ) {
                Text(
                    text = node.name,
                    fontSize = if (isMinimal) 12.sp else 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = node.type,
                        fontSize = if (isMinimal) 10.5.sp else 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    val (pingLabel, pingColor) = when (pingState) {
                        is PingState.Success -> {
                            val ms = pingState.latencyMs
                            val accent = MaterialTheme.colorScheme.primary
                            val c = when {
                                ms < 200 -> accent
                                ms < 450 -> accent.copy(alpha = 0.78f)
                                else -> accent.copy(alpha = 0.58f)
                            }
                            "${ms} ms" to c
                        }
                        is PingState.Testing -> "..." to MaterialTheme.colorScheme.onSurfaceVariant
                        is PingState.Timeout -> "Timeout" to MaterialTheme.colorScheme.error
                        is PingState.Idle, null -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = pingLabel,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = pingColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomGroupEditorDialog(
    initialGroup: CustomProxyGroup,
    allNodes: List<ProxyNode>,
    onDismiss: () -> Unit,
    onSave: (CustomProxyGroup) -> Unit
) {
    var name by remember { mutableStateOf(initialGroup.name) }
    var groupType by remember { mutableStateOf(initialGroup.type) }
    var intervalText by remember { mutableStateOf(initialGroup.autoIntervalMinutes.toString()) }
    var filter by remember { mutableStateOf("") }
    var selectedKeys by remember { mutableStateOf(initialGroup.selectedNodeKeys.toSet()) }

    val filteredNodes = remember(allNodes, filter) {
        val q = filter.trim()
        if (q.isEmpty()) allNodes
        else allNodes.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.type.contains(q, ignoreCase = true) ||
                it.server.contains(q, ignoreCase = true)
        }
    }
    val filteredKeys = remember(filteredNodes) { filteredNodes.map { it.stableKey }.toSet() }
    val selectedInFiltered = remember(selectedKeys, filteredKeys) {
        filteredKeys.count { it in selectedKeys }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canSave = name.isNotBlank() && (groupType == ProxyGroupType.MANUAL || (intervalText.toIntOrNull() ?: 0) >= 1)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 520.dp, max = 720.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Sheet Header: Title + Save + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialGroup.name.isBlank()) "Новая группа" else "Редактировать",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        enabled = canSave,
                        onClick = {
                            onSave(
                                initialGroup.copy(
                                    name = name.trim(),
                                    selectedNodeKeys = selectedKeys.toList(),
                                    type = groupType,
                                    autoIntervalMinutes = (intervalText.toIntOrNull() ?: 15).coerceIn(1, 1440)
                                )
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Сохранить", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                    }
                }
            }

            // Group Name + Type selector side by side / compact
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название группы") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SheetOptionButton(
                    label = "Обычная",
                    selected = groupType == ProxyGroupType.MANUAL,
                    onClick = { groupType = ProxyGroupType.MANUAL },
                    modifier = Modifier.weight(1f)
                )
                SheetOptionButton(
                    label = "⚡ Авто",
                    selected = groupType == ProxyGroupType.AUTO,
                    onClick = { groupType = ProxyGroupType.AUTO },
                    modifier = Modifier.weight(1f)
                )
            }

            if (groupType == ProxyGroupType.AUTO) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1, 3, 5, 10, 15, 30, 60).forEach { preset ->
                            SheetOptionButton(
                                label = "${preset}м",
                                selected = intervalText == preset.toString(),
                                onClick = { intervalText = preset.toString() }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { input -> intervalText = input.filter { it.isDigit() }.take(4) },
                        label = { Text("Мин") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.width(86.dp)
                    )
                }
            }

            // Search Filter
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Фильтр серверов (страна, LTE, Hysteria2)...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (filter.isNotEmpty()) {
                        IconButton(onClick = { filter = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Очистить", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Filter-aware bulk actions: Add filtered / Remove filtered / Clear all
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Выбрано: ${selectedKeys.size}" + if (filter.isNotBlank()) " ($selectedInFiltered/${filteredNodes.size})" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                SheetOptionButton(
                    label = if (filter.isNotBlank()) "+ Найд. (${filteredNodes.size})" else "+ Все",
                    selected = false,
                    onClick = { selectedKeys = selectedKeys + filteredKeys }
                )
                SheetOptionButton(
                    label = if (filter.isNotBlank()) "− Найд." else "− Все",
                    selected = false,
                    onClick = { selectedKeys = selectedKeys - filteredKeys }
                )
            }

            // Large comfortable FlClash-style server list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredNodes, key = { it.id }) { node ->
                    val checked = node.stableKey in selectedKeys
                    val rowBg = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                    val rowBorder = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(rowBg)
                            .border(1.dp, rowBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                selectedKeys = if (checked) selectedKeys - node.stableKey else selectedKeys + node.stableKey
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                selectedKeys = if (checked) selectedKeys - node.stableKey else selectedKeys + node.stableKey
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = node.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${node.type.uppercase()} · ${node.server}:${node.port}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
