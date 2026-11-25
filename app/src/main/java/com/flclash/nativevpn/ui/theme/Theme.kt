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

// Comfortable, soft neutral charcoal-gray base (not overly dark) + delicate pastel accents
private fun buildPastelDarkScheme(accent: Color): ColorScheme {
    val container = lerp(Color(0xFF333944), accent, 0.18f)
    val secContainer = lerp(Color(0xFF2E333D), accent, 0.12f)
    return darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF13171F),
        primaryContainer = container,
        onPrimaryContainer = Color(0xFFF0F4FA),
        secondary = lerp(accent, Color(0xFFB8C0CC), 0.35f),
        onSecondary = Color(0xFF13171F),
        secondaryContainer = secContainer,
        onSecondaryContainer = Color(0xFFE5EAF2),
        background = Color(0xFF1A1D22),
        onBackground = Color(0xFFF0F3F8),
        surface = Color(0xFF1A1D22),
        onSurface = Color(0xFFF0F3F8),
        surfaceContainerLow = Color(0xFF20242B),
        surfaceContainer = Color(0xFF2E333D),
        surfaceContainerHigh = Color(0xFF2E333D),
        surfaceContainerHighest = Color(0xFF373D49),
        surfaceVariant = Color(0xFF2E333D),
        onSurfaceVariant = Color(0xFF9BA3B0),
        outline = Color(0xFF3E4552),
        outlineVariant = Color(0xFF3A414E),
        error = Color(0xFFE89B9B),
        errorContainer = Color(0xFF3D2629),
        onErrorContainer = Color(0xFFF7D6D6)
    )
}

private fun buildPastelLightScheme(accent: Color): ColorScheme {
    val primary = lerp(accent, Color(0xFF20252E), 0.42f)
    val container = lerp(Color(0xFFEAECEF), accent, 0.35f)
    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = container,
        onPrimaryContainer = Color(0xFF16191F),
        secondary = lerp(primary, Color(0xFF566070), 0.3f),
        onSecondary = Color.White,
        secondaryContainer = lerp(Color(0xFFEAECEF), accent, 0.22f),
        onSecondaryContainer = Color(0xFF1A1E26),
        background = Color(0xFFF3F4F6),
        onBackground = Color(0xFF16191F),
        surface = Color(0xFFF3F4F6),
        onSurface = Color(0xFF16191F),
        surfaceContainerLow = Color(0xFFF9FAFB),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFECEEF2),
        surfaceContainerHighest = Color(0xFFE2E6EC),
        surfaceVariant = Color(0xFFEAEEF3),
        onSurfaceVariant = Color(0xFF626B79),
        outline = Color(0xFF9AA3B2),
        outlineVariant = Color(0xFFDDE2EA),
        error = Color(0xFFC25B5B),
        errorContainer = Color(0xFFFCEAEA),
        onErrorContainer = Color(0xFF7A1F1F)
    )
}

// 9 Soft Pastel Accent Colors
private val PastelSky = Color(0xFFA8C7FA)
private val PastelMint = Color(0xFFA2DEC0)
private val PastelAqua = Color(0xFF9ADCD7)
private val PastelLavender = Color(0xFFC9B8FA)
private val PastelPeach = Color(0xFFF8C4A6)
private val PastelRose = Color(0xFFF4B6D0)
private val PastelCream = Color(0xFFF2DCA2)
private val PastelMist = Color(0xFFD6DCE5)

private val OceanDark = buildPastelDarkScheme(PastelSky)
private val EmeraldDark = buildPastelDarkScheme(PastelMint)
private val TealDark = buildPastelDarkScheme(PastelAqua)
private val VioletDark = buildPastelDarkScheme(PastelLavender)
private val CrimsonDark = buildPastelDarkScheme(PastelPeach)
private val RoseDark = buildPastelDarkScheme(PastelRose)
private val AmberDark = buildPastelDarkScheme(PastelCream)
private val MonochromeDark = buildPastelDarkScheme(PastelMist)

private val OceanLight = buildPastelLightScheme(PastelSky)
private val EmeraldLight = buildPastelLightScheme(PastelMint)
private val TealLight = buildPastelLightScheme(PastelAqua)
private val VioletLight = buildPastelLightScheme(PastelLavender)
private val CrimsonLight = buildPastelLightScheme(PastelPeach)
private val RoseLight = buildPastelLightScheme(PastelRose)
private val AmberLight = buildPastelLightScheme(PastelCream)
private val MonochromeLight = buildPastelLightScheme(PastelMist)

private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0C0F),
    surfaceContainer = Color(0xFF121418),
    surfaceContainerHigh = Color(0xFF1B1E24),
    surfaceContainerHighest = Color(0xFF242932),
    surfaceVariant = Color(0xFF1B1E24),
    outlineVariant = Color(0xFF20252D)
)

@Composable
fun FlClashTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    palette: AppThemePalette = AppThemePalette.DYNAMIC,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
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
                    // Neutral gray surfaces + gentle pastelized dynamic accent
                    buildPastelDarkScheme(lerp(dyn.primary, Color(0xFFE6ECF5), 0.25f))
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
