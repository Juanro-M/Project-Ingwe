package io.ingwe.dash.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/*
 * Ingwe design system — quiet, OEM-style instrument cluster for a 1280×720 head unit.
 *
 * Rules:
 *   - Neutral greys carry almost everything. Colour is information, not decoration.
 *   - Amber marks the hero dials (tacho, boost) and the active selection.
 *   - Teal is the "in normal range" fill for secondary readouts (bars, speedometer).
 *   - Red only when something needs the driver's attention; blue only for vacuum / cold.
 *   - No gradients, glows or coloured borders. Hairlines separate, space groups.
 *
 * Dark and light palettes share the same roles. The light one deepens the signal
 * colours so they keep contrast in sunlight (yellow becomes amber-brown).
 */
@Immutable
data class IngwePalette(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val teal: Color,
    val caution: Color,
    val alert: Color,
    val cool: Color,
    val ok: Color,
    val onAccent: Color,
    val isDark: Boolean,
)

val DarkPalette = IngwePalette(
    background = Color(0xFF0B0B0C), surface = Color(0xFF141416), surfaceHigh = Color(0xFF1C1C1F), hairline = Color(0xFF26262A),
    textPrimary = Color(0xFFEDEDEF), textSecondary = Color(0xFF9A9AA1), textTertiary = Color(0xFF5E5E66),
    accent = Color(0xFFFFA43A), teal = Color(0xFF3CCFB8), caution = Color(0xFFFFC857), alert = Color(0xFFFF5A4F),
    cool = Color(0xFF6FB6FF), ok = Color(0xFF5FD38D), onAccent = Color(0xFF1A1206), isDark = true,
)

val LightPalette = IngwePalette(
    background = Color(0xFFF3F3F1), surface = Color(0xFFFFFFFF), surfaceHigh = Color(0xFFE9E9E6), hairline = Color(0xFFDCDCD8),
    textPrimary = Color(0xFF141416), textSecondary = Color(0xFF55555C), textTertiary = Color(0xFF8C8C93),
    accent = Color(0xFFE07800), teal = Color(0xFF0E9E88), caution = Color(0xFFB97D00), alert = Color(0xFFD7372D),
    cool = Color(0xFF2C7BD6), ok = Color(0xFF1F9D57), onAccent = Color(0xFFFFFFFF), isDark = false,
)

/**
 * Current theme. It's Compose snapshot state, so anything that *reads* a colour —
 * composition, layout, or draw code, including cached gauge bitmaps — is
 * automatically refreshed when [isLight] flips. No colour needs passing around.
 */
object IngweThemeState {
    var isLight by mutableStateOf(false)
    val palette: IngwePalette get() = if (isLight) LightPalette else DarkPalette
}

// Colour tokens used throughout the app (getters, so they follow the theme).
val IngweBackground: Color get() = IngweThemeState.palette.background
val IngweSurface: Color get() = IngweThemeState.palette.surface
val IngweSurfaceHigh: Color get() = IngweThemeState.palette.surfaceHigh
val IngweHairline: Color get() = IngweThemeState.palette.hairline
val IngweTextPrimary: Color get() = IngweThemeState.palette.textPrimary
val IngweTextSecondary: Color get() = IngweThemeState.palette.textSecondary
val IngweTextTertiary: Color get() = IngweThemeState.palette.textTertiary
val IngweAccent: Color get() = IngweThemeState.palette.accent
val IngweTeal: Color get() = IngweThemeState.palette.teal
val IngweCaution: Color get() = IngweThemeState.palette.caution
val IngweAlert: Color get() = IngweThemeState.palette.alert
val IngweCool: Color get() = IngweThemeState.palette.cool
val IngweOk: Color get() = IngweThemeState.palette.ok
val IngweOnAccent: Color get() = IngweThemeState.palette.onAccent

@Composable
fun IngweTheme(content: @Composable () -> Unit) {
    val p = IngweThemeState.palette
    val scheme = if (p.isDark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent, secondary = p.textSecondary, background = p.background,
            surface = p.surface, surfaceVariant = p.surfaceHigh, surfaceContainerHigh = p.surfaceHigh,
            onBackground = p.textPrimary, onSurface = p.textPrimary, onSurfaceVariant = p.textSecondary,
            error = p.alert, outline = p.hairline, outlineVariant = p.hairline,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent, secondary = p.textSecondary, background = p.background,
            surface = p.surface, surfaceVariant = p.surfaceHigh, surfaceContainerHigh = p.surfaceHigh,
            onBackground = p.textPrimary, onSurface = p.textPrimary, onSurfaceVariant = p.textSecondary,
            error = p.alert, outline = p.hairline, outlineVariant = p.hairline,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = IngweTypography, content = content)
}
