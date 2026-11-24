package com.flclash.nativevpn.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flclash.nativevpn.model.PingState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun cardBorderStroke(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

@Composable
fun PingBadge(
    state: PingState?,
    onClick: () -> Unit,
    compact: Boolean = false,
    plainText: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (label, bgColor, textColor) = when (state) {
        is PingState.Success -> {
            val ms = state.latencyMs
            val color = when {
                ms < 120 -> Color(0xFF6FBC91)
                ms < 260 -> Color(0xFFCFA859)
                else -> Color(0xFFD67A7A)
            }
            Triple("${ms} ms", color.copy(alpha = 0.14f), color)
        }
        is PingState.Testing -> Triple(
            "...",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        is PingState.Timeout -> Triple(
            "Timeout",
            Color(0xFFD67A7A).copy(alpha = 0.14f),
            Color(0xFFD67A7A)
        )
        is PingState.Idle, null -> Triple(
            "—",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Box(
        modifier = if (plainText) {
            modifier.clickable(onClick = onClick)
        } else {
            modifier
                .clip(RoundedCornerShape(if (compact) 7.dp else 9.dp))
                .background(bgColor)
                .clickable(onClick = onClick)
                .padding(
                    horizontal = if (compact) 6.dp else 9.dp,
                    vertical = if (compact) 3.dp else 5.dp
                )
        },
        contentAlignment = Alignment.Center
    ) {
        if (state is PingState.Testing) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (compact) 11.dp else 13.dp),
                strokeWidth = 1.8.dp,
                color = textColor
            )
        } else {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontSize = if (compact) 10.5.sp else 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

fun formatBytesPerSec(bytes: Long): String {
    if (bytes <= 0L) return "0 B/s"
    if (bytes < 1024L) return "$bytes B/s"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB/s", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB/s", mb)
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f КБ", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f МБ", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f ГБ", gb)
}

fun formatDuration(totalSeconds: Long): String {
    val s = max(0L, totalSeconds)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, sec)
}

fun formatExpireDate(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return ""
    val fmt = SimpleDateFormat("d MMM", Locale("ru"))
    return fmt.format(Date(epochSeconds * 1000L))
}

fun formatDaysRemaining(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return ""
    val diffSec = epochSeconds - (System.currentTimeMillis() / 1000L)
    val days = (diffSec / 86400L).coerceAtLeast(0L)
    return "$days дн."
}
