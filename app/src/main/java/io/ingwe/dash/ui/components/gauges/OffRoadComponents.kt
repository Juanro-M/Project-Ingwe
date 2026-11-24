package io.ingwe.dash.ui.components.gauges

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import io.ingwe.dash.ui.theme.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

enum class DiffState(val label: String) { OPEN("Open"), LOCKED("Locked") }

/** Per-wheel slip, front-left → rear-right. */
data class WheelSlip(val fl: Float, val fr: Float, val rl: Float, val rr: Float) {
    val max get() = maxOf(fl, fr, rl, rr)
}

private const val SLIP_CAUTION = 6f
private const val SLIP_ALERT = 15f

private fun slipColor(pct: Float) = when {
    pct >= SLIP_ALERT -> IngweAlert
    pct >= SLIP_CAUTION -> IngweCaution
    else -> IngweTeal
}

private fun angleColor(deg: Float, caution: Float, alert: Float) = when {
    abs(deg) >= alert -> IngweAlert
    abs(deg) >= caution -> IngweCaution
    else -> IngweTeal
}

/**
 * 4Motion panel: steering wheel that turns with the real angle, a top-down drivetrain
 * with each wheel coloured by slip, centre / rear differential state and the current
 * front:rear torque split. (Pitch and roll have their own [Clinometer]s.)
 */
@Composable
fun OffRoadPanel(
    steeringAngleDeg: Float,
    wheelSlip: WheelSlip,
    rearDiff: DiffState,
    rearTorquePct: Float,
    modifier: Modifier = Modifier,
) {
    val steer = animateFloatAsState(steeringAngleDeg, NeedleSpring, label = "steer")

    val traction = when {
        wheelSlip.max >= SLIP_ALERT -> "Wheels slipping" to IngweAlert
        wheelSlip.max >= SLIP_CAUTION -> "Light slip" to IngweCaution
        else -> "Full grip" to IngweTeal
    }

    Panel(
        modifier = modifier,
        title = "4Motion",
        trailing = { StatusDot(traction.first, traction.second) },
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            val s = steeringAngleDeg.roundToInt()
            AngleTile(
                label = "Steering",
                value = "${abs(s)}°",
                caption = when { s > 0 -> "right"; s < 0 -> "left"; else -> "centre" },
                color = IngweTeal,
                modifier = Modifier.width(172.dp).fillMaxHeight(),
            ) { drawSteeringWheel(steer.value) }
            Spacer(Modifier.width(20.dp))
            DrivetrainDiagram(wheelSlip = wheelSlip, rearDiff = rearDiff)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusLine("Centre diff", "Torsen · ${(100 - rearTorquePct).roundToInt()} : ${rearTorquePct.roundToInt()}", IngweTeal)
                StatusLine(
                    "Rear diff lock",
                    if (rearDiff == DiffState.LOCKED) "Engaged" else "Open",
                    if (rearDiff == DiffState.LOCKED) IngweAccent else IngweTextTertiary,
                )
                TorqueSplitBar(rearTorquePct)
                LinearMeter(
                    label = "Highest wheel slip",
                    value = wheelSlip.max,
                    unit = "%",
                    min = 0f,
                    max = 30f,
                    cautionAt = SLIP_CAUTION,
                    alertAt = SLIP_ALERT,
                    format = "%.1f",
                )
            }
        }
    }
}

enum class ClinometerKind { ROLL, PITCH }

/**
 * Roll or pitch instrument: a 180° scale from −45° to +45° with caution / alert zones,
 * a pointer and live arc, and the truck (rear view for roll, side view for pitch)
 * tilting against a dashed level line. The angle follows the needle spring.
 */
@Composable
fun Clinometer(
    kind: ClinometerKind,
    angleDeg: Float,
    modifier: Modifier = Modifier,
) {
    val caution = if (kind == ClinometerKind.ROLL) 20f else 25f
    val alert = if (kind == ClinometerKind.ROLL) 30f else 35f
    val animated = animateFloatAsState(angleDeg, NeedleSpring, label = "clino")
    val textMeasurer = rememberTextMeasurer()

    val shownDeg by remember(animated) { derivedStateOf { animated.value.roundToInt() } }
    val valueColor = angleColor(shownDeg.toFloat(), caution, alert).let { if (it == IngweTeal) IngweTextPrimary else it }
    val caption = when (kind) {
        ClinometerKind.ROLL -> when { shownDeg > 0 -> "right side down"; shownDeg < 0 -> "left side down"; else -> "level" }
        ClinometerKind.PITCH -> when { shownDeg > 0 -> "nose up"; shownDeg < 0 -> "nose down"; else -> "level" }
    }

    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(IngweSurface)
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (kind == ClinometerKind.ROLL) "Roll" else "Pitch", style = LabelStyle, color = IngweTextSecondary, modifier = Modifier.weight(1f))
            Text("${abs(shownDeg)}°", style = ReadoutMedium, color = valueColor)
            Spacer(Modifier.width(6.dp))
            Text(caption, style = LabelStyle, color = IngweTextTertiary, modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .drawWithCache {
                    val labelStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
                    val labels = (-45..45 step 15).associateWith { d ->
                        textMeasurer.measure(abs(d).toString(), labelStyle.copy(color = if (abs(d) >= alert) IngweAlert else IngweTextTertiary))
                    }
                    onDrawBehind { drawClinometer(kind, animated.value, caution, alert, labels) }
                },
        )
    }
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Building blocks
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
private fun AngleTile(
    label: String,
    value: String,
    caption: String,
    color: Color,
    modifier: Modifier = Modifier,
    draw: DrawScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(IngweBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = LabelStyle, color = IngweTextSecondary)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(78.dp)
                .padding(vertical = 4.dp),
        ) { draw() }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = ReadoutMedium, color = if (color == IngweTeal) IngweTextPrimary else color)
            Spacer(Modifier.width(6.dp))
            Text(caption, style = LabelStyle, color = IngweTextTertiary, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String, dot: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = LabelStyle, color = IngweTextSecondary, modifier = Modifier.weight(1f))
        StatusDot(value, dot)
    }
}

/** Front | rear torque share as one split bar. */
@Composable
private fun TorqueSplitBar(rearPct: Float) {
    val rear by animateFloatAsState((rearPct / 100f).coerceIn(0f, 1f), tween(220), label = "split")
    Column {
        Row {
            Text("Front", style = LabelStyle, color = IngweTextTertiary, modifier = Modifier.weight(1f))
            Text("Rear", style = LabelStyle, color = IngweTextTertiary)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
        ) {
            Box(Modifier.weight(1f - rear + 0.0001f).fillMaxHeight().background(IngweTextSecondary))
            Spacer(Modifier.width(2.dp))
            Box(Modifier.weight(rear + 0.0001f).fillMaxHeight().background(IngweTeal))
        }
    }
}

/** Top-down chassis: wheels coloured by slip, axles, prop shaft, diffs. Front is up. */
@Composable
private fun DrivetrainDiagram(wheelSlip: WheelSlip, rearDiff: DiffState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        WheelLabels(top = wheelSlip.fl, bottom = wheelSlip.rl, alignEnd = true)
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Front", style = LabelStyle, color = IngweTextTertiary)
            Canvas(Modifier.size(width = 92.dp, height = 132.dp)) {
                val w = size.width
                val h = size.height
                val wheelW = w * 0.20f
                val wheelH = h * 0.22f
                val frontY = h * 0.18f
                val rearY = h * 0.82f
                val leftX = wheelW / 2f
                val rightX = w - wheelW / 2f
                val shaft = 2.5.dp.toPx()
                val line = IngweTextTertiary

                // Axles and prop shaft
                drawLine(line, Offset(leftX, frontY), Offset(rightX, frontY), shaft)
                drawLine(line, Offset(leftX, rearY), Offset(rightX, rearY), shaft)
                drawLine(line, Offset(w / 2f, frontY), Offset(w / 2f, rearY), shaft)

                // Front diff (open), centre diff (Torsen), rear diff (open / locked)
                val diffR = w * 0.09f
                drawCircle(IngweSurface, diffR, Offset(w / 2f, frontY))
                drawCircle(IngweTextSecondary, diffR, Offset(w / 2f, frontY), style = Stroke(1.5.dp.toPx()))
                drawCircle(IngweSurface, diffR * 1.15f, Offset(w / 2f, h / 2f))
                drawCircle(IngweTeal, diffR * 1.15f, Offset(w / 2f, h / 2f), style = Stroke(1.5.dp.toPx()))
                if (rearDiff == DiffState.LOCKED) {
                    drawRoundRect(
                        IngweAccent,
                        topLeft = Offset(w / 2f - diffR, rearY - diffR),
                        size = Size(diffR * 2, diffR * 2),
                        cornerRadius = CornerRadius(diffR * 0.4f),
                    )
                } else {
                    drawCircle(IngweSurface, diffR, Offset(w / 2f, rearY))
                    drawCircle(IngweTextSecondary, diffR, Offset(w / 2f, rearY), style = Stroke(1.5.dp.toPx()))
                }

                // Wheels
                listOf(
                    Offset(leftX, frontY) to wheelSlip.fl,
                    Offset(rightX, frontY) to wheelSlip.fr,
                    Offset(leftX, rearY) to wheelSlip.rl,
                    Offset(rightX, rearY) to wheelSlip.rr,
                ).forEach { (c, slip) ->
                    drawRoundRect(
                        slipColor(slip),
                        topLeft = Offset(c.x - wheelW / 2f, c.y - wheelH / 2f),
                        size = Size(wheelW, wheelH),
                        cornerRadius = CornerRadius(wheelW * 0.3f),
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        WheelLabels(top = wheelSlip.fr, bottom = wheelSlip.rr, alignEnd = false)
    }
}

@Composable
private fun WheelLabels(top: Float, bottom: Float, alignEnd: Boolean) {
    Column(
        Modifier.height(150.dp).padding(top = 30.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text("%.1f%%".format(top), style = ReadoutSmall, color = if (top >= SLIP_CAUTION) slipColor(top) else IngweTextSecondary)
        Text("%.1f%%".format(bottom), style = ReadoutSmall, color = if (bottom >= SLIP_CAUTION) slipColor(bottom) else IngweTextSecondary)
    }
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Drawings
 * ──────────────────────────────────────────────────────────────────────────── */

/** Steering wheel turned by the actual wheel angle, with a top-centre marker. */
private fun DrawScope.drawSteeringWheel(angleDeg: Float) {
    val r = size.minDimension / 2f * 0.86f
    val c = center
    rotate(angleDeg, c) {
        drawCircle(IngweTextSecondary, r, c, style = Stroke(r * 0.13f))
        drawLine(IngweTextSecondary, Offset(c.x - r, c.y), Offset(c.x + r, c.y), r * 0.11f)
        drawLine(IngweTextSecondary, c, Offset(c.x, c.y + r), r * 0.11f)
        drawCircle(IngweSurfaceHigh, r * 0.30f, c)
        drawCircle(IngweTextSecondary, r * 0.30f, c, style = Stroke(r * 0.07f))
        drawLine(IngweTeal, Offset(c.x, c.y - r - r * 0.09f), Offset(c.x, c.y - r + r * 0.09f), r * 0.16f, StrokeCap.Round)
    }
}

private const val CLINO_LIMIT = 45f

/** Scale, zones, live arc, pointer, level line and tilting truck for a [Clinometer]. */
private fun DrawScope.drawClinometer(
    kind: ClinometerKind,
    deg: Float,
    caution: Float,
    alert: Float,
    labels: Map<Int, TextLayoutResult>,
) {
    val cx = size.width / 2f
    val base = size.height * 0.86f
    val r = min(size.width / 2f - 14.dp.toPx(), base - 14.dp.toPx())
    fun ang(d: Float) = 270f + (d.coerceIn(-CLINO_LIMIT, CLINO_LIMIT) / CLINO_LIMIT) * 90f
    val color = angleColor(deg, caution, alert)
    val topLeft = Offset(cx - r, base - r)
    val arcSize = Size(r * 2, r * 2)
    fun arc(from: Float, to: Float, c: Color, width: Float, cap: StrokeCap = StrokeCap.Butt) {
        if (to <= from) return
        drawArc(c, ang(from), ang(to) - ang(from), false, topLeft, arcSize, style = Stroke(width, cap = cap))
    }
    fun polar(d: Float, radius: Float): Offset {
        val a = Math.toRadians(ang(d).toDouble())
        return Offset(cx + radius * cos(a).toFloat(), base + radius * sin(a).toFloat())
    }

    // Scale and zones (mirrored both sides)
    val track = 3.dp.toPx()
    arc(-CLINO_LIMIT, CLINO_LIMIT, IngweHairline, track)
    arc(-alert, -caution, IngweCaution.copy(alpha = 0.5f), track)
    arc(caution, alert, IngweCaution.copy(alpha = 0.5f), track)
    arc(-CLINO_LIMIT, -alert, IngweAlert.copy(alpha = 0.8f), track)
    arc(alert, CLINO_LIMIT, IngweAlert.copy(alpha = 0.8f), track)

    // Ticks every 5°, labels every 15°
    for (d in -45..45 step 5) {
        val major = d % 15 == 0
        val tickColor = when {
            abs(d) >= alert -> IngweAlert
            major -> IngweTextSecondary
            else -> IngweTextTertiary
        }
        drawLine(
            tickColor,
            polar(d.toFloat(), r - (if (major) 12 else 7).dp.toPx()),
            polar(d.toFloat(), r - 3.dp.toPx()),
            (if (major) 1.6f else 1f).dp.toPx(),
        )
        labels[d]?.let { layout ->
            val p = polar(d.toFloat(), r - 24.dp.toPx())
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }
    }

    // Live arc from level to the angle, plus a pointer outside the scale
    val live = deg.coerceIn(-CLINO_LIMIT, CLINO_LIMIT)
    arc(minOf(0f, live), maxOf(0f, live), color, 5.dp.toPx(), StrokeCap.Round)
    val tip = polar(live, r + 9.dp.toPx())
    rotate(ang(live) + 90f, tip) {
        val pointer = Path().apply {
            moveTo(tip.x, tip.y + 7.dp.toPx())
            lineTo(tip.x - 6.dp.toPx(), tip.y - 4.dp.toPx())
            lineTo(tip.x + 6.dp.toPx(), tip.y - 4.dp.toPx())
            close()
        }
        drawPath(pointer, color)
    }

    drawLevelLine(base)
    when (kind) {
        ClinometerKind.ROLL -> drawTruckRear(cx, base, r * 0.68f, deg, color)
        ClinometerKind.PITCH -> drawTruckSide(cx, base, r * 1.12f, deg, color)
    }
}

/** Dashed level reference across the tile at [groundY]. */
private fun DrawScope.drawLevelLine(groundY: Float) {
    drawLine(
        IngweTextTertiary,
        Offset(0f, groundY),
        Offset(size.width, groundY),
        1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
    )
}

/**
 * Side view of a double-cab pickup, front to the right. Positive pitch = nose up.
 * Shape is in normalised units: x 0…1 along the truck, y 0…~0.37 up from the ground.
 */
private fun DrawScope.drawTruckSide(cx: Float, groundY: Float, len: Float, pitchDeg: Float, color: Color) {
    val left = cx - len / 2f
    val pivot = Offset(cx, groundY)
    fun p(x: Float, y: Float) = Offset(left + x * len, groundY - y * len)
    val stroke = 1.5.dp.toPx()

    val wheelR = 0.085f * len
    val archR = 0.11f * len
    val rearWheel = p(0.22f, 0.09f)
    val frontWheel = p(0.79f, 0.09f)

    val body = Path().apply {
        moveTo(p(0.03f, 0.10f))
        lineTo(p(0.02f, 0.24f))   // tailgate
        lineTo(p(0.40f, 0.245f))  // bed rail
        lineTo(p(0.43f, 0.36f))   // rear of cab
        lineTo(p(0.66f, 0.37f))   // roof
        lineTo(p(0.77f, 0.255f))  // windscreen
        lineTo(p(0.95f, 0.225f))  // bonnet
        lineTo(p(0.99f, 0.17f))   // grille
        lineTo(p(0.98f, 0.10f))
        lineTo(p(0.79f + 0.11f, 0.10f))
        arcTo(Rect(frontWheel.copy(y = p(0f, 0.10f).y), archR), 0f, -180f, false) // front arch
        lineTo(p(0.22f + 0.11f, 0.10f))
        arcTo(Rect(rearWheel.copy(y = p(0f, 0.10f).y), archR), 0f, -180f, false)  // rear arch
        close()
    }
    val glass = Path().apply {
        moveTo(p(0.455f, 0.34f))
        lineTo(p(0.645f, 0.35f))
        lineTo(p(0.735f, 0.26f))
        lineTo(p(0.455f, 0.26f))
        close()
    }

    rotate(-pitchDeg, pivot) {
        drawPath(body, color.copy(alpha = 0.16f))
        drawPath(body, color, style = Stroke(stroke, join = StrokeJoin.Round))
        drawPath(glass, IngweBackground.copy(alpha = 0.7f))
        drawLine(color.copy(alpha = 0.5f), p(0.55f, 0.35f), p(0.55f, 0.12f), stroke * 0.7f) // B-pillar / door line
        listOf(rearWheel, frontWheel).forEach { c ->
            drawCircle(IngweSurfaceHigh, wheelR, c)
            drawCircle(color, wheelR, c, style = Stroke(stroke))
            drawCircle(color.copy(alpha = 0.5f), wheelR * 0.45f, c, style = Stroke(stroke * 0.8f))
        }
    }
}

/**
 * Rear view of the pickup. Positive roll = right side down.
 * Normalised units: x −0.5…0.5 across, y 0…0.70 up from the ground.
 */
private fun DrawScope.drawTruckRear(cx: Float, groundY: Float, w: Float, rollDeg: Float, color: Color) {
    val pivot = Offset(cx, groundY)
    fun p(x: Float, y: Float) = Offset(pivot.x + x * w, groundY - y * w)
    fun rect(x0: Float, y0: Float, x1: Float, y1: Float) = Rect(p(x0, y1), p(x1, y0))
    val stroke = 1.5.dp.toPx()

    val body = Path().apply {
        moveTo(p(-0.46f, 0.15f))
        lineTo(p(-0.47f, 0.42f))  // bed side
        lineTo(p(-0.36f, 0.43f))
        lineTo(p(-0.32f, 0.70f))  // cab side
        lineTo(p(0.32f, 0.70f))   // roof
        lineTo(p(0.36f, 0.43f))
        lineTo(p(0.47f, 0.42f))
        lineTo(p(0.46f, 0.15f))
        close()
    }

    rotate(rollDeg, pivot) {
        // Tyres sit behind the body
        listOf(rect(-0.49f, 0f, -0.32f, 0.25f), rect(0.32f, 0f, 0.49f, 0.25f)).forEach { r ->
            drawRoundRect(IngweSurfaceHigh, r.topLeft, r.size, CornerRadius(3.dp.toPx()))
            drawRoundRect(color, r.topLeft, r.size, CornerRadius(3.dp.toPx()), style = Stroke(stroke))
        }
        drawPath(body, color.copy(alpha = 0.16f))
        drawPath(body, color, style = Stroke(stroke, join = StrokeJoin.Round))
        // Rear window, tailgate line, bumper, tail lights, mirrors
        rect(-0.24f, 0.50f, 0.24f, 0.64f).let { drawRoundRect(IngweBackground.copy(alpha = 0.7f), it.topLeft, it.size, CornerRadius(2.dp.toPx())) }
        drawLine(color.copy(alpha = 0.5f), p(-0.40f, 0.36f), p(0.40f, 0.36f), stroke * 0.7f)
        rect(-0.44f, 0.10f, 0.44f, 0.16f).let { drawRoundRect(color.copy(alpha = 0.5f), it.topLeft, it.size, CornerRadius(2.dp.toPx())) }
        listOf(rect(-0.45f, 0.25f, -0.39f, 0.39f), rect(0.39f, 0.25f, 0.45f, 0.39f)).forEach { r ->
            drawRoundRect(IngweAlert.copy(alpha = 0.85f), r.topLeft, r.size, CornerRadius(1.dp.toPx()))
        }
        listOf(rect(-0.44f, 0.50f, -0.34f, 0.56f), rect(0.34f, 0.50f, 0.44f, 0.56f)).forEach { r ->
            drawRoundRect(color, r.topLeft, r.size, CornerRadius(1.dp.toPx()))
        }
    }
}

private fun Path.moveTo(o: Offset) = moveTo(o.x, o.y)
private fun Path.lineTo(o: Offset) = lineTo(o.x, o.y)
