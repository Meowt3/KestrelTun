package com.flclash.nativevpn.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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
    primary = Color(0xFF62C493),
    onPrimary = Color(0xFF072617),
    primaryContainer = Color(0xFF1C3A2C),
    onPrimaryContainer = Color(0xFFC8F0DA),
    secondary = Color(0xFF7AB3E8),
    secondaryContainer = Color(0xFF183126)
)

private val TealDark = OceanDark.copy(
    primary = Color(0xFF4ECBC4),
    onPrimary = Color(0xFF062624),
    primaryContainer = Color(0xFF183A39),
    onPrimaryContainer = Color(0xFFC7F3F0),
    secondary = Color(0xFF7AB3E8),
    secondaryContainer = Color(0xFF163131)
)

private val VioletDark = OceanDark.copy(
    primary = Color(0xFFA99AE0),
    onPrimary = Color(0xFF21174A),
    primaryContainer = Color(0xFF2D2847),
    onPrimaryContainer = Color(0xFFE2DCFA),
    secondary = Color(0xFF7AB3E8),
    secondaryContainer = Color(0xFF242038)
)

private val CrimsonDark = OceanDark.copy(
    primary = Color(0xFFE87A88),
    onPrimary = Color(0xFF3B0D14),
    primaryContainer = Color(0xFF472027),
    onPrimaryContainer = Color(0xFFFBD5DA),
    secondary = Color(0xFFE8A86B),
    secondaryContainer = Color(0xFF3A1C22)
)

private val AmberDark = OceanDark.copy(
    primary = Color(0xFFE8B45E),
    onPrimary = Color(0xFF332104),
    primaryContainer = Color(0xFF423217),
    onPrimaryContainer = Color(0xFFFBE6C2),
    secondary = Color(0xFF7AB3E8),
    secondaryContainer = Color(0xFF352814)
)

private val RoseDark = OceanDark.copy(
    primary = Color(0xFFE589B9),
    onPrimary = Color(0xFF3A1026),
    primaryContainer = Color(0xFF452236),
    onPrimaryContainer = Color(0xFFFBD8EA),
    secondary = Color(0xFFA99AE0),
    secondaryContainer = Color(0xFF381C2C)
)

private val MonochromeDark = OceanDark.copy(
    primary = Color(0xFFD0D7DF),
    onPrimary = Color(0xFF12161B),
    primaryContainer = Color(0xFF2A3038),
    onPrimaryContainer = Color(0xFFEDF1F5),
    secondary = Color(0xFF9AA5B1),
    secondaryContainer = Color(0xFF22272E)
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
    primary = Color(0xFF287D56),
    primaryContainer = Color(0xFFD5F0E3),
    onPrimaryContainer = Color(0xFF124A30)
)

private val TealLight = OceanLight.copy(
    primary = Color(0xFF1F7E7A),
    primaryContainer = Color(0xFFD2F0EE),
    onPrimaryContainer = Color(0xFF0E4745)
)

private val VioletLight = OceanLight.copy(
    primary = Color(0xFF6654A8),
    primaryContainer = Color(0xFFE6E1F7),
    onPrimaryContainer = Color(0xFF382B6B)
)

private val CrimsonLight = OceanLight.copy(
    primary = Color(0xFFB83E50),
    primaryContainer = Color(0xFFF9DBE0),
    onPrimaryContainer = Color(0xFF6B1623)
)

private val AmberLight = OceanLight.copy(
    primary = Color(0xFFA86C14),
    primaryContainer = Color(0xFFF9E8CC),
    onPrimaryContainer = Color(0xFF5C3804)
)

private val RoseLight = OceanLight.copy(
    primary = Color(0xFFB0437C),
    primaryContainer = Color(0xFFF8DCEB),
    onPrimaryContainer = Color(0xFF631740)
)

private val MonochromeLight = OceanLight.copy(
    primary = Color(0xFF3E4650),
    primaryContainer = Color(0xFFE2E7EC),
    onPrimaryContainer = Color(0xFF1D2228)
)

private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF080A0D),
    surfaceContainer = Color(0xFF0F1318),
    surfaceContainerHigh = Color(0xFF171C22),
    surfaceContainerHighest = Color(0xFF20262E),
    surfaceVariant = Color(0xFF171C22),
    outlineVariant = Color(0xFF1C2229)
)

@Composable
fun FlClashTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    palette: AppThemePalette = AppThemePalette.DYNAMIC,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    // Backward compatibility if a user previously saved palette == AMOLED
    val isAmoled = themeMode == AppThemeMode.AMOLED || (palette == AppThemePalette.AMOLED && themeMode != AppThemeMode.LIGHT)
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
        AppThemeMode.LIGHT -> false
    }
    val context = LocalContext.current

    val baseScheme = when (palette) {
        AppThemePalette.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (useDark) {
                    val dyn = dynamicDarkColorScheme(context)
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
        AppThemePalette.OCEAN, AppThemePalette.AMOLED -> if (useDark) OceanDark else OceanLight
        AppThemePalette.EMERALD -> if (useDark) EmeraldDark else EmeraldLight
        AppThemePalette.TEAL -> if (useDark) TealDark else TealLight
        AppThemePalette.VIOLET -> if (useDark) VioletDark else VioletLight
        AppThemePalette.CRIMSON -> if (useDark) CrimsonDark else CrimsonLight
        AppThemePalette.AMBER -> if (useDark) AmberDark else AmberLight
        AppThemePalette.ROSE -> if (useDark) RoseDark else RoseLight
        AppThemePalette.MONOCHROME -> if (useDark) MonochromeDark else MonochromeLight
    }

    val finalScheme = if (isAmoled) baseScheme.toAmoled() else baseScheme

    MaterialTheme(
        colorScheme = finalScheme,
        content = content
    )
}
