package com.flclash.nativevpn.ui.screens

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CardMembership
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.flclash.nativevpn.model.OutboundMode
import com.flclash.nativevpn.model.TrafficStats
import com.flclash.nativevpn.ui.MainViewModel
import com.flclash.nativevpn.ui.components.cardBorderStroke
import com.flclash.nativevpn.ui.components.formatBytes
import com.flclash.nativevpn.ui.components.formatBytesPerSec
import com.flclash.nativevpn.ui.components.formatDaysRemaining
import com.flclash.nativevpn.ui.components.formatDuration
import com.flclash.nativevpn.ui.components.formatExpireDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel, isVisible: Boolean = true) {
    val runtime by viewModel.runtimeState.collectAsState()
    val routing by viewModel.routingConfig.collectAsState()
    val splitTunnel by viewModel.splitTunnelConfig.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val networkCheck by viewModel.networkCheck.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current

    // Live traffic state isolated in holders so parent DashboardScreen never recomposes on wave ticks
    val liveTrafficState = remember { mutableStateOf(TrafficStats()) }
    val sessionDownState = remember { mutableLongStateOf(0L) }
    val sessionUpState = remember { mutableLongStateOf(0L) }

    // Foreground-only traffic stream (0 background wakeups)
    LaunchedEffect(runtime.isConnected, isVisible, lifecycleOwner) {
        if (!isVisible) return@LaunchedEffect
        if (!runtime.isConnected) {
            liveTrafficState.value = TrafficStats()
            sessionDownState.longValue = 0L
            sessionUpState.longValue = 0L
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.observeTraffic().collect { stats ->
                liveTrafficState.value = stats
                sessionDownState.longValue += stats.downloadBytesPerSec
                sessionUpState.longValue += stats.uploadBytesPerSec
            }
        }
    }

    val scrollState = rememberScrollState()
    val primarySubForRefresh = subscriptions.firstOrNull()

    PullToRefreshBox(
        isRefreshing = isImporting,
        onRefresh = { primarySubForRefresh?.let { viewModel.refreshSubscription(it) } },
        modifier = Modifier.fillMaxSize()
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Isolated Speed Wave Card (Draw-phase only wave, 0 parent recompositions)
        SpeedWaveCard(
            isConnected = runtime.isConnected && isVisible,
            isScrollingProvider = { scrollState.isScrollInProgress },
            trafficProvider = { liveTrafficState.value }
        )

        // 2. Asymmetric 2-Column Bento Masonry (FlClash style)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // LEFT COLUMN: Outbound Mode (Buttons) + Isolated Session Timer
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    border = cardBorderStroke(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AltRoute,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Режим",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        listOf(
                            OutboundMode.RULE to "Правило",
                            OutboundMode.GLOBAL to "Глобальный",
                            OutboundMode.DIRECT to "Прямой"
                        ).forEach { (mode, label) ->
                            val selected = routing.outboundMode == mode
                            val btnBg by animateColorAsState(
                                targetValue = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
                                animationSpec = tween(220),
                                label = "mode_bg_$label"
                            )
                            val btnBorder by animateColorAsState(
                                targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
                                animationSpec = tween(220),
                                label = "mode_border_$label"
                            )
                            val btnText by animateColorAsState(
                                targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = tween(220),
                                label = "mode_text_$label"
                            )
                            Surface(
                                onClick = { viewModel.setOutboundMode(mode) },
                                shape = RoundedCornerShape(999.dp),
                                color = btnBg,
                                border = BorderStroke(1.dp, btnBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = btnText,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Isolated Session Timer Card (ticks only itself, 0 parent recompositions)
                SessionTimerCard(
                    isConnected = runtime.isConnected && isVisible,
                    connectedAtElapsedMs = runtime.connectedAtElapsedMs
                )
            }

            // RIGHT COLUMN: IP & Gemini Check + Isolated Traffic Donut
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    border = cardBorderStroke(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.checkNetworkAndGemini() }
                ) {
                    Column(modifier = Modifier.padding(13.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Проверка IP",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when {
                                networkCheck.isChecking -> "Проверка..."
                                networkCheck.ip.isNotBlank() -> networkCheck.ip
                                else -> "Нажми для теста"
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = networkCheck.country.ifBlank { if (runtime.isConnected) "VPN" else "Прямой" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            val geminiLabel = when (networkCheck.geminiReachable) {
                                true -> "Gemini OK"
                                false -> "Нет доступа"
                                null -> "—"
                            }
                            val geminiColor = when (networkCheck.geminiReachable) {
                                true -> MaterialTheme.colorScheme.primary
                                false -> Color(0xFFD67A7A)
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = geminiLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = geminiColor
                            )
                        }
                    }
                }

                // Isolated Traffic Donut Card
                TrafficDonutCard(
                    downBytesProvider = { sessionDownState.longValue },
                    upBytesProvider = { sessionUpState.longValue }
                )
            }
        }

        // 3. Subscription Quota & Expiry Card
        val primarySub = subscriptions.firstOrNull()
        if (primarySub != null) {
            Card(
                shape = RoundedCornerShape(28.dp),
                border = cardBorderStroke(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CardMembership,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${primarySub.name} · Подписка",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (primarySub.url.startsWith("http", ignoreCase = true)) {
                            Surface(
                                onClick = { viewModel.refreshAllSubscriptions(silent = false) },
                                enabled = !isImporting,
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = cardBorderStroke()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Обновить подписку",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isImporting) "Обновл..." else "Обновить",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val trafficText = if (primarySub.totalBytes > 0L) {
                            "${formatBytes(primarySub.usedBytes)} / ${formatBytes(primarySub.totalBytes)}"
                        } else {
                            "Серверов: ${primarySub.nodeCount}"
                        }
                        Text(
                            text = trafficText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val exp = formatExpireDate(primarySub.expireEpochSeconds)
                        val days = formatDaysRemaining(primarySub.expireEpochSeconds)
                        if (exp.isNotEmpty()) {
                            Text(
                                text = "до $exp · $days",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (primarySub.totalBytes > 0L) {
                        val progress = (primarySub.usedBytes.toFloat() / primarySub.totalBytes.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                }
            }
        }

        // 4. 2x2 Flat Rule/Eco Buttons (NO switches!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            RuleToggleButton(
                title = "AdBlock",
                subtitle = if (routing.adBlockEnabled) "Вкл" else "Выкл",
                icon = Icons.Rounded.Block,
                active = routing.adBlockEnabled,
                onClick = { viewModel.toggleAdBlock(!routing.adBlockEnabled) },
                modifier = Modifier.weight(1f)
            )
            RuleToggleButton(
                title = "Обход РФ",
                subtitle = if (routing.ruBypassEnabled) ".ru напрямую" else "Через VPN",
                icon = Icons.Rounded.AltRoute,
                active = routing.ruBypassEnabled,
                onClick = { viewModel.toggleRuBypass(!routing.ruBypassEnabled) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            RuleToggleButton(
                title = "Gemini",
                subtitle = if (routing.geminiBypassEnabled) "VPN + DoH" else "Выкл",
                icon = Icons.Rounded.AutoAwesome,
                active = routing.geminiBypassEnabled,
                onClick = { viewModel.toggleGeminiBypass(!routing.geminiBypassEnabled) },
                modifier = Modifier.weight(1f)
            )
            RuleToggleButton(
                title = "Приложения",
                subtitle = if (splitTunnel.enabled) "Обход: ${splitTunnel.selectedPackages.size}" else "Выкл",
                icon = Icons.Rounded.Apps,
                active = splitTunnel.enabled,
                onClick = { viewModel.toggleSplitTunnel(!splitTunnel.enabled) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(74.dp))
    }
    }
}

@Composable
private fun SpeedWaveCard(
    isConnected: Boolean,
    isScrollingProvider: () -> Boolean,
    trafficProvider: () -> TrafficStats
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val pointCount = 20
    val samples = remember { FloatArray(pointCount) { 0f } }
    val drawVersion = remember { mutableLongStateOf(0L) }
    val linePath = remember { Path() }
    val fillPath = remember { Path() }

    // Push each new speed sample on the right and shift older peaks smoothly to the left (FlClash rolling wave).
    // Runs ONLY while app is in foreground (Lifecycle.State.STARTED) and connected -> 0 background CPU/GPU!
    LaunchedEffect(isConnected, lifecycleOwner) {
        if (!isConnected) {
            samples.fill(0f)
            drawVersion.longValue++
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                if (!isScrollingProvider()) {
                    val stats = trafficProvider()
                    val bps = (stats.downloadBytesPerSec + stats.uploadBytesPerSec).toFloat()
                    for (i in 0 until pointCount - 1) {
                        samples[i] = samples[i + 1]
                    }
                    samples[pointCount - 1] = bps
                    drawVersion.longValue++
                }
                delay(500L)
            }
        }
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        border = cardBorderStroke(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .height(144.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val stats = trafficProvider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Скорость сети",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "↑ ${formatBytesPerSec(stats.uploadBytesPerSec)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "↓ ${formatBytesPerSec(stats.downloadBytesPerSec)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            val accentColor = MaterialTheme.colorScheme.primary
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Read drawVersion ONLY inside DrawScope -> 0 parent/composable recompositions!
                val tick = drawVersion.longValue
                if (tick < 0L) return@Canvas

                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                val baselineY = h - 18.dp.toPx()
                val usableH = (h - 28.dp.toPx()).coerceAtLeast(10f)

                var maxVal = 65_536f // 64 KB/s floor so tiny background noise stays low
                for (v in samples) {
                    if (v > maxVal) maxVal = v
                }
                val ceiling = maxVal * 1.18f
                val stepX = w / (pointCount - 1).toFloat()

                linePath.reset()
                fillPath.reset()

                fun yFor(index: Int): Float {
                    val v = samples[index].coerceAtLeast(0f)
                    val norm = if (v <= 0f) 0f else (v / ceiling).coerceIn(0f, 1f)
                    return (baselineY - norm * usableH).coerceIn(6f, baselineY)
                }

                val firstY = yFor(0)
                linePath.moveTo(0f, firstY)
                fillPath.moveTo(0f, firstY)

                for (i in 0 until pointCount - 1) {
                    val x0 = i * stepX
                    val y0 = yFor(i)
                    val x1 = (i + 1) * stepX
                    val y1 = yFor(i + 1)
                    val cx = (x0 + x1) * 0.5f
                    linePath.cubicTo(cx, y0, cx, y1, x1, y1)
                    fillPath.cubicTo(cx, y0, cx, y1, x1, y1)
                }

                fillPath.lineTo(w, h)
                fillPath.lineTo(0f, h)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(accentColor.copy(alpha = 0.24f), Color.Transparent),
                        startY = 8f,
                        endY = h
                    )
                )
                drawPath(
                    path = linePath,
                    color = accentColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun SessionTimerCard(
    isConnected: Boolean,
    connectedAtElapsedMs: Long
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isConnected, connectedAtElapsedMs, lifecycleOwner) {
        if (!isConnected || connectedAtElapsedMs <= 0L) {
            elapsedSeconds = 0L
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                elapsedSeconds = ((SystemClock.elapsedRealtime() - connectedAtElapsedMs) / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
        }
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        border = cardBorderStroke(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Время",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = formatDuration(elapsedSeconds),
                fontFamily = FontFamily.Monospace,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isConnected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isConnected) "Подключён · MTU 1500" else "Туннель выключен",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TrafficDonutCard(
    downBytesProvider: () -> Long,
    upBytesProvider: () -> Long
) {
    val downBytes = downBytesProvider()
    val upBytes = upBytesProvider()

    Card(
        shape = RoundedCornerShape(28.dp),
        border = cardBorderStroke(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DataUsage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Трафик",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val accentColor = MaterialTheme.colorScheme.primary
            val upColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(72.dp)) {
                    val strokePx = 9.dp.toPx()
                    val inset = strokePx / 2f
                    val arcSize = Size(size.width - strokePx, size.height - strokePx)
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokePx)
                    )
                    val total = (downBytes + upBytes).coerceAtLeast(1L).toFloat()
                    val hasData = (downBytes + upBytes) > 0L
                    val upSweep = if (hasData) ((upBytes / total) * 320f).coerceIn(28f, 290f) else 155f
                    val downSweep = if (hasData) (320f - upSweep).coerceAtLeast(28f) else 155f

                    drawArc(
                        color = upColor,
                        startAngle = -90f,
                        sweepAngle = upSweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = accentColor,
                        startAngle = -90f + upSweep + 20f,
                        sweepAngle = downSweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("↑", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = upColor)
                Text(
                    text = formatBytes(upBytes),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("↓", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = accentColor)
                Text(
                    text = formatBytes(downBytes),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun RuleToggleButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tile_scale"
    )
    val corner by animateDpAsState(
        targetValue = if (active) 28.dp else 22.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "tile_corner"
    )
    val bgColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(240),
        label = "tile_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(240),
        label = "tile_border"
    )
    val dotColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(240),
        label = "tile_dot"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(corner),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(if (active) 14.dp else 10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}
