package io.ingwe.dash.ui.pages

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ingwe.dash.data.vehicle.Economy
import io.ingwe.dash.data.vehicle.VehicleState
import io.ingwe.dash.ui.components.gauges.NeedleSpring
import io.ingwe.dash.ui.components.gauges.Panel
import io.ingwe.dash.ui.components.gauges.fmt
import io.ingwe.dash.ui.components.gauges.sweepTowards
import io.ingwe.dash.ui.components.gauges.LocalIgnitionSweep
import io.ingwe.dash.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TANK_LITRES = 80f

private fun ecoColor(v: Float) = when {
    v >= 20f -> IngweAlert
    v >= 12f -> IngweCaution
    else -> IngweTeal
}

@Composable
fun EconomyPage(car: VehicleState, economy: Economy, onResetTrip: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f), title = "Consumption now", trailing = { SourceTag("OBD-II") }) {
                ConsumptionArc(car, economy.averageL100)
            }
            Panel(
                Modifier.weight(1.65f),
                title = "Last 30 seconds · L/100 km",
                trailing = { Text("trip avg ${economy.averageL100.fmt("%.1f")}", style = PageSmall, color = IngweTextSecondary) },
            ) {
                Box {
                    Column { ConsumptionHistory(economy.history, economy.averageL100) }
                    if (economy.history.size < 4) {
                        Text("Collecting · fills over 30 s of driving", style = PageLabel, color = IngweTextTertiary, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f), title = "Fuel", trailing = { SourceTag("OBD-II") }) {
                FuelTank(car.fuelLevelPct)
                Spacer(Modifier.height(10.dp))
                val litres = car.fuelLevelPct / 100f * TANK_LITRES
                val range = if (economy.averageL100.isNaN()) Float.NaN else litres / economy.averageL100 * 100f
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.Top) {
                    PageStat("Range", range.fmt(), "km", big = true)
                    PageStat("In tank", litres.fmt(), "L")
                }
            }
            Panel(
                Modifier.weight(1.65f),
                title = "This trip",
                trailing = {
                    Text(
                        "Reset",
                        style = PageSmall,
                        color = IngweTextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onResetTrip)
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                },
            ) {
                val e = economy
                Row(Modifier.fillMaxWidth()) {
                    PageStat("Distance", e.distanceKm.fmt("%.1f"), "km", Modifier.weight(1f))
                    PageStat("Time", formatDuration(e.seconds), null, Modifier.weight(1f))
                    PageStat("Average", e.averageL100.fmt("%.1f"), "L/100 km", Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    PageStat("Avg speed", e.averageKmH.fmt(), "km/h", Modifier.weight(1f))
                    PageStat("Fuel used", e.fuelUsedL.fmt("%.1f"), "L", Modifier.weight(1f))
                    PageStat("Idle time", formatDuration(e.idleSeconds), null, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun PageStat(label: String, value: String, unit: String?, modifier: Modifier = Modifier, big: Boolean = false) {
    Column(modifier) {
        Text(label, style = PageLabel, color = IngweTextSecondary)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = if (big) ReadoutLarge.copy(fontSize = 38.sp) else PageValue, color = IngweTextPrimary)
            if (unit != null) {
                Spacer(Modifier.width(5.dp))
                Text(unit, style = PageLabel, color = IngweTextTertiary, modifier = Modifier.padding(bottom = if (big) 7.dp else 4.dp))
            }
        }
    }
}

private fun formatDuration(seconds: Float): String {
    val s = seconds.toInt()
    return "%d:%02d".format(s / 3600, (s / 60) % 60)
}

/** Half ring: instant L/100 km (L/h at idle) with the trip average as a white bug. */
@Composable
private fun ConsumptionArc(car: VehicleState, tripAverage: Float) {
    val moving = !car.litresPer100.isNaN()
    val max = if (moving) 30f else 5f
    val raw = if (moving) car.litresPer100 else car.fuelRateLh
    val animated = animateFloatAsState(if (raw.isNaN()) 0f else raw.coerceIn(0f, max), NeedleSpring, label = "eco")
    val sweep = LocalIgnitionSweep.current
    val measurer = rememberTextMeasurer()

    Box(Modifier.fillMaxWidth().height(200.dp)) {
        androidx.compose.foundation.layout.Spacer(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val step = if (moving) 5 else 1
                    val labels = (0..max.toInt() step step).map { it to measurer.label(it.toString(), IngweTextTertiary, 12f) }
                    val stroke = 10.dp.toPx()
                    val cx = size.width / 2f
                    val cy = size.height * 0.82f
                    val r = minOf(size.width / 2f - 20.dp.toPx(), size.height * 0.74f)
                    val topLeft = Offset(cx - r, cy - r)
                    val arcSize = Size(r * 2, r * 2)
                    fun angle(v: Float) = 180f + (v.coerceIn(0f, max) / max) * 180f
                    onDrawBehind {
                        fun arc(from: Float, to: Float, c: Color, cap: StrokeCap = StrokeCap.Butt) {
                            if (to > from) drawArc(c, angle(from), angle(to) - angle(from), false, topLeft, arcSize, style = Stroke(stroke, cap = cap))
                        }
                        arc(0f, max, IngweHairline)
                        if (moving) {
                            arc(12f, 20f, IngweCaution.copy(alpha = 0.35f))
                            arc(20f, max, IngweAlert.copy(alpha = 0.35f))
                        }
                        val v = sweepTowards(animated.value, max, sweep.value)
                        arc(0f, v, if (moving) ecoColor(v) else IngweCool, StrokeCap.Round)
                        labels.forEach { (value, layout) ->
                            val a = Math.toRadians(angle(value.toFloat()).toDouble())
                            drawLabel(layout, Offset(cx + (r - 26.dp.toPx()) * cos(a).toFloat(), cy + (r - 26.dp.toPx()) * sin(a).toFloat()))
                        }
                        if (moving && !tripAverage.isNaN()) {
                            val a = Math.toRadians(angle(tripAverage).toDouble())
                            val c = cos(a).toFloat()
                            val s = sin(a).toFloat()
                            val o = 9.dp.toPx()
                            drawLine(IngweTextPrimary, Offset(cx + (r - o) * c, cy + (r - o) * s), Offset(cx + (r + o) * c, cy + (r + o) * s), 3.dp.toPx(), StrokeCap.Round)
                        }
                    }
                },
        )
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val text by remember(animated) { androidx.compose.runtime.derivedStateOf { "%.1f".format(animated.value) } }
            Text(if (raw.isNaN()) "–" else text, style = ReadoutLarge, color = IngweTextPrimary)
            Text(if (moving) "L/100 km" else "L/h · idling", style = PageLabel, color = IngweTextSecondary)
        }
    }
}

/** Rolling bars with numbered gridlines and a dashed trip-average line. Idle samples show as blue dots. */
@Composable
private fun ConsumptionHistory(history: List<Float>, tripAverage: Float) {
    val measurer = rememberTextMeasurer()
    androidx.compose.foundation.layout.Spacer(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .drawWithCache {
                val grid = listOf(0, 10, 20, 30).map { it to measurer.label(it.toString(), IngweTextTertiary, 12f) }
                val left = 34.dp.toPx()
                val top = 8.dp.toPx()
                val bottom = size.height - 6.dp.toPx()
                val max = 30f
                fun y(v: Float) = bottom - (v.coerceIn(0f, max) / max) * (bottom - top)
                val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                onDrawBehind {
                    grid.forEach { (g, layout) ->
                        drawLine(IngweHairline, Offset(left, y(g.toFloat())), Offset(size.width, y(g.toFloat())), 1f)
                        drawLabel(layout, Offset(left - 8.dp.toPx(), y(g.toFloat())), ax = 1f)
                    }
                    val n = 60
                    val bw = (size.width - left) / n
                    history.forEachIndexed { i, v ->
                        val x = left + (n - history.size + i) * bw
                        if (v.isNaN()) {
                            drawCircle(IngweCool.copy(alpha = 0.7f), 2.dp.toPx(), Offset(x + bw / 2f, bottom - 3.dp.toPx()))
                        } else {
                            val alpha = if (i == history.lastIndex) 1f else 0.7f
                            drawRoundRect(
                                ecoColor(v).copy(alpha = alpha),
                                topLeft = Offset(x + 1f, y(v)),
                                size = Size((bw - 2f).coerceAtLeast(1f), bottom - y(v)),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )
                        }
                    }
                    if (!tripAverage.isNaN()) {
                        drawLine(IngweTextPrimary.copy(alpha = 0.6f), Offset(left, y(tripAverage)), Offset(size.width, y(tripAverage)), 1.2.dp.toPx(), pathEffect = dash)
                    }
                }
            },
    )
    Row(Modifier.fillMaxWidth().padding(start = 34.dp)) {
        Text("30 s ago", style = PageSmall, color = IngweTextTertiary, modifier = Modifier.weight(1f))
        Text("now", style = PageSmall, color = IngweTextTertiary)
    }
}

/** Capsule tank with a gently sloshing fuel surface and quarter marks. */
@Composable
private fun FuelTank(levelPct: Float) {
    val sweep = LocalIgnitionSweep.current
    val level = animateFloatAsState(if (levelPct.isNaN()) 0f else levelPct / 100f, tween(600), label = "tank")
    val phase by rememberInfiniteTransition(label = "slosh").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Box(Modifier.fillMaxWidth().height(50.dp)) {
        androidx.compose.foundation.layout.Spacer(
            Modifier.fillMaxSize().drawBehind {
                val r = size.height / 2f
                val capsule = Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
                }
                val l = sweepTowards(level.value, 1f, sweep.value)
                clipPath(capsule) {
                    drawRect(IngweSurfaceHigh)
                    val fillW = size.width * l
                    val surface = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(fillW, 0f)
                        var yy = 0f
                        while (yy <= size.height) {
                            lineTo(fillW + sin(phase + yy / 7.dp.toPx()) * 3.dp.toPx(), yy)
                            yy += 2.dp.toPx()
                        }
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(surface, (if (l < 0.15f) IngweAlert else IngweTeal).copy(alpha = 0.85f))
                }
                drawPath(capsule, IngweHairline, style = Stroke(1.5.dp.toPx()))
            },
        )
        Text(
            levelPct.fmt() + " %",
            style = PageValueSmall.copy(fontSize = 17.sp),
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
        )
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("E", "¼", "½", "¾", "F").forEach { Text(it, style = PageSmall, color = IngweTextTertiary) }
    }
}

