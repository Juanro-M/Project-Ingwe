package io.ingwe.dash.ui.components.gauges

import androidx.compose.animation.core.spring
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Start-up "ignition sweep", 0 → 1 → 0.
 *
 * Like a real cluster at key-on, every gauge swings to full scale and back.
 * Gauges read this only in the draw phase (or via derivedStateOf for text), so the
 * sweep costs a redraw per frame and no recomposition.
 */
val LocalIgnitionSweep = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

/**
 * Needle physics shared by every gauge: a damped spring (ζ ≈ 0.62) that swings,
 * overshoots slightly and settles — the feel of a real instrument needle.
 */
internal val NeedleSpring = spring<Float>(dampingRatio = 0.62f, stiffness = 120f)

/** Blend a live value towards [full] by the current sweep amount. */
internal fun sweepTowards(value: Float, full: Float, sweep: Float): Float =
    if (sweep <= 0f) value else value + (full - value) * sweep
