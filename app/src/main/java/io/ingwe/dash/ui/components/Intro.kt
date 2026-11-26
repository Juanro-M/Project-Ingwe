package io.ingwe.dash.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ingwe.dash.ui.components.gauges.LocalIgnitionSweep
import io.ingwe.dash.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Wordmark shown in the intro. */
const val APP_NAME = "ARGUS"

private const val END_MS = 4130f
private const val SKIP_TO_MS = 2420f

/**
 * Start-up (≈4 s, once per launch, tap to skip):
 *   a slit draws out → the eye opens (upper lid leads) → the gauge-ring iris spins down →
 *   it glances left / right → locks on: pupil narrows, the core flares →
 *   ARGUS resolves letter by letter with "Welcome back" → blink → the slit collapses to a
 *   glowing point → every gauge underneath does its ignition sweep.
 *
 * Cost: one Animatable clock. Everything that moves reads it in the draw phase
 * (Canvas / graphicsLayer), so nothing recomposes per frame, and the whole overlay
 * leaves the composition once it has faded.
 */
@Composable
fun IgnitionIntro(content: @Composable () -> Unit) {
    var played by rememberSaveable { mutableStateOf(false) }
    val clock = remember { Animatable(if (played) END_MS else 0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        clock.animateTo(END_MS, tween(END_MS.toInt(), easing = LinearEasing))
        played = true
    }
    val sweep = remember { derivedStateOf { sweepAt(clock.value) } }

    CompositionLocalProvider(LocalIgnitionSweep provides sweep) {
        Box(Modifier.fillMaxSize().background(IngweBackground)) {
            content()

            val showOverlay by remember { derivedStateOf { overlayAlpha(clock.value) > 0f } }
            if (showOverlay) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = overlayAlpha(clock.value) }
                        .background(IngweBackground)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            scope.launch {
                                if (clock.value < SKIP_TO_MS) clock.snapTo(SKIP_TO_MS)
                                clock.animateTo(END_MS, tween((END_MS - clock.value).toInt().coerceAtLeast(1), easing = LinearEasing))
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Backdrop(Modifier.fillMaxSize())
                    Canvas(Modifier.offset(y = (-30).dp).size(440.dp, 240.dp)) { drawEye(clock.value) }
                    Wordmark(clock)
                }
            }
        }
    }
}

/* ── Timeline helpers (ms) ───────────────────────────────────────────────── */

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun ease(t: Float) = if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2
private fun easeOut(t: Float) = 1 - (1 - t).pow(3)
private fun backOut(t: Float): Float { val s = 1.4f; return 1 + (s + 1) * (t - 1).pow(3) + s * (t - 1).pow(2) }

private fun overlayAlpha(t: Float) = 1f - ease(clamp01((t - 2560f) / 380f))

/** Needle sweep once the eye has blinked: up 2600–3250, hold, down 3330–4130. */
private fun sweepAt(t: Float) = when {
    t < 2600f -> 0f
    t < 3250f -> ease((t - 2600f) / 650f)
    t < 3330f -> 1f
    t < END_MS -> 1f - easeOut(clamp01((t - 3330f) / 800f))
    else -> 0f
}

private fun lookAt(t: Float) = when {
    t < 1000f -> 0f
    t < 1230f -> -ease((t - 1000f) / 230f)
    t < 1560f -> -1f + 2f * ease((t - 1230f) / 330f)
    else -> 1f - ease(clamp01((t - 1560f) / 260f))
}

/* ── Wordmark ─────────────────────────────────────────────────────────────── */

@Composable
private fun Wordmark(clock: Animatable<Float, *>) {
    Column(Modifier.offset(y = 128.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.graphicsLayer { alpha = 1f - ease(clamp01((clock.value - 2250f) / 300f)) }) {
            APP_NAME.forEachIndexed { i, ch ->
                Text(
                    ch.toString(),
                    style = LabelStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Light, letterSpacing = 0.sp),
                    color = IngweTextPrimary,
                    modifier = Modifier
                        .padding(horizontal = 7.dp)
                        .graphicsLayer {
                            val p = easeOut(clamp01((clock.value - 1150f - i * 70f) / 380f))
                            alpha = p
                            translationY = (1f - p) * 10.dp.toPx()
                        },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Welcome back",
            style = LabelStyle.copy(fontSize = 12.sp, letterSpacing = 2.sp),
            color = IngweTextTertiary,
            modifier = Modifier.graphicsLayer {
                val t = clock.value
                val p = easeOut(clamp01((t - 1650f) / 400f))
                alpha = p * (1f - ease(clamp01((t - 2250f) / 300f)))
                translationY = (1f - p) * 6.dp.toPx()
            },
        )
    }
}

/* ── Backdrop: fine dot grid fading toward the edges + a faint warm glow ──── */

@Composable
private fun Backdrop(modifier: Modifier) {
    val dot = IngweTextPrimary
    val accent = IngweAccent
    Box(
        modifier.drawWithCache {
            // Dots grouped into a few alpha bands → a handful of drawPoints calls, built once.
            val step = 22.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height * 0.46f
            val rx = size.width * 0.55f
            val ry = size.height * 0.60f
            val bands = Array(6) { ArrayList<Offset>() }
            var y = step / 2
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) {
                    val a = 1f - sqrt(((x - cx) / rx).pow(2) + ((y - cy) / ry).pow(2))
                    if (a > 0f) bands[min(5, (a * 6).toInt())].add(Offset(x, y))
                    x += step
                }
                y += step
            }
            val glow = Brush.radialGradient(
                listOf(accent.copy(alpha = 0.07f), Color.Transparent),
                center = Offset(cx, size.height * 0.44f), radius = size.minDimension * 0.5f,
            )
            val dotPx = 2.dp.toPx()
            onDrawBehind {
                bands.forEachIndexed { i, pts ->
                    if (pts.isNotEmpty()) drawPoints(pts, PointMode.Points, dot.copy(alpha = 0.09f * (i + 1) / 6f), strokeWidth = dotPx, cap = StrokeCap.Round)
                }
                drawRect(glow)
            }
        },
    )
}

/* ── The eye ─────────────────────────────────────────────────────────────── */

private class Fibre(val a: Float, val l: Float)
private val FIBRES = List(96) { i -> Fibre((i / 96f) * 2f * PI.toFloat() + sin(i * 12.9898f) * 0.03f, 0.55f + ((sin(i * 78.233f) + 1f) / 2f) * 0.4f) }

private fun DrawScope.drawEye(t: Float) {
    val light = IngweThemeState.isLight
    val accent = IngweAccent
    val teal = IngweTeal
    val text = IngweTextPrimary
    val d = density
    val cx = size.width / 2f
    val cy = size.height / 2f
    val w = 150f * d
    val look = lookAt(t)

    val slit = easeOut(clamp01(t / 280f)) * (1f - ease(clamp01((t - 2420f) / 200f)))
    val blink = ease(clamp01((t - 2200f) / 220f))
    val up = 64f * d * backOut(clamp01((t - 260f) / 620f)) * (1f - blink)
    val low = 54f * d * clamp01((t - 300f) / 600f) * (1f - blink)
    val flare = sin(PI.toFloat() * clamp01((t - 1820f) / 420f))
    val open = up / (64f * d)

    // Warm glow behind the eye (kept inside the canvas so there's no visible edge)
    drawRect(Brush.radialGradient(listOf(accent.copy(alpha = clamp01(0.10f * open + 0.12f * flare)), Color.Transparent), Offset(cx, cy), size.height / 2f - 2f))

    // Sonar rings
    for (k in 0 until 3) {
        val p = clamp01((t - 950f - k * 240f) / 950f)
        if (p > 0f && p < 1f) {
            val rw = w * (0.8f + p * 0.85f)
            val rh = (up + 10f * d) * (0.9f + p * 1.1f)
            drawOval(teal.copy(alpha = 0.3f * (1f - p)), Offset(cx - rw, cy - rh), Size(rw * 2, rh * 2), style = Stroke(1.2f * d))
        }
    }

    val lid = Path().apply {
        moveTo(cx - w * slit, cy)
        quadraticTo(cx, cy - 2 * up, cx + w * slit, cy)
        quadraticTo(cx, cy + 2 * low * 0.85f, cx - w * slit, cy)
        close()
    }

    if (open > 0.02f && slit > 0.05f) {
        clipPath(lid) {
            val r = 54f * d
            val ix = cx + look * 46f * d
            val iy = cy + abs(look) * 4f * d
            // Iris core — warm fire that flares on lock
            drawCircle(
                Brush.radialGradient(
                    0f to accent,
                    (0.4f + flare * 0.15f) to accent.copy(alpha = clamp01((if (light) 0.6f else 0.38f) + flare * 0.3f)),
                    1f to accent.copy(alpha = if (light) 0.16f else 0.05f),
                    center = Offset(ix, iy), radius = r,
                ),
                r, Offset(ix, iy),
            )
            // Fibres for depth
            val spin = (1f - easeOut(clamp01((t - 260f) / 1450f))) * PI.toFloat() * 3f
            val fibre = if (light) Color(0xFF5A2E00).copy(alpha = 0.16f) else text.copy(alpha = 0.10f)
            for (f in FIBRES) {
                val a = f.a + spin * 0.35f
                val r0 = 12f * d
                val r1 = (r - 14f * d) * f.l
                drawLine(fibre, Offset(ix + cos(a) * r0, iy + sin(a) * r0), Offset(ix + cos(a) * r1, iy + sin(a) * r1), strokeWidth = d)
            }
            // Gauge-ring ticks, flashing bright the moment it locks
            val tickFlash = max(0f, 1f - abs(t - 1800f) / 160f)
            for (i in 0 until 60) {
                val a = (i / 60f) * 2f * PI.toFloat() + spin
                val major = i % 5 == 0
                val r0 = r - (if (major) 10f else 6f) * d
                val r1 = r - 2f * d
                val c = if (tickFlash > 0f && major) text else teal.copy(alpha = if (major) 0.95f else 0.4f + tickFlash * 0.4f)
                drawLine(c, Offset(ix + cos(a) * r0, iy + sin(a) * r0), Offset(ix + cos(a) * r1, iy + sin(a) * r1), strokeWidth = (if (major) 2f else 1f) * d)
            }
            drawCircle(teal.copy(alpha = 0.5f), r - 14f * d, Offset(ix, iy), style = Stroke(d))
            // Pupil slit narrows as it focuses
            val pw = (14f - 8f * easeOut(clamp01((t - 1550f) / 300f)) + abs(look) * 4f) * d
            val ph = r * 0.78f
            drawOval(if (light) Color(0xFF141416) else IngweBackground, Offset(ix - pw, iy - ph), Size(pw * 2, ph * 2))
            drawOval(accent.copy(alpha = 0.9f), Offset(ix - pw, iy - ph), Size(pw * 2, ph * 2), style = Stroke(1.5f * d))
            // Scan line while it searches
            val sp = clamp01((t - 1000f) / 700f)
            if (sp > 0f && sp < 1f) {
                val sy = cy - up + sp * (up + low) * 2f
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, teal.copy(alpha = 0.35f), Color.Transparent), startY = sy - 6f * d, endY = sy + 6f * d),
                    Offset(cx - w, sy - 6f * d), Size(w * 2, 12f * d),
                )
            }
            // Glint, and the upper lid's soft shadow
            drawCircle(Color.White.copy(alpha = 0.8f), 3.2f * d, Offset(ix - 18f * d, iy - 20f * d))
            drawRect(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (light) 0.14f else 0.45f), Color.Transparent), startY = cy - up, endY = cy - up + 26f * d),
                Offset(cx - w, cy - up - 2f * d), Size(w * 2, 30f * d),
            )
        }
    }

    // Lid outline, fading out at the corners
    if (slit > 0.05f) {
        drawPath(
            lid,
            Brush.horizontalGradient(
                0f to Color.Transparent, 0.18f to text.copy(alpha = 0.9f), 0.82f to text.copy(alpha = 0.9f), 1f to Color.Transparent,
                startX = cx - w, endX = cx + w,
            ),
            style = Stroke(2f * d, join = StrokeJoin.Round),
        )
    }
    // Crease above the upper lid
    if (open > 0.3f) {
        val crease = Path().apply {
            moveTo(cx - w * 0.7f, cy - up * 0.55f)
            quadraticTo(cx, cy - 2 * up - 14f * d, cx + w * 0.7f, cy - up * 0.55f)
        }
        drawPath(crease, text.copy(alpha = 0.18f * open), style = Stroke(d))
    }
    // The collapse: a bright point that pops and fades
    val dot = clamp01((t - 2520f) / 120f) * (1f - clamp01((t - 2640f) / 260f))
    if (dot > 0f) {
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = dot), Color.Transparent), Offset(cx, cy), (18f + dot * 10f) * d), 30f * d, Offset(cx, cy))
    }
}
