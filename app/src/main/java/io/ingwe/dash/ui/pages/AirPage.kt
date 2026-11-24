package io.ingwe.dash.ui.pages

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ingwe.dash.data.vehicle.VehicleState
import io.ingwe.dash.ui.components.gauges.BoostUnit
import io.ingwe.dash.ui.components.gauges.LocalIgnitionSweep
import io.ingwe.dash.ui.components.gauges.NeedleSpring
import io.ingwe.dash.ui.components.gauges.Panel
import io.ingwe.dash.ui.components.gauges.boostFromKPa
import io.ingwe.dash.ui.components.gauges.fmt
import io.ingwe.dash.ui.components.gauges.formatBoost
import io.ingwe.dash.ui.components.gauges.sweepTowards
import io.ingwe.dash.ui.theme.*
import kotlin.math.max

private val STATIONS = floatArrayOf(0f, 0.19f, 0.4f, 0.6f, 0.8f, 1f)

private fun tempColor(c: Float) = when {
    c.isNaN() -> IngweTextTertiary
    c >= 90f -> IngweAlert
    c >= 55f -> IngweCaution
    c >= 35f -> IngweTeal
    else -> IngweCool
}

@Composable
fun AirPage(car: VehicleState, unit: BoostUnit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(Modifier.fillMaxWidth(), title = "Air path", trailing = { SourceTag("OBD-II · compressor-out temperature estimated") }) {
            AirPathDiagram(car, unit)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f), title = "Altitude", trailing = { SourceTag("OBD-II · barometer") }) {
                AltitudeChart(car.altitudeM)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("Barometer  ", style = PageLabel, color = IngweTextSecondary)
                    Text("${car.baroKPa.fmt("%.1f")} kPa", style = PageLabel, color = IngweTextPrimary, modifier = Modifier.weight(1f))
                    Text("Air  ", style = PageLabel, color = IngweTextSecondary)
                    Text("${car.airDensityPct.fmt()} % of sea level", style = PageLabel, color = IngweTextPrimary)
                }
            }
            Panel(Modifier.weight(1f), title = "Outside", trailing = { SourceTag("OBD-II") }) {
                OutsideCard(car.ambientC, car.intakeC)
            }
            Panel(Modifier.weight(1.2f), title = "Airflow health", trailing = { SourceTag("expected MAF: VW UDS") }) {
                HealthCard(car, unit)
            }
        }
    }
}

/**
 * Ambient → MAF → turbo → intercooler → manifold → engine.
 * Particles move at the measured airflow; their colour follows the air temperature
 * along the pipe. The turbo spins faster with boost. Motion is integrated per frame
 * and read only in the draw phase, so the page never recomposes to animate.
 */
@Composable
private fun AirPathDiagram(car: VehicleState, unit: BoostUnit) {
    val maf = rememberUpdatedState(if (car.mafGs.isNaN()) 0f else car.mafGs)
    val boost = rememberUpdatedState(boostFromKPa(car.boostKPa, BoostUnit.BAR, car.baroKPa))
    val temps = rememberUpdatedState(
        floatArrayOf(car.ambientC, car.ambientC + 1f, car.compressorOutC, car.chargeAirC, car.intakeC, car.intakeC),
    )
    val flow = remember { mutableFloatStateOf(0f) }
    val spin = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
                    flow.floatValue = (flow.floatValue + dt * (0.04f + maf.value / 900f)) % 1f
                    spin.floatValue = (spin.floatValue + dt * (2f + max(0f, boost.value.takeIf { !it.isNaN() } ?: 0f) * 22f) * 57.3f) % 360f
                }
                last = now
            }
        }
    }

    Spacer(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .drawBehind {
                val y = size.height / 2f
                val x0 = 34.dp.toPx()
                val x1 = size.width - 40.dp.toPx()
                val xs = FloatArray(6) { x0 + (x1 - x0) * STATIONS[it] }
                val t = temps.value
                fun tempAt(x: Float): Float {
                    for (i in 0 until 5) if (x <= xs[i + 1]) {
                        val f = (x - xs[i]) / (xs[i + 1] - xs[i])
                        val a = if (t[i].isNaN()) 30f else t[i]
                        val b = if (t[i + 1].isNaN()) 30f else t[i + 1]
                        return a + (b - a) * f
                    }
                    return if (t[5].isNaN()) 30f else t[5]
                }
                // Pipe tinted by temperature
                drawLine(
                    Brush.horizontalGradient(*xs.indices.map { i -> (xs[i] - x0) / (x1 - x0) to tempColor(t[i]).copy(alpha = 0.22f) }.toTypedArray(), startX = x0, endX = x1),
                    Offset(x0, y), Offset(x1, y), 26.dp.toPx(), StrokeCap.Round,
                )
                // Particles (70, deterministic lanes)
                for (i in 0 until 70) {
                    val p = (i / 70f + flow.floatValue) % 1f
                    val x = x0 + (x1 - x0) * p
                    val lane = ((i * 37) % 7) / 7f - 0.5f
                    drawCircle(tempColor(tempAt(x)).copy(alpha = 0.9f), 2.2.dp.toPx(), Offset(x, y + lane * 16.dp.toPx()))
                }
                // Stations
                stationPlate(xs[0], y, 22.dp.toPx())
                translate(xs[0], y) {
                    for (k in -1..1) {
                        val p = Path().apply {
                            moveTo(-11.dp.toPx(), k * 7.dp.toPx())
                            cubicTo(-4.dp.toPx(), k * 7.dp.toPx() - 5.dp.toPx(), 2.dp.toPx(), k * 7.dp.toPx() + 5.dp.toPx(), 11.dp.toPx(), k * 7.dp.toPx())
                        }
                        drawPath(p, IngweCool, style = Stroke(2.dp.toPx()))
                    }
                }
                stationPlate(xs[1], y, 20.dp.toPx())
                translate(xs[1], y) {
                    val s = 9.dp.toPx()
                    drawRect(IngweTextSecondary, Offset(-s, -s), Size(s * 2, s * 2), style = Stroke(2.dp.toPx()))
                    drawLine(IngweTextSecondary, Offset(-s, 0f), Offset(s, 0f), 2.dp.toPx())
                }
                stationPlate(xs[2], y, 30.dp.toPx())
                rotate(spin.floatValue, Offset(xs[2], y)) {
                    translate(xs[2], y) {
                        val c = tempColor(t[2])
                        for (k in 0 until 9) {
                            rotate(k * 40f, Offset.Zero) {
                                val blade = Path().apply {
                                    moveTo(5.dp.toPx(), 0f)
                                    quadraticTo(14.dp.toPx(), 4.dp.toPx(), 22.dp.toPx(), -4.dp.toPx())
                                }
                                drawPath(blade, c, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
                            }
                        }
                        drawCircle(IngweSurfaceHigh, 5.dp.toPx(), Offset.Zero)
                    }
                }
                translate(xs[3], y) {
                    val w = 26.dp.toPx()
                    val h = 24.dp.toPx()
                    drawRoundRect(IngweSurface, Offset(-w, -h), Size(w * 2, h * 2), CornerRadius(6.dp.toPx()))
                    drawRoundRect(IngweHairline, Offset(-w, -h), Size(w * 2, h * 2), CornerRadius(6.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    var k = -20f
                    while (k <= 20f) {
                        drawLine(IngweTeal.copy(alpha = 0.8f), Offset(k.dp.toPx(), -18.dp.toPx()), Offset(k.dp.toPx(), 18.dp.toPx()), 1.5.dp.toPx())
                        k += 5f
                    }
                }
                stationPlate(xs[4], y, 20.dp.toPx())
                translate(xs[4], y) {
                    for (dy in listOf(-8f, 0f, 8f)) drawLine(IngweTextSecondary, Offset(-8.dp.toPx(), 0f), Offset(9.dp.toPx(), dy.dp.toPx()), 2.dp.toPx())
                }
                translate(xs[5], y) {
                    val w = 18.dp.toPx()
                    val h = 34.dp.toPx()
                    for (x in listOf(-20.dp.toPx(), 2.dp.toPx())) {
                        drawRoundRect(IngweSurface, Offset(x, -20.dp.toPx()), Size(w, h), CornerRadius(4.dp.toPx()))
                        drawRoundRect(IngweTextSecondary, Offset(x, -20.dp.toPx()), Size(w, h), CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx()))
                    }
                }
            },
    )

    // Readings under each station
    val boostText = formatBoost(boostFromKPa(car.boostKPa, unit, car.baroKPa), unit)
    StationLayout(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Station("Outside", "${car.ambientC.fmt()} °C", tempColor(car.ambientC), "${car.baroKPa.fmt("%.1f")} kPa", IngweTextTertiary)
        Station("Mass airflow", "${car.mafGs.fmt()} g/s", IngweTextPrimary)
        Station("Turbo", if (car.boostKPa.isNaN()) "–" else "$boostText ${unit.label}", IngweAccent, "~${car.compressorOutC.fmt()} °C out", tempColor(car.compressorOutC))
        val drop = car.compressorOutC - car.chargeAirC
        Station("Intercooler", "${car.chargeAirC.fmt()} °C out", tempColor(car.chargeAirC), if (drop.isNaN()) null else "−${drop.fmt()} °C", IngweTextTertiary)
        Station("Manifold", "${car.boostKPa.fmt()} kPa", IngweTextPrimary, "${car.intakeC.fmt()} °C", tempColor(car.intakeC))
        Station("Engine", if (car.rpm.isNaN()) "–" else "%,d rpm".format((car.rpm / 10f).toInt() * 10), IngweTextPrimary)
    }
}

private fun DrawScope.stationPlate(x: Float, y: Float, r: Float) {
    drawCircle(IngweSurface, r, Offset(x, y))
    drawCircle(IngweHairline, r, Offset(x, y), style = Stroke(1.5.dp.toPx()))
}

@Composable
private fun Station(name: String, value: String, valueColor: Color, sub: String? = null, subColor: Color = IngweTextTertiary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = PageLabel, color = IngweTextSecondary)
        Text(value, style = PageValueSmall, color = valueColor)
        if (sub != null) Text(sub, style = PageSmall, color = subColor)
    }
}

/** Places child i centred under station fraction i (clamped so the ends stay on screen). */
@Composable
private fun StationLayout(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val x0 = 34.dp.roundToPx()
        val x1 = constraints.maxWidth - 40.dp.roundToPx()
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { i, p ->
                val cx = x0 + ((x1 - x0) * STATIONS[i]).toInt()
                p.place((cx - p.width / 2).coerceIn(0, constraints.maxWidth - p.width), 0)
            }
        }
    }
}

/** Mountain profile with the truck's height against a metre scale. */
@Composable
private fun AltitudeChart(altitudeM: Float) {
    val sweep = LocalIgnitionSweep.current
    val alt = animateFloatAsState(if (altitudeM.isNaN()) 0f else altitudeM, NeedleSpring, label = "alt")
    val measurer = rememberTextMeasurer()
    Box(Modifier.fillMaxWidth().height(124.dp)) {
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val maxM = 3500f
                val left = 36.dp.toPx()
                val top = 8.dp.toPx()
                val bottom = size.height - 6.dp.toPx()
                fun y(m: Float) = bottom - (m / maxM) * (bottom - top)
                val axis = listOf(0, 1000, 2000, 3000).map { it to measurer.label(if (it == 0) "0" else "${it / 1000}k", IngweTextTertiary, 12f) }
                val ridgePts = listOf(0f to 300f, 0.12f to 900f, 0.24f to 650f, 0.38f to 1500f, 0.5f to 1250f, 0.62f to 2300f, 0.74f to 1800f, 0.86f to 2900f, 1f to 2100f)
                val ridge = Path().apply {
                    moveTo(left, bottom)
                    ridgePts.forEach { (f, m) -> lineTo(left + (size.width - left) * f, y(m)) }
                    lineTo(size.width, bottom)
                    close()
                }
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                onDrawBehind {
                    axis.forEach { (m, l) -> drawLabel(l, Offset(left - 8.dp.toPx(), y(m.toFloat())), ax = 1f) }
                    drawPath(ridge, IngweTeal.copy(alpha = 0.12f))
                    drawPath(ridge, IngweTeal.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx()))
                    if (!altitudeM.isNaN()) {
                        val a = sweepTowards(alt.value, maxM, sweep.value)
                        drawLine(IngweAccent, Offset(left, y(a)), Offset(size.width, y(a)), 1.2.dp.toPx(), pathEffect = dash)
                        drawCircle(IngweAccent, 5.dp.toPx(), Offset(left + (size.width - left) * 0.44f, y(a)))
                    }
                }
            },
        )
        Text(
            if (altitudeM.isNaN()) "–" else "%,d m".format(alt.value.toInt()),
            style = ReadoutMedium.copy(fontSize = 24.sp),
            color = IngweTextPrimary,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

/** Ambient temperature, and how much hotter the engine's intake air is than outside. */
@Composable
private fun OutsideCard(ambientC: Float, intakeC: Float) {
    val soak = intakeC - ambientC
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(if (ambientC.isNaN()) "–" else "${ambientC.fmt()}°", style = ReadoutLarge.copy(fontSize = 54.sp), color = IngweTextPrimary)
            Text("ambient", style = PageLabel, color = IngweTextSecondary)
        }
        if (!soak.isNaN()) {
            Text("+${soak.fmt()}° heat soak", style = PageLabel, color = if (soak > 25f) IngweCaution else IngweTextSecondary, modifier = Modifier.padding(bottom = 22.dp))
        }
    }
    Spacer(Modifier.height(10.dp))
    Spacer(
        Modifier.fillMaxWidth().height(22.dp).drawBehind {
            val stripY = 8.dp.toPx()
            val minT = -10f
            val maxT = 50f
            fun x(t: Float) = ((t.coerceIn(minT, maxT) - minT) / (maxT - minT)) * size.width
            drawRoundRect(
                Brush.horizontalGradient(0f to IngweCool, 0.45f to IngweTeal, 0.75f to IngweCaution, 1f to IngweAlert),
                topLeft = Offset(0f, stripY), size = Size(size.width, 6.dp.toPx()), cornerRadius = CornerRadius(3.dp.toPx()), alpha = 0.85f,
            )
            fun marker(t: Float, up: Boolean) {
                if (t.isNaN()) return
                val xx = x(t)
                val m = Path().apply {
                    if (up) { moveTo(xx, stripY - 1.dp.toPx()); lineTo(xx - 5.dp.toPx(), stripY - 8.dp.toPx()); lineTo(xx + 5.dp.toPx(), stripY - 8.dp.toPx()) }
                    else { val b = stripY + 7.dp.toPx(); moveTo(xx, b); lineTo(xx - 5.dp.toPx(), b + 7.dp.toPx()); lineTo(xx + 5.dp.toPx(), b + 7.dp.toPx()) }
                    close()
                }
                drawPath(m, IngweTextPrimary)
            }
            marker(ambientC, up = true)
            marker(intakeC, up = false)
        },
    )
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text("▲ outside ${ambientC.fmt()}°", style = PageSmall, color = IngweTextSecondary, modifier = Modifier.weight(1f))
        Text("▼ intake ${intakeC.fmt()}°", style = PageSmall, color = IngweTextSecondary)
    }
}

/** Score ring: actual airflow vs what the ECU expects, plus boost vs target. */
@Composable
private fun HealthCard(car: VehicleState, unit: BoostUnit) {
    val score = if (car.mafGs.isNaN() || car.mafExpectedGs.isNaN()) Float.NaN else (car.mafGs / car.mafExpectedGs * 100f).coerceIn(0f, 100f)
    val sweep = LocalIgnitionSweep.current
    val animated = animateFloatAsState(if (score.isNaN()) 0f else score, NeedleSpring, label = "health")
    val color = when {
        score.isNaN() -> IngweTextTertiary
        score >= 95f -> IngweTeal
        score >= 85f -> IngweCaution
        else -> IngweAlert
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            Spacer(
                Modifier.fillMaxSize().drawBehind {
                    val stroke = 9.dp.toPx()
                    val r = size.minDimension / 2f - stroke / 2f
                    val tl = Offset(size.width / 2f - r, size.height / 2f - r)
                    drawCircle(IngweHairline, r, style = Stroke(stroke))
                    val v = sweepTowards(animated.value, 100f, sweep.value)
                    drawArc(color, -90f, v / 100f * 360f, false, tl, Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
                },
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(score.fmt(), style = ReadoutMedium.copy(fontSize = 28.sp), color = IngweTextPrimary)
                Text(
                    when { score.isNaN() -> "no data"; score >= 95f -> "Good"; score >= 85f -> "Check"; else -> "Poor" },
                    style = PageSmall, color = color,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HealthLine("MAF vs expected", "${car.mafGs.fmt()} / ${car.mafExpectedGs.fmt()} g/s")
            val diff = boostFromKPa(car.boostKPa, unit, car.baroKPa) - boostFromKPa(car.targetBoostKPa, unit, car.baroKPa)
            HealthLine("Boost vs target", if (diff.isNaN()) "–" else "${formatBoost(diff, unit)} ${unit.label}")
            HealthLine("Charge air", "${car.chargeAirC.fmt()} °C")
        }
    }
}

@Composable
private fun HealthLine(label: String, value: String) {
    Column {
        Text(label, style = PageLabel, color = IngweTextSecondary)
        Text(value, style = PageValueSmall, color = IngweTextPrimary)
    }
}
