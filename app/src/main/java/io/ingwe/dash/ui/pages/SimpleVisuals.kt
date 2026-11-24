package io.ingwe.dash.ui.pages

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ingwe.dash.data.vehicle.LockupState
import io.ingwe.dash.ui.components.gauges.LocalIgnitionSweep
import io.ingwe.dash.ui.components.gauges.NeedleSpring
import io.ingwe.dash.ui.components.gauges.sweepTowards
import io.ingwe.dash.ui.theme.*
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val BigNumber = ReadoutLarge.copy(fontSize = 34.sp)

/** Common rail: big pressure number, one bar, a white tick at the ECU's target. */
@Composable
fun RailReadout(railBar: Float, targetBar: Float) {
    val max = 2000f
    val sweep = LocalIgnitionSweep.current
    val animated = animateFloatAsState(if (railBar.isNaN()) 0f else railBar, NeedleSpring, label = "rail")
    val text by remember(animated) { derivedStateOf { "%,d".format(animated.value.roundToInt()) } }

    Row(verticalAlignment = Alignment.Bottom) {
        Text(if (railBar.isNaN()) "–" else text, style = BigNumber, color = IngweTextPrimary)
        Spacer(Modifier.width(6.dp))
        Text("bar", style = PageLabel, color = IngweTextSecondary, modifier = Modifier.padding(bottom = 6.dp).weight(1f))
        if (!targetBar.isNaN()) {
            Text("target %,d".format(targetBar.roundToInt()), style = PageLabel, color = IngweTextSecondary, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
    Spacer(Modifier.height(10.dp))
    Spacer(
        Modifier.fillMaxWidth().height(4.dp).drawBehind {
            val corner = CornerRadius(2.dp.toPx())
            drawRoundRect(IngweHairline, cornerRadius = corner)
            val v = sweepTowards(animated.value, max, sweep.value)
            val fill = if (v >= 1800f) IngweCaution else IngweTeal
            drawRoundRect(fill, size = Size(size.width * (v / max).coerceIn(0f, 1f), size.height), cornerRadius = corner)
            if (!targetBar.isNaN()) {
                val x = size.width * (targetBar / max).coerceIn(0f, 1f)
                drawRect(IngweTextPrimary, Offset(x - 1.dp.toPx(), -4.dp.toPx()), Size(2.dp.toPx(), size.height + 8.dp.toPx()))
            }
        },
    )
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
        Text("0", style = PageSmall, color = IngweTextTertiary, modifier = Modifier.weight(1f))
        Text("2,000", style = PageSmall, color = IngweTextTertiary)
    }
}

/** EGR: big % open next to a butterfly-valve symbol (flat = closed, upright = fully open). */
@Composable
fun EgrReadout(openPct: Float) {
    val sweep = LocalIgnitionSweep.current
    val animated = animateFloatAsState(if (openPct.isNaN()) 0f else openPct, NeedleSpring, label = "egr")
    val text by remember(animated) { derivedStateOf { animated.value.roundToInt().coerceIn(0, 100).toString() } }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (openPct.isNaN()) "–" else text, style = BigNumber, color = IngweTextPrimary)
                Spacer(Modifier.width(6.dp))
                Text("% open", style = PageLabel, color = IngweTextSecondary, modifier = Modifier.padding(bottom = 6.dp))
            }
            Text(
                when {
                    openPct.isNaN() -> "No data"
                    openPct < 3f -> "Closed"
                    openPct > 30f -> "Recirculating exhaust"
                    else -> "Slightly open"
                },
                style = PageLabel,
                color = IngweTextTertiary,
            )
        }
        Spacer(
            Modifier.size(56.dp).drawBehind {
                val r = size.minDimension / 2f - 2.dp.toPx()
                drawCircle(IngweHairline, r, style = Stroke(2.dp.toPx()))
                val open = sweepTowards(animated.value, 100f, sweep.value) / 100f
                val a = open * Math.PI.toFloat() / 2f
                val l = r - 6.dp.toPx()
                drawLine(
                    if (open > 0.02f) IngweAccent else IngweTextTertiary,
                    Offset(center.x - cos(a) * l, center.y - sin(a) * l),
                    Offset(center.x + cos(a) * l, center.y + sin(a) * l),
                    4.dp.toPx(), StrokeCap.Round,
                )
                drawCircle(IngweSurfaceHigh, 4.dp.toPx())
            },
        )
    }
}

/** Gearbox: P R N D · 1–8 with the current gear lit, and one converter status line. */
@Composable
fun GearboxRow(gear: String?, lockup: LockupState?) {
    val mode = gear?.firstOrNull()?.toString() ?: "–"
    val number = gear?.drop(1)?.toIntOrNull()
    val slipping = lockup == LockupState.SLIP

    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf("P", "R", "N", "D").forEach { GearCell(it, lit = it == mode, numbered = false, slipping = false) }
        Spacer(Modifier.width(14.dp))
        (1..8).forEach { GearCell("$it", lit = it == number, numbered = true, slipping = slipping) }
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (lockup == null) IngweTextTertiary else if (slipping) IngweCaution else IngweTeal))
        Spacer(Modifier.width(8.dp))
        Text(
            when (lockup) {
                null -> "Torque converter –"
                LockupState.SLIP -> "Torque converter slipping"
                LockupState.LOCKED -> "Torque converter locked"
                LockupState.OPEN -> "Torque converter open"
            },
            style = PageLabel,
            color = IngweTextPrimary,
        )
    }
}

@Composable
private fun GearCell(label: String, lit: Boolean, numbered: Boolean, slipping: Boolean) {
    val bg = when {
        !lit -> androidx.compose.ui.graphics.Color.Transparent
        !numbered -> IngweSurfaceHigh
        slipping -> IngweCaution
        else -> IngweAccent
    }
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .size(width = 36.dp, height = 34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = PageValueSmall.copy(fontSize = 17.sp, fontWeight = if (lit) FontWeight.SemiBold else FontWeight.Medium),
            color = if (!lit) IngweTextTertiary else if (numbered) IngweOnAccent else IngweTextPrimary,
        )
    }
}
