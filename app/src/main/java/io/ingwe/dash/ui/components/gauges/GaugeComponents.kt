package io.ingwe.dash.ui.components.gauges

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ingwe.dash.ui.theme.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Display unit for boost pressure. Ranges are gauge (relative) pressure. */
enum class BoostUnit(val label: String) {
    BAR("bar"),
    PSI("psi"),
}

private const val KPA_PER_BAR = 100f
private const val PSI_PER_BAR = 14.5038f
private const val AMBIENT_KPA = 100f

/** Convert absolute manifold pressure (kPa) to gauge pressure in [unit]. */
fun boostFromKPa(absKPa: Float, unit: BoostUnit): Float {
    val bar = (absKPa - AMBIENT_KPA) / KPA_PER_BAR
    return if (unit == BoostUnit.BAR) bar else bar * PSI_PER_BAR
}

fun formatBoost(value: Float, unit: BoostUnit): String {
    val pattern = if (unit == BoostUnit.BAR) "%+.2f" else "%+.1f"
    return pattern.format(value)
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Dial gauge
 * ──────────────────────────────────────────────────────────────────────────── */

/**
 * Flat circular dial: thin track, value arc, fine ticks, slim needle and a
 * centred numeric readout. Colour only changes when the value enters a zone
 * that matters (caution / redline / vacuum).
 *
 * Performance (head units are slow):
 *   - Track, zones, ticks and tick labels are rasterised once into a bitmap and
 *     only rebuilt when the dial's size or scale changes (e.g. bar ⇄ psi).
 *   - The animated value is read only in the draw phase, so a moving needle
 *     redraws the dial without recomposing it.
 *   - The numeric readout recomposes only when its formatted text changes.
 */
@Composable
fun DialGauge(
    value: Float,
    min: Float,
    max: Float,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    majorStep: Float,
    minorPerMajor: Int = 4,
    cautionFrom: Float? = null,
    redlineFrom: Float? = null,
    zeroAt: Float? = null,
    coolBelow: Float? = null,
    peak: Float? = null,
    target: Float? = null,
    targetText: String? = null,
    hubText: String? = null,
    hubColor: Color = IngweAccent,
    accent: Color = IngweAccent,
    compact: Boolean = false,
    valueText: (Float) -> String = { "%.0f".format(it) },
    tickLabel: (Float) -> String = { "%.0f".format(it) },
) {
    // Damped spring: the needle swings, overshoots slightly and settles like a real instrument.
    val animated = animateFloatAsState(
        targetValue = value.coerceIn(min, max),
        animationSpec = NeedleSpring,
        label = "dial",
    )
    val textMeasurer = rememberTextMeasurer()
    val sweep = LocalIgnitionSweep.current
    val currentPeak = rememberUpdatedState(peak)
    val currentTarget = rememberUpdatedState(target)
    val hasHub = hubText != null
    val currentAccent = rememberUpdatedState(accent)
    val currentTickLabel = rememberUpdatedState(tickLabel)

    // Rebuilt only when the scale itself changes — not when the value moves.
    val dialDrawing = remember(min, max, majorStep, minorPerMajor, cautionFrom, redlineFrom, zeroAt, coolBelow, compact, hasHub, textMeasurer, sweep) {
        val scale = DialScale(min, max, majorStep, minorPerMajor, cautionFrom, redlineFrom, zeroAt, coolBelow, compact)
        Modifier.drawWithCache {
            val g = DialGeometry(this.size, scale)
            val staticLayer = ImageBitmap(
                this.size.width.roundToInt().coerceAtLeast(1),
                this.size.height.roundToInt().coerceAtLeast(1),
            )
            CanvasDrawScope().draw(this, layoutDirection, Canvas(staticLayer), this.size) {
                drawDialScale(g, textMeasurer, currentTickLabel.value)
            }
            onDrawBehind {
                drawImage(staticLayer)

                // The spring can overshoot; keep the drawing on the scale.
                val v = sweepTowards(animated.value.coerceIn(min, max), max, sweep.value)
                val color = scale.zoneColor(v, currentAccent.value)
                val origin = zeroAt ?: min
                if (v >= origin) {
                    g.arc(this, color, origin, v, g.radius * if (compact) 0.07f else 0.045f, StrokeCap.Round)
                } else {
                    g.arc(this, color, v, origin, g.radius * if (compact) 0.07f else 0.045f, StrokeCap.Round)
                }

                val pk = currentPeak.value
                if (pk != null && pk in min..max) {
                    radialLine(g.angleOf(pk), g.arcRadius + g.radius * 0.025f, g.arcRadius + g.radius * 0.075f, IngweTextPrimary, g.radius * 0.016f)
                }
                // ECU target (e.g. requested boost): a teal "bug" laid across the track,
                // distinct from the white peak-hold mark outside it.
                val tg = currentTarget.value
                if (tg != null) {
                    val ta = g.angleOf(sweepTowards(tg.coerceIn(min, max), max, sweep.value))
                    radialLine(ta, g.arcRadius - g.radius * 0.04f, g.arcRadius + g.radius * 0.04f, IngweBackground, g.radius * 0.05f)
                    radialLine(ta, g.arcRadius - g.radius * 0.035f, g.arcRadius + g.radius * 0.035f, IngweTeal, g.radius * 0.028f, StrokeCap.Round)
                }

                if (!compact && hasHub) {
                    // Sport-tacho style: a solid hub holds text (the gear); the needle starts at its edge.
                    val hubR = g.radius * HUB_RADIUS
                    radialLine(g.angleOf(v), hubR + g.radius * 0.02f, g.arcRadius - g.radius * 0.06f, color, g.radius * 0.022f, StrokeCap.Round)
                    drawCircle(IngweSurfaceHigh, radius = hubR, center = center)
                    drawCircle(color.copy(alpha = 0.6f), radius = hubR, center = center, style = Stroke(g.radius * 0.012f))
                } else if (!compact) {
                    radialLine(g.angleOf(v), -g.radius * 0.08f, g.arcRadius - g.radius * 0.06f, color, g.radius * 0.022f, StrokeCap.Round)
                    drawCircle(IngweSurfaceHigh, radius = g.radius * 0.07f, center = center)
                    drawCircle(color, radius = g.radius * 0.025f, center = center)
                }
            }
        }
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Spacer(Modifier.fillMaxSize().then(dialDrawing))

        if (hubText != null) {
            Text(
                text = hubText,
                style = ReadoutLarge.copy(fontSize = (size.value * 0.12f).sp, fontWeight = FontWeight.Normal),
                color = hubColor,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        DialReadout(
            animated = animated,
            sweep = sweep,
            min = min,
            max = max,
            valueText = valueText,
            alertFrom = redlineFrom,
            caption = if (compact) unit else "$label · $unit",
            captionExtra = targetText,
            fontSize = (size.value * if (compact) 0.245f else 0.19f).sp,
            modifier = if (compact) {
                Modifier.align(Alignment.Center).padding(top = size * 0.06f)
            } else {
                Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.01f)
            },
        )
        if (compact) {
            Text(
                text = label,
                style = LabelStyle,
                color = IngweTextSecondary,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Numeric readout; recomposes only when the formatted string or alert state changes. */
@Composable
private fun DialReadout(
    animated: State<Float>,
    sweep: State<Float>,
    min: Float,
    max: Float,
    valueText: (Float) -> String,
    alertFrom: Float?,
    caption: String,
    captionExtra: String?,
    fontSize: TextUnit,
    modifier: Modifier,
) {
    val format = rememberUpdatedState(valueText)
    // Read from the same spring as the needle, so the number counts with it.
    val shown = remember(animated, sweep, min, max) { derivedStateOf { sweepTowards(animated.value.coerceIn(min, max), max, sweep.value) } }
    val text by remember(shown) { derivedStateOf { format.value(shown.value) } }
    val alert by remember(shown, alertFrom) { derivedStateOf { alertFrom != null && shown.value >= alertFrom } }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text,
            style = ReadoutLarge.copy(fontSize = fontSize),
            color = if (alert) IngweAlert else IngweTextPrimary,
        )
        Row {
            Text(text = caption, style = LabelStyle, color = IngweTextSecondary)
            if (captionExtra != null) {
                Spacer(Modifier.width(8.dp))
                Text(text = captionExtra, style = LabelStyle.copy(fontFeatureSettings = "tnum"), color = IngweTeal)
            }
        }
    }
}

private const val HUB_RADIUS = 0.27f

/** The fixed scale of a dial — everything except the live value. */
private class DialScale(
    val min: Float,
    val max: Float,
    val majorStep: Float,
    val minorPerMajor: Int,
    val cautionFrom: Float?,
    val redlineFrom: Float?,
    val zeroAt: Float?,
    val coolBelow: Float?,
    val compact: Boolean,
) {
    fun zoneColor(v: Float, accent: Color): Color = when {
        redlineFrom != null && v >= redlineFrom -> IngweAlert
        cautionFrom != null && v >= cautionFrom -> IngweCaution
        zeroAt != null && v < zeroAt -> IngweCool
        coolBelow != null && v < coolBelow -> IngweCool
        else -> accent
    }
}

/** Pixel geometry for one dial size. */
private class DialGeometry(size: Size, val scale: DialScale) {
    val radius = size.minDimension / 2f
    val arcRadius = radius * 0.92f
    val track = radius * if (scale.compact) 0.04f else 0.025f
    private val arcTopLeft = Offset(size.width / 2f - arcRadius, size.height / 2f - arcRadius)
    private val arcSize = Size(arcRadius * 2, arcRadius * 2)

    fun angleOf(v: Float) = START_ANGLE + ((v - scale.min) / (scale.max - scale.min)) * SWEEP

    fun arc(scope: DrawScope, color: Color, from: Float, to: Float, width: Float, cap: StrokeCap = StrokeCap.Butt) {
        if (to <= from) return
        scope.drawArc(
            color = color,
            startAngle = angleOf(from),
            sweepAngle = angleOf(to) - angleOf(from),
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = width, cap = cap),
        )
    }

    private companion object {
        const val START_ANGLE = 135f
        const val SWEEP = 270f
    }
}

/** Static part of a dial: track, zones, ticks and tick labels. Drawn once into a bitmap. */
private fun DrawScope.drawDialScale(g: DialGeometry, textMeasurer: TextMeasurer, tickLabel: (Float) -> String) {
    val s = g.scale
    val r = g.radius

    g.arc(this, IngweHairline, s.min, s.max, g.track)
    if (s.zeroAt != null) g.arc(this, IngweCool.copy(alpha = 0.35f), s.min, s.zeroAt, g.track)
    if (s.coolBelow != null) g.arc(this, IngweCool.copy(alpha = 0.35f), s.min, s.coolBelow, g.track)
    if (s.cautionFrom != null) g.arc(this, IngweCaution.copy(alpha = 0.45f), s.cautionFrom, s.redlineFrom ?: s.max, g.track)
    if (s.redlineFrom != null) g.arc(this, IngweAlert.copy(alpha = 0.8f), s.redlineFrom, s.max, g.track)

    val majorCount = ((s.max - s.min) / s.majorStep).roundToInt()
    val labelStyle = TextStyle(
        fontSize = (r * 0.095f).toSp(), // px → sp via density
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    )
    for (i in 0..majorCount) {
        val v = s.min + i * s.majorStep
        val inRedline = s.redlineFrom != null && v >= s.redlineFrom
        val tickColor = if (inRedline) IngweAlert else IngweTextSecondary
        radialLine(g.angleOf(v), g.arcRadius - r * 0.10f, g.arcRadius - r * 0.045f, tickColor, r * if (s.compact) 0.02f else 0.014f)

        val text = if (s.compact) "" else tickLabel(v)
        if (text.isNotEmpty()) {
            val layout = textMeasurer.measure(text, labelStyle.copy(color = if (inRedline) IngweAlert else IngweTextTertiary))
            val rad = Math.toRadians(g.angleOf(v).toDouble())
            val labelRadius = g.arcRadius - r * 0.20f
            drawText(
                layout,
                topLeft = Offset(
                    center.x + labelRadius * cos(rad).toFloat() - layout.size.width / 2f,
                    center.y + labelRadius * sin(rad).toFloat() - layout.size.height / 2f,
                ),
            )
        }

        if (i < majorCount && !s.compact) {
            for (m in 1..s.minorPerMajor) {
                val mv = v + m * s.majorStep / (s.minorPerMajor + 1)
                radialLine(g.angleOf(mv), g.arcRadius - r * 0.07f, g.arcRadius - r * 0.045f, IngweTextTertiary, r * 0.008f)
            }
        }
    }
}

private fun DrawScope.radialLine(
    angleDeg: Float,
    fromRadius: Float,
    toRadius: Float,
    color: Color,
    width: Float,
    cap: StrokeCap = StrokeCap.Butt,
) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    drawLine(
        color = color,
        start = Offset(center.x + fromRadius * c, center.y + fromRadius * s),
        end = Offset(center.x + toRadius * c, center.y + toRadius * s),
        strokeWidth = width,
        cap = cap,
    )
}

/** Boost: −1…+2.5 bar (−15…+35 psi) with a vacuum zone, overboost redline and peak hold. */
@Composable
fun BoostGauge(
    boostKPa: Float,
    peakKPa: Float,
    unit: BoostUnit,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    targetKPa: Float? = null,
) {
    val isBar = unit == BoostUnit.BAR
    val target = targetKPa?.let { boostFromKPa(it, unit) }
    DialGauge(
        value = boostFromKPa(boostKPa, unit),
        peak = boostFromKPa(peakKPa, unit),
        target = target,
        targetText = target?.let { "target ${formatBoost(it, unit)}" },
        min = if (isBar) -1f else -15f,
        max = if (isBar) 2.5f else 35f,
        majorStep = if (isBar) 0.5f else 5f,
        minorPerMajor = 4,
        cautionFrom = if (isBar) 1.8f else 26f,
        redlineFrom = if (isBar) 2.2f else 32f,
        zeroAt = 0f,
        label = "Boost",
        unit = unit.label,
        size = size,
        modifier = modifier,
        valueText = { formatBoost(it, unit) },
        tickLabel = { if (isBar) "%.1f".format(it).removeSuffix(".0") else if (it.roundToInt() % 10 == 0) "%.0f".format(it) else "" },
    )
}

/** Engine speed, 0–6000 rpm, redline from 4500 (3.0 V6 TDI). */
@Composable
fun TachometerGauge(
    rpm: Float,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    gear: String? = null,
    gearColor: Color = IngweAccent,
) {
    DialGauge(
        value = rpm,
        hubText = gear,
        hubColor = gearColor,
        min = 0f,
        max = 6000f,
        majorStep = 1000f,
        minorPerMajor = 3,
        cautionFrom = 4000f,
        redlineFrom = 4500f,
        label = "Engine",
        unit = "rpm",
        size = size,
        modifier = modifier,
        valueText = { "%,d".format((it / 10f).roundToInt() * 10) },
        tickLabel = { "%.0f".format(it / 1000f) },
    )
}

/** Road speed, 0–220 km/h. */
@Composable
fun SpeedometerGauge(speedKmH: Float, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    DialGauge(
        value = speedKmH,
        min = 0f,
        max = 220f,
        majorStep = 20f,
        minorPerMajor = 1,
        label = "Speed",
        unit = "km/h",
        accent = IngweTeal,
        size = size,
        modifier = modifier,
        valueText = { "%.0f".format(it) },
        tickLabel = { v -> if (v.roundToInt() % 40 == 0) "%.0f".format(v) else "" },
    )
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Sub-dials — compact, needle-less, teal while in range
 * ──────────────────────────────────────────────────────────────────────────── */

/** Engine coolant, 40–130 °C. Blue below operating temperature. */
@Composable
fun CoolantGauge(tempC: Float, modifier: Modifier = Modifier, size: Dp = 124.dp) {
    DialGauge(
        value = tempC,
        min = 40f,
        max = 130f,
        majorStep = 15f,
        coolBelow = 80f,
        cautionFrom = 105f,
        redlineFrom = 115f,
        label = "Coolant",
        unit = "°C",
        accent = IngweTeal,
        compact = true,
        size = size,
        modifier = modifier,
    )
}

/** Engine oil, 40–150 °C. Blue below operating temperature. */
@Composable
fun OilTempGauge(tempC: Float, modifier: Modifier = Modifier, size: Dp = 124.dp) {
    DialGauge(
        value = tempC,
        min = 40f,
        max = 150f,
        majorStep = 22f,
        coolBelow = 85f,
        cautionFrom = 125f,
        redlineFrom = 138f,
        label = "Oil",
        unit = "°C",
        accent = IngweTeal,
        compact = true,
        size = size,
        modifier = modifier,
    )
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Linear meter, panels, small building blocks
 * ──────────────────────────────────────────────────────────────────────────── */

/**
 * One telemetry row: label and value on one line, a bar underneath, then the scale.
 *
 * The bar is teal (in range) until the value is below [coolBelow], at/above [cautionAt]
 * or at/above [alertAt]. Each threshold gets a coloured tick on the track; the scale
 * shows min, max and the thresholds that have room for a label.
 *
 * The value follows the same damped spring as the dial needles, so the bar and the
 * number move together. The bar is drawn in the draw phase; only the number text
 * recomposes, and only when its formatted string changes.
 */
@Composable
fun LinearMeter(
    label: String,
    value: Float,
    unit: String,
    min: Float,
    max: Float,
    modifier: Modifier = Modifier,
    cautionAt: Float? = null,
    alertAt: Float? = null,
    coolBelow: Float? = null,
    format: String = "%.0f",
    showScale: Boolean = true,
) {
    val animated = animateFloatAsState(value, NeedleSpring, label = "meter")
    val sweep = LocalIgnitionSweep.current
    val currentFormat = rememberUpdatedState(format)
    val text by remember(animated) { derivedStateOf { currentFormat.value.format(animated.value) } }
    val color by remember(animated, cautionAt, alertAt, coolBelow) {
        derivedStateOf { statusColor(animated.value, cautionAt, alertAt, coolBelow) }
    }
    val span = max - min
    fun fractionOf(v: Float) = ((v - min) / span).coerceIn(0f, 1f)
    val marks = remember(cautionAt, alertAt, coolBelow) {
        listOfNotNull(coolBelow?.let { it to IngweCool }, cautionAt?.let { it to IngweCaution }, alertAt?.let { it to IngweAlert })
    }

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(label, style = LabelStyle, color = IngweTextSecondary, modifier = Modifier.weight(1f))
            Text(text, style = ReadoutSmall, color = color)
            Spacer(Modifier.width(3.dp))
            Text(unit, style = LabelStyle, color = IngweTextTertiary)
        }
        Spacer(Modifier.height(10.dp))
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .drawBehind {
                    val corner = CornerRadius(size.height / 2f)
                    drawRoundRect(IngweHairline, cornerRadius = corner)
                    val v = animated.value
                    val w = size.width * sweepTowards(fractionOf(v), 1f, sweep.value)
                    val c = statusColor(v, cautionAt, alertAt, coolBelow)
                    if (w > 0f) {
                        drawRoundRect(if (c == IngweTextPrimary) IngweTeal else c, size = Size(w, size.height), cornerRadius = corner)
                    }
                    // Threshold ticks poke out above and below the track
                    val tickW = 2.dp.toPx()
                    val overhang = 3.dp.toPx()
                    marks.forEach { (at, tickColor) ->
                        val x = size.width * fractionOf(at)
                        drawRoundRect(
                            tickColor.copy(alpha = 0.85f),
                            topLeft = Offset(x - tickW / 2f, -overhang),
                            size = Size(tickW, size.height + overhang * 2),
                            cornerRadius = CornerRadius(tickW / 2f),
                        )
                    }
                },
        )
        if (showScale) {
            Spacer(Modifier.height(5.dp))
            MeterScale(min, max, marks)
        }
    }
}

/** Scale numbers under a meter: min, max, and thresholds with ≥ 12 % room either side. */
@Composable
private fun MeterScale(min: Float, max: Float, marks: List<Pair<Float, Color>>) {
    val labels = remember(min, max, marks) {
        val pos = { v: Float -> (v - min) / (max - min) }
        val out = mutableListOf(min to IngweTextTertiary)
        marks.forEach { (at, c) ->
            if (pos(at) - pos(out.last().first) >= 0.12f && pos(max) - pos(at) >= 0.12f) out += at to c
        }
        out += max to IngweTextTertiary
        out.map { (v, c) -> Triple(pos(v).coerceIn(0f, 1f), formatScale(v), c) }
    }
    Box(Modifier.fillMaxWidth()) {
        labels.forEach { (fraction, text, c) ->
            Text(
                text = text,
                style = LabelStyle.copy(fontSize = 11.sp, fontFeatureSettings = "tnum"),
                color = c,
                // Bias −1…1 maps the label from the left edge to the right edge, so the
                // end labels sit flush and middle labels land on their tick.
                modifier = Modifier.align(BiasAlignment(horizontalBias = fraction * 2f - 1f, verticalBias = 0f)),
            )
        }
    }
}

private fun formatScale(v: Float): String =
    if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else "%.1f".format(v)

private fun statusColor(value: Float, cautionAt: Float?, alertAt: Float?, coolBelow: Float?): Color = when {
    alertAt != null && value >= alertAt -> IngweAlert
    cautionAt != null && value >= cautionAt -> IngweCaution
    coolBelow != null && value < coolBelow -> IngweCool
    else -> IngweTextPrimary
}

/** Plain surface with an optional quiet title and trailing status. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(IngweSurface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        if (title != null || trailing != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (title != null) {
                    Text(title, style = LabelStyle, color = IngweTextSecondary, modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(14.dp))
        }
        content()
    }
}

/** Small "label over value" readout. */
@Composable
fun Stat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = IngweTextPrimary,
    large: Boolean = false,
) {
    Column(modifier) {
        Text(label, style = LabelStyle, color = IngweTextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = if (large) ReadoutLarge else ReadoutMedium, color = valueColor)
            if (unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    unit,
                    style = LabelStyle,
                    color = IngweTextTertiary,
                    modifier = Modifier.padding(bottom = if (large) 8.dp else 3.dp),
                )
            }
        }
    }
}

/** Status text with a small leading dot — e.g. "Locked", "Slipping". */
@Composable
fun StatusDot(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = LabelStyle, color = IngweTextPrimary)
    }
}

/** Compact segmented control (e.g. bar / psi). */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(IngweSurface)
            .padding(2.dp),
    ) {
        options.forEach { (key, text) ->
            val isSelected = key == selected
            Text(
                text = text,
                style = LabelStyle,
                color = if (isSelected) IngweTextPrimary else IngweTextTertiary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) IngweSurfaceHigh else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Feature panels
 * ──────────────────────────────────────────────────────────────────────────── */

enum class LockupState(val label: String) { LOCKED("Locked"), SLIP("Slipping"), OPEN("Open") }

/** ZF 8HP: engine load, converter lockup, output torque and ATF temperature. (Gear is in the tacho hub.) */
@Composable
fun TransmissionPanel(
    engineLoadPct: Float,
    lockup: LockupState,
    atfTempC: Float,
    engineTorqueNm: Float,
    modifier: Modifier = Modifier,
) {
    Panel(
        modifier = modifier,
        title = "ZF 8HP transmission",
        trailing = {
            StatusDot(
                text = "Converter ${lockup.label.lowercase()}",
                color = when (lockup) {
                    LockupState.LOCKED -> IngweTeal
                    LockupState.SLIP -> IngweCaution
                    LockupState.OPEN -> IngweCool
                },
            )
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Stat("Engine load", "%.0f".format(engineLoadPct), unit = "%", large = true, modifier = Modifier.weight(1f))
            Stat("Engine torque", "%.0f".format(engineTorqueNm), unit = "Nm", large = true)
        }
        Spacer(Modifier.height(18.dp))
        LinearMeter(
            label = "ATF temperature",
            value = atfTempC,
            unit = "°C",
            min = 40f,
            max = 130f,
            coolBelow = 65f,
            cautionAt = 105f,
            alertAt = 115f,
        )
    }
}
