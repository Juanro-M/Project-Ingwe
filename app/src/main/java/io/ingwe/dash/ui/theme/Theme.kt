package io.ingwe.dash.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
 */

// Surfaces
val IngweBackground   = Color(0xFF0B0B0C)
val IngweSurface      = Color(0xFF141416)
val IngweSurfaceHigh  = Color(0xFF1C1C1F)
val IngweHairline     = Color(0xFF26262A)

// Text
val IngweTextPrimary   = Color(0xFFEDEDEF)
val IngweTextSecondary = Color(0xFF9A9AA1)
val IngweTextTertiary  = Color(0xFF5E5E66)

// Signal colours
val IngweAccent  = Color(0xFFFFA43A)   // live value, active state
val IngweTeal    = Color(0xFF3CCFB8)   // healthy fill: bar meters, speedometer
val IngweCaution = Color(0xFFFFC857)   // approaching a limit
val IngweAlert   = Color(0xFFFF5A4F)   // over limit / fault
val IngweCool    = Color(0xFF6FB6FF)   // vacuum, below operating temperature
val IngweOk      = Color(0xFF5FD38D)   // used sparingly: link status only

val IngweOnAccent = Color(0xFF1A1206)

private val DarkColorScheme = darkColorScheme(
    primary          = IngweAccent,
    onPrimary        = IngweOnAccent,
    secondary        = IngweTextSecondary,
    background       = IngweBackground,
    surface          = IngweSurface,
    surfaceVariant   = IngweSurfaceHigh,
    surfaceContainerHigh = IngweSurfaceHigh,
    onBackground     = IngweTextPrimary,
    onSurface        = IngweTextPrimary,
    onSurfaceVariant = IngweTextSecondary,
    error            = IngweAlert,
    outline          = IngweHairline,
    outlineVariant   = IngweHairline,
)

@Composable
fun IngweTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = IngweTypography,
        content     = content,
    )
}
