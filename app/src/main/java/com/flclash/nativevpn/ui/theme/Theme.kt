package com.flclash.nativevpn.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.flclash.nativevpn.model.AppThemeMode
import com.flclash.nativevpn.model.AppThemePalette

// Calm, non-acid FlClash-aligned palettes
private val OceanDark = darkColorScheme(
    primary = Color(0xFF7AB3E8),
    onPrimary = Color(0xFF0A223A),
    primaryContainer = Color(0xFF27415C),
    onPrimaryContainer = Color(0xFFDCEEFF),
    secondary = Color(0xFF6FBC91),
    onSecondary = Color(0xFF0A2919),
    secondaryContainer = Color(0xFF1F3347),
    onSecondaryContainer = Color(0xFFCDE2F7),
    background = Color(0xFF0B0E11),
    onBackground = Color(0xFFE9EEF4),
    surface = Color(0xFF0B0E11),
    onSurface = Color(0xFFE9EEF4),
    surfaceContainerLow = Color(0xFF111519),
    surfaceContainer = Color(0xFF151A1F),
    surfaceContainerHigh = Color(0xFF1D232A),
    surfaceContainerHighest = Color(0xFF252C35),
    surfaceVariant = Color(0xFF1D232A),
    onSurfaceVariant = Color(0xFF93A0AE),
    outline = Color(0xFF323B46),
    outlineVariant = Color(0xFF232A32),
    error = Color(0xFFD67A7A),
    errorContainer = Color(0xFF3A2024),
    onErrorContainer = Color(0xFFF3C6C6)
)

private val EmeraldDark = OceanDark.copy(
    primary = Color(0xFF6EB590),
    onPrimary = Color(0xFF0A2618),
    primaryContainer = Color(0xFF1F382C),
    onPrimaryContainer = Color(0xFFCBEAD9),
    secondary = Color(0xFF7AB3E8),
    secondaryContainer = Color(0xFF1B3027)
)

private val VioletDark = OceanDark.copy(
    primary = Color(0xFFA99AE0),
    onPrimary = Color(0xFF21174A),
    primaryContainer = Color(0xFF2D2847),
    onPrimaryContainer = Color(0xFFE2DCFA),
    secondary = Color(0xFF7AB3E8),
    secondaryContainer = Color(0xFF242038)
)

private val AmoledDark = OceanDark.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0D10),
    surfaceContainer = Color(0xFF12161A),
    surfaceContainerHigh = Color(0xFF191F25),
    surfaceContainerHighest = Color(0xFF212830),
    outlineVariant = Color(0xFF1E242B)
)

private val OceanLight = lightColorScheme(
    primary = Color(0xFF3B73B4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6F8),
    onPrimaryContainer = Color(0xFF173F6E),
    secondary = Color(0xFF257A4E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2ECF7),
    onSecondaryContainer = Color(0xFF1E3A5F),
    background = Color(0xFFF2F5F8),
    onBackground = Color(0xFF141A21),
    surface = Color(0xFFF2F5F8),
    onSurface = Color(0xFF141A21),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEBF0F6),
    surfaceContainerHighest = Color(0xFFE0E8F1),
    surfaceVariant = Color(0xFFE6ECF3),
    onSurfaceVariant = Color(0xFF586575),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFDCE3EC),
    error = Color(0xFFC24E4E),
    errorContainer = Color(0xFFFCE8E8),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val EmeraldLight = OceanLight.copy(
    primary = Color(0xFF2D7A56),
    primaryContainer = Color(0xFFD9EFE4),
    onPrimaryContainer = Color(0xFF174B32)
)

private val VioletLight = OceanLight.copy(
    primary = Color(0xFF6654A8),
    primaryContainer = Color(0xFFE6E1F7),
    onPrimaryContainer = Color(0xFF382B6B)
)

@Composable
fun FlClashTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    palette: AppThemePalette = AppThemePalette.DYNAMIC,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }
    val context = LocalContext.current

    val colorScheme = when (palette) {
        AppThemePalette.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (useDark) {
                    val dyn = dynamicDarkColorScheme(context)
                    // Calm matte surfaces & muted tonal containers (zero acid saturation)
                    dyn.copy(
                        primary = lerp(dyn.primary, Color(0xFF9FB8D6), 0.25f),
                        primaryContainer = lerp(dyn.primaryContainer, Color(0xFF1D2632), 0.50f),
                        onPrimaryContainer = Color(0xFFDCEEFF),
                        secondaryContainer = lerp(dyn.secondaryContainer, Color(0xFF1B232D), 0.55f),
                        onSecondaryContainer = Color(0xFFCDE2F7),
                        background = Color(0xFF0B0E11),
                        surface = Color(0xFF0B0E11),
                        surfaceContainerLow = Color(0xFF111519),
                        surfaceContainer = Color(0xFF151A1F),
                        surfaceContainerHigh = Color(0xFF1D232A),
                        surfaceContainerHighest = Color(0xFF252C35),
                        outlineVariant = Color(0xFF232A32)
                    )
                } else {
                    dynamicLightColorScheme(context)
                }
            } else {
                if (useDark) OceanDark else OceanLight
            }
        }
        AppThemePalette.EMERALD -> if (useDark) EmeraldDark else EmeraldLight
        AppThemePalette.OCEAN -> if (useDark) OceanDark else OceanLight
        AppThemePalette.VIOLET -> if (useDark) VioletDark else VioletLight
        AppThemePalette.AMOLED -> if (useDark) AmoledDark else OceanLight
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
