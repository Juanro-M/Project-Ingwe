package io.ingwe.dash.ui.components.gauges

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlin.math.min
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
 * 4Motion off-road panel.
 *
 * Top: three angle tiles that *show* the angle — a steering wheel that turns,
 * a side-view truck that pitches and a rear-view truck that rolls, each against
 * a dashed level line.
 * Bottom: top-down drivetrain with each wheel coloured by slip, centre / rear
 * differential state and the current front:rear torque split.
 */
@Composable
fun OffRoadPanel(
    steeringAngleDeg: Float,
    pitchDeg: Float,
    rollDeg: Float,
    wheelSlip: WheelSlip,
    rearDiff: DiffState,
    rearTorquePct: Float,
    modifier: Modifier = Modifier,
) {
    val steer by animateFloatAsState(steeringAngleDeg, tween(220), label = "steer")
    val pitch by animateFloatAsState(pitchDeg, tween(220), label = "pitch")
    val roll by animateFloatAsState(rollDeg, tween(220), label = "roll")

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
        // ── Angles ───────────────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val s = steeringAngleDeg.roundToInt()
            AngleTile(
                label = "Steering",
                value = "${abs(s)}°",
                caption = when { s > 0 -> "right"; s < 0 -> "left"; else -> "centre" },
                color = IngweTeal,
                modifier = Modifier.weight(1f),
            ) { drawSteeringWheel(steer) }

            val p = pitchDeg.roundToInt()
            val pitchColor = angleColor(pitchDeg, caution = 25f, alert = 35f)
            AngleTile(
                label = "Pitch",
                value = "${abs(p)}°",
                caption = when { p > 0 -> "nose up"; p < 0 -> "nose down"; else -> "level" },
                color = pitchColor,
                modifier = Modifier.weight(1f),
            ) { drawPitchProfile(pitch, pitchColor) }

            val r = rollDeg.roundToInt()
            val rollColor = angleColor(rollDeg, caution = 20f, alert = 30f)
            AngleTile(
                label = "Roll",
                value = "${abs(r)}°",
                caption = when { r > 0 -> "right side down"; r < 0 -> "left side down"; else -> "level" },
                color = rollColor,
                modifier = Modifier.weight(1f),
            ) { drawRollProfile(roll, rollColor) }
        }

        Spacer(Modifier.height(14.dp))

        // ── Drivetrain ───────────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DrivetrainDiagram(wheelSlip = wheelSlip, rearDiff = rearDiff)
            Spacer(Modifier.width(24.dp))
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
private fun DrawScope.drawPitchProfile(pitchDeg: Float, color: Color) {
    val groundY = size.height * 0.88f
    val len = min(size.width * 0.86f, size.height * 2.0f)
    val left = size.width / 2f - len / 2f
    val pivot = Offset(size.width / 2f, groundY)
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

    drawLevelLine(groundY)
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
private fun DrawScope.drawRollProfile(rollDeg: Float, color: Color) {
    val groundY = size.height * 0.88f
    val w = min(size.width * 0.62f, size.height * 1.12f)
    val pivot = Offset(size.width / 2f, groundY)
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

    drawLevelLine(groundY)
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
