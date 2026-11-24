package io.ingwe.dash.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Ingwe Design System — dark, high-contrast, optimised for 1280×720 LCD.
 *
 * Colour palette inspired by a leopard's coat:
 *   Background   #0A0A0A  — near-black
 *   Surface      #141414  — dark card
 *   Primary      #F5A623  — amber/gold (gauge needles, active states)
 *   Secondary    #3DDC84  — Android green (connected, OK status)
 *   Error        #FF4444  — alert red
 *   OnSurface    #E8E8E8  — primary text
 *   Outline      #2A2A2A  — card borders
 */

val IngweBackground   = Color(0xFF0A0A0A)
val IngweSurface      = Color(0xFF141414)
val IngweSurfaceVar   = Color(0xFF1E1E1E)
val IngwePrimary      = Color(0xFFF5A623)   // amber — gauge needles, highlights
val IngweSecondary    = Color(0xFF3DDC84)   // green — connected/OK
val IngweError        = Color(0xFFFF4444)   // red — warnings, errors
val IngweOnPrimary    = Color(0xFF1A0F00)
val IngweOnSurface    = Color(0xFFE8E8E8)
val IngweOnBackground = Color(0xFFE8E8E8)
val IngweOutline      = Color(0xFF2A2A2A)
val IngweWarning      = Color(0xFFFFB84D)   // orange — soft warnings

private val DarkColorScheme = darkColorScheme(
    primary           = IngwePrimary,
    onPrimary         = IngweOnPrimary,
    secondary         = IngweSecondary,
    background        = IngweBackground,
    surface           = IngweSurface,
    surfaceVariant    = IngweSurfaceVar,
    onBackground      = IngweOnBackground,
    onSurface         = IngweOnSurface,
    error             = IngweError,
    outline           = IngweOutline,
)

@Composable
fun IngweTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = IngweTypography,
        content     = content,
    )
}
