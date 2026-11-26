package io.ingwe.dash.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** The screen size the whole UI is designed for, in dp. */
private val DESIGN_WIDTH = 1280.dp
private val DESIGN_HEIGHT = 720.dp

/**
 * Lays the UI out on a 1280 × 720 dp design canvas and scales it to fit the actual screen.
 *
 * Why: head units report very different densities for the same panel. A 1280 × 720 px
 * screen at 1.5× density is only 853 × 480 dp, so a layout designed for 1280 × 720 dp
 * overflows and gets clipped. Here we change the density Compose uses, so 1 design-dp
 * maps to whatever fraction of a pixel makes the canvas fit. Every size, font and
 * stroke scales together, so the layout looks identical on any head unit.
 *
 * The shorter side decides the scale; the other side keeps the leftover space, so the
 * UI fills the whole screen (on a 1024 × 600 unit the canvas becomes ~1229 × 720).
 * The system font-size setting is ignored on purpose: a cluster must keep its layout.
 */
@Composable
fun FitToDesignSize(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val outer = LocalDensity.current
        val scale = minOf(maxWidth / DESIGN_WIDTH, maxHeight / DESIGN_HEIGHT)
        CompositionLocalProvider(LocalDensity provides Density(outer.density * scale, fontScale = 1f)) {
            content()
        }
    }
}
