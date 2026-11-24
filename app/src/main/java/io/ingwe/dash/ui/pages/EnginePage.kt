package io.ingwe.dash.ui.pages

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.ingwe.dash.data.vehicle.GlowPhase
import io.ingwe.dash.data.vehicle.VehicleState
import io.ingwe.dash.ui.components.gauges.NeedleSpring
import io.ingwe.dash.ui.components.gauges.Panel
import io.ingwe.dash.ui.components.gauges.StatusDot
import io.ingwe.dash.ui.theme.*
import kotlin.math.abs

private fun injColor(v: Float) = when {
    abs(v) >= 2.5f -> IngweAlert
    abs(v) >= 2f -> IngweCaution
    else -> IngweTeal
}

@Composable
fun EnginePage(car: VehicleState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Panel(Modifier.weight(1.55f), title = "3.0 V6 TDI · cylinders", trailing = { SourceTag("VW UDS · unconfirmed") }) {
            val corrections = car.injectorCorrections
            if (corrections == null) {
                Unavailable("Injector corrections and glow-plug data need VW-specific identifiers. They'll appear here once mapped on the truck.")
            } else {
                // One spring per cylinder, so the rings and bars move like the gauges.
                val animated = List(6) { i -> animateFloatAsState(corrections.getOrElse(i) { 0f }, NeedleSpring, label = "inj$i") }
                V6Diagram(animated, car.glowOhms, car.glowPhase)
                Spacer(Modifier.height(8.dp))
                BalanceChart(animated)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(
                Modifier.fillMaxWidth(),
                title = "Glow system",
                trailing = {
                    when (car.glowPhase) {
                        GlowPhase.OFF -> StatusDot("Off · engine warm", IngweTextTertiary)
                        else -> StatusDot("${if (car.glowPhase == GlowPhase.PRE) "Pre-glow" else "After-glow"} · coolant ${car.coolantC.toInt()} °C", IngweAccent)
                    }
                },
            ) {
                GlowCard(car)
            }
            Panel(Modifier.fillMaxWidth(), title = "Common rail", trailing = { SourceTag("OBD-II") }) {
                RailReadout(car.railBar, car.railTargetBar)
            }
            Panel(Modifier.fillMaxWidth(), title = "EGR valve", trailing = { SourceTag("OBD-II") }) {
                EgrReadout(car.egrPct)
            }
        }
    }
}

@Composable
private fun Unavailable(text: String) {
    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        Text(text, style = PageLabel, color = IngweTextTertiary)
    }
}

/**
 * Top view of the V6, both banks staggered. Each cylinder: glow plug in the centre
 * (glowing while heating, red ✕ if open circuit) and a ring arc for the injector
 * correction — clockwise from 12 o'clock means the ECU is adding fuel.
 */
@Composable
private fun V6Diagram(correctionStates: List<State<Float>>, glowOhms: List<Float?>?, phase: GlowPhase) {
    // Read the springs only in draw (and in a derived label list), never in composition.
    val states = rememberUpdatedState(correctionStates)
    val measurer = rememberTextMeasurer()
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    // Re-measure the number labels only when the rounded values change, not every frame.
    val labelText by remember { derivedStateOf { states.value.map { "%+.1f".format(it.value) } } }
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(236.dp)
            .drawWithCache {
                val r = minOf(46.dp.toPx(), size.height * 0.19f, size.width / 11f)
                val gap = r * 2.55f
                val cx = size.width / 2f + 24.dp.toPx()
                val midY = size.height / 2f
                val centres = List(6) { i ->
                    val bank = if (i < 3) 0 else 1
                    val k = i % 3
                    Offset(cx + (k - 1) * gap + if (bank == 1) gap * 0.35f else -gap * 0.35f, if (bank == 1) midY + r + 14.dp.toPx() else midY - r - 14.dp.toPx())
                }
                val numbers = List(6) { measurer.label("${it + 1}", IngweTextSecondary, 14f, FontWeight.SemiBold) }
                val values = labelText.map { s -> measurer.label(s, injColor(s.toFloat()), 13f) }
                val bank1 = measurer.label("Bank 1", IngweTextTertiary, 13f)
                val bank2 = measurer.label("Bank 2", IngweTextTertiary, 13f)
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))
                onDrawBehind {
                    val blockLeft = cx - gap * 1.95f
                    drawRoundRect(IngweTextTertiary.copy(alpha = 0.06f), Offset(blockLeft, 14.dp.toPx()), Size(gap * 3.9f, size.height - 28.dp.toPx()), CornerRadius(22.dp.toPx()))
                    drawRoundRect(IngweHairline, Offset(blockLeft, 14.dp.toPx()), Size(gap * 3.9f, size.height - 28.dp.toPx()), CornerRadius(22.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    drawLine(IngweHairline, Offset(cx - gap * 1.7f, midY), Offset(cx + gap * 1.7f, midY), 1.dp.toPx(), pathEffect = dash)
                    drawLabel(bank1, Offset(blockLeft - 12.dp.toPx(), centres[0].y), ax = 1f)
                    drawLabel(bank2, Offset(blockLeft - 12.dp.toPx(), centres[3].y), ax = 1f)
                    val glowing = phase != GlowPhase.OFF
                    centres.forEachIndexed { i, c ->
                        val corr = states.value[i].value
                        val open = glowOhms != null && glowOhms.getOrNull(i) == null
                        drawCircle(IngweSurface, r, c)
                        drawCircle(IngweHairline, r, c, style = Stroke(6.dp.toPx()))
                        val sweepDeg = (corr.coerceIn(-3f, 3f) / 3f) * 150f
                        drawArc(
                            injColor(corr), -90f + minOf(0f, sweepDeg), abs(sweepDeg), false,
                            Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
                        )
                        if (glowing && !open) {
                            drawCircle(Brush.radialGradient(listOf(IngweAccent.copy(alpha = 0.95f * pulse), IngweAccent.copy(alpha = 0f)), c, r * 0.75f), r * 0.75f, c)
                        }
                        drawCircle(if (open) IngweAlert else if (glowing) IngweAccent else IngweTextTertiary, 6.dp.toPx(), c)
                        if (open) {
                            val s = 10.dp.toPx()
                            drawLine(IngweAlert, Offset(c.x - s, c.y - s), Offset(c.x + s, c.y + s), 2.dp.toPx())
                            drawLine(IngweAlert, Offset(c.x + s, c.y - s), Offset(c.x - s, c.y + s), 2.dp.toPx())
                        }
                        drawLabel(numbers[i], Offset(c.x, c.y - 16.dp.toPx()))
                        drawLabel(values[i], Offset(c.x, c.y + 24.dp.toPx()))
                    }
                }
            },
    )
}

/** One bar per cylinder around zero, with ±2 mg caution lines. */
@Composable
private fun BalanceChart(correctionStates: List<State<Float>>) {
    val states = rememberUpdatedState(correctionStates)
    val measurer = rememberTextMeasurer()
    Text("Injector correction · mg/stroke", style = PageSmall, color = IngweTextTertiary, modifier = Modifier.padding(start = 34.dp))
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .drawWithCache {
                val left = 34.dp.toPx()
                val top = 6.dp.toPx()
                val bottom = size.height - 6.dp.toPx()
                val mid = (top + bottom) / 2f
                fun y(v: Float) = mid - (v.coerceIn(-3f, 3f) / 3f) * (bottom - top) / 2f
                val axis = listOf(-2, 0, 2).map { it to measurer.label(if (it > 0) "+$it" else "$it", if (it == 0) IngweTextTertiary else IngweCaution, 12f) }
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                onDrawBehind {
                    axis.forEach { (g, l) ->
                        drawLine(if (g == 0) IngweHairline else IngweCaution.copy(alpha = 0.6f), Offset(left, y(g.toFloat())), Offset(size.width, y(g.toFloat())), 1f, pathEffect = if (g == 0) null else dash)
                        drawLabel(l, Offset(left - 8.dp.toPx(), y(g.toFloat())), ax = 1f)
                    }
                    val cw = (size.width - left) / 6f
                    states.value.forEachIndexed { i, st -> val v = st.value
                        val x = left + i * cw + cw * 0.3f
                        val bw = cw * 0.4f
                        drawRoundRect(injColor(v), Offset(x, minOf(y(v), mid)), Size(bw, abs(y(v) - mid).coerceAtLeast(1f)), CornerRadius(3.dp.toPx()))
                    }
                }
            },
    )
    Row(Modifier.fillMaxWidth().padding(start = 34.dp)) {
        repeat(6) { Text("cyl ${it + 1}", style = PageSmall, color = IngweTextTertiary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}

/** Resistance per plug ("open" if failed) and the glow-phase countdown. */
@Composable
private fun GlowCard(car: VehicleState) {
    val ohms = car.glowOhms
    if (ohms == null) {
        Text("Glow plug data needs VW UDS identifiers — not mapped yet.", style = PageLabel, color = IngweTextTertiary)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ohms.forEachIndexed { i, ohm ->
            val open = ohm == null
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (open) IngweAlert.copy(alpha = 0.12f) else IngweSurfaceHigh)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text("${i + 1}", style = PageSmall, color = IngweTextTertiary)
                Text(if (open) "open" else "%.2f Ω".format(ohm), style = PageValueSmall.copy(fontSize = 15.sp()), color = if (open) IngweAlert else IngweTextPrimary)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    val total = if (car.glowPhase == GlowPhase.PRE) 6f else 180f
    val fraction = if (car.glowPhase == GlowPhase.OFF) 0f else (car.glowSecondsLeft / total).coerceIn(0f, 1f)
    Spacer(
        Modifier.fillMaxWidth().height(4.dp).drawBehind {
            drawRoundRect(IngweHairline, cornerRadius = CornerRadius(2.dp.toPx()))
            if (fraction > 0f) drawRoundRect(IngweAccent, size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(2.dp.toPx()))
        },
    )
    Spacer(Modifier.height(4.dp))
    Text(
        when (car.glowPhase) {
            GlowPhase.OFF -> "Plugs off"
            GlowPhase.PRE -> "Pre-glow · ${car.glowSecondsLeft.toInt() + 1} s left"
            GlowPhase.AFTER -> "After-glow · ${car.glowSecondsLeft.toInt() + 1} s left"
        },
        style = PageSmall,
        color = IngweTextSecondary,
    )
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
