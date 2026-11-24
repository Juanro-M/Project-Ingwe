package io.ingwe.dash.ui.components.gauges

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
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
    accent: Color = IngweAccent,
    compact: Boolean = false,
    valueText: (Float) -> String = { "%.0f".format(it) },
    tickLabel: (Float) -> String = { "%.0f".format(it) },
) {
    val animated by animateFloatAsState(
        targetValue = value.coerceIn(min, max),
        animationSpec = tween(durationMillis = 220),
        label = "dial",
    )
    val textMeasurer = rememberTextMeasurer()

    val state = when {
        redlineFrom != null && animated >= redlineFrom -> IngweAlert
        cautionFrom != null && animated >= cautionFrom -> IngweCaution
        zeroAt != null && animated < zeroAt -> IngweCool
        coolBelow != null && animated < coolBelow -> IngweCool
        else -> accent
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val startAngle = 135f
            val sweep = 270f
            val radius = this.size.minDimension / 2f
            val arcRadius = radius * 0.92f
            val track = radius * if (compact) 0.04f else 0.025f
            val span = max - min
            fun angleOf(v: Float) = startAngle + ((v - min) / span) * sweep

            fun arc(color: Color, from: Float, to: Float, width: Float, cap: StrokeCap = StrokeCap.Butt) {
                if (to <= from) return
                drawArc(
                    color = color,
                    startAngle = angleOf(from),
                    sweepAngle = angleOf(to) - angleOf(from),
                    useCenter = false,
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                    size = Size(arcRadius * 2, arcRadius * 2),
                    style = Stroke(width = width, cap = cap),
                )
            }

            // Track and zones
            arc(IngweHairline, min, max, track)
            if (zeroAt != null) arc(IngweCool.copy(alpha = 0.35f), min, zeroAt, track)
            if (coolBelow != null) arc(IngweCool.copy(alpha = 0.35f), min, coolBelow, track)
            if (cautionFrom != null) arc(IngweCaution.copy(alpha = 0.45f), cautionFrom, redlineFrom ?: max, track)
            if (redlineFrom != null) arc(IngweAlert.copy(alpha = 0.8f), redlineFrom, max, track)

            // Value arc — from zero (boost) or from min (everything else)
            val origin = zeroAt ?: min
            val (from, to) = if (animated >= origin) origin to animated else animated to origin
            arc(state, from, to, radius * if (compact) 0.07f else 0.045f, StrokeCap.Round)

            // Ticks
            val majorCount = ((max - min) / majorStep).roundToInt()
            for (i in 0..majorCount) {
                val v = min + i * majorStep
                val tickColor = when {
                    redlineFrom != null && v >= redlineFrom -> IngweAlert
                    else -> IngweTextSecondary
                }
                radialLine(angleOf(v), arcRadius - radius * 0.10f, arcRadius - radius * 0.045f, tickColor, radius * if (compact) 0.02f else 0.014f)

                val label = if (compact) "" else tickLabel(v)
                if (label.isNotEmpty()) {
                    val layout = textMeasurer.measure(
                        text = label,
                        style = TextStyle(
                            color = if (tickColor == IngweAlert) IngweAlert else IngweTextTertiary,
                            fontSize = (radius * 0.085f).toSp(), // px → sp via density
                            fontWeight = FontWeight.Medium,
                            fontFeatureSettings = "tnum",
                        ),
                    )
                    val rad = Math.toRadians(angleOf(v).toDouble())
                    val labelRadius = arcRadius - radius * 0.20f
                    drawText(
                        layout,
                        topLeft = Offset(
                            center.x + labelRadius * cos(rad).toFloat() - layout.size.width / 2f,
                            center.y + labelRadius * sin(rad).toFloat() - layout.size.height / 2f,
                        ),
                    )
                }

                if (i < majorCount && !compact) {
                    for (m in 1..minorPerMajor) {
                        val mv = v + m * majorStep / (minorPerMajor + 1)
                        radialLine(angleOf(mv), arcRadius - radius * 0.07f, arcRadius - radius * 0.045f, IngweTextTertiary, radius * 0.008f)
                    }
                }
            }

            // Peak hold — a small mark just outside the track
            if (peak != null && peak in min..max) {
                radialLine(angleOf(peak), arcRadius + radius * 0.025f, arcRadius + radius * 0.075f, IngweTextPrimary, radius * 0.016f)
            }

            // Needle
            val needleAngle = angleOf(animated)
            if (compact) {
                // Sub-dials: no needle, the arc alone carries the value; readout sits centred.
                return@Canvas
            }
            radialLine(needleAngle, -radius * 0.08f, arcRadius - radius * 0.06f, state, radius * 0.022f, StrokeCap.Round)
            drawCircle(IngweSurfaceHigh, radius = radius * 0.07f, center = center)
            drawCircle(state, radius = radius * 0.025f, center = center)
        }

        // Readout
        Column(
            modifier = if (compact) {
                Modifier.align(Alignment.Center).padding(top = size * 0.06f)
            } else {
                Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.08f)
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = valueText(animated),
                style = ReadoutLarge.copy(fontSize = (size.value * if (compact) 0.22f else 0.17f).sp),
                color = if (state == IngweAlert) IngweAlert else IngweTextPrimary,
            )
            Text(
                text = if (compact) unit else "$label · $unit",
                style = LabelStyle,
                color = IngweTextSecondary,
            )
        }
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
) {
    val isBar = unit == BoostUnit.BAR
    DialGauge(
        value = boostFromKPa(boostKPa, unit),
        peak = boostFromKPa(peakKPa, unit),
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
fun TachometerGauge(rpm: Float, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    DialGauge(
        value = rpm,
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
 * One telemetry row: label and value on one line, a thin bar underneath.
 * The bar is teal (in range) until the value is below [coolBelow], at/above [cautionAt]
 * or at/above [alertAt].
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
) {
    val fraction = ((value - min) / (max - min)).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(220), label = "meter")
    val color = statusColor(value, cautionAt, alertAt, coolBelow)

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(label, style = LabelStyle, color = IngweTextSecondary, modifier = Modifier.weight(1f))
            Text(format.format(value), style = ReadoutSmall, color = if (color == IngweTextPrimary) IngweTextPrimary else color)
            Spacer(Modifier.width(3.dp))
            Text(unit, style = LabelStyle, color = IngweTextTertiary)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(IngweHairline),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .background(if (color == IngweTextPrimary) IngweTeal else color),
            )
        }
    }
}

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

/** ZF 8HP: gear, converter lockup, output torque and ATF temperature. */
@Composable
fun TransmissionPanel(
    gear: String,
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
            Stat("Gear", gear, large = true, valueColor = IngweAccent, modifier = Modifier.weight(1f))
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
