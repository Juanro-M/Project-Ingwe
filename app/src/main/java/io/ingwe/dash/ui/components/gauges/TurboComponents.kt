package io.ingwe.dash.ui.components.gauges

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ingwe.dash.ui.theme.*
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val VANE_COUNT = 11
/** How far each vane rotates between fully closed and fully open. */
private const val VANE_TRAVEL_DEG = 58f

/**
 * Variable-geometry turbo, drawn as its nozzle ring.
 *
 * Eleven guide vanes sit around the turbine wheel. At 0 % they lie nearly
 * tangential and overlap — a tight nozzle that spools the turbo at low rpm.
 * As the position rises they pivot open towards radial, widening the gaps.
 * A thin ring around the outside shows the same percentage.
 */
@Composable
fun VgtVaneGauge(positionPct: Float, modifier: Modifier = Modifier, size: Dp = 124.dp) {
    val fraction by animateFloatAsState((positionPct / 100f).coerceIn(0f, 1f), tween(220), label = "vgt")

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(bottom = 14.dp)) {
            val r = this.size.minDimension / 2f // `size` alone would be the Dp parameter
            val c = center

            // Housing ring + percentage ring
            val ringR = r * 0.94f
            drawCircle(IngweHairline, ringR, c, style = Stroke(r * 0.05f))
            drawArc(
                color = IngweTeal,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(c.x - ringR, c.y - ringR),
                size = Size(ringR * 2, ringR * 2),
                style = Stroke(r * 0.05f, cap = StrokeCap.Round),
            )

            // Turbine wheel: hub with swept blades
            drawCircle(IngweSurfaceHigh, r * 0.46f, c)
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0)
                val b = Math.toRadians(i * 30.0 + 22.0)
                drawLine(
                    IngweTextTertiary,
                    Offset(c.x + r * 0.34f * cos(a).toFloat(), c.y + r * 0.34f * sin(a).toFloat()),
                    Offset(c.x + r * 0.45f * cos(b).toFloat(), c.y + r * 0.45f * sin(b).toFloat()),
                    r * 0.03f,
                    StrokeCap.Round,
                )
            }
            drawCircle(IngweBackground, r * 0.33f, c)

            // Guide vanes: pivot on a ring, rotate from tangential (closed) towards radial (open)
            val pivotR = r * 0.66f
            val vaneLen = r * 0.30f
            for (i in 0 until VANE_COUNT) {
                val theta = i * 360f / VANE_COUNT
                val pivot = Offset(
                    c.x + pivotR * cos(Math.toRadians(theta.toDouble())).toFloat(),
                    c.y + pivotR * sin(Math.toRadians(theta.toDouble())).toFloat(),
                )
                val vaneDeg = theta + 90f + 12f - fraction * VANE_TRAVEL_DEG
                val dx = cos(Math.toRadians(vaneDeg.toDouble())).toFloat()
                val dy = sin(Math.toRadians(vaneDeg.toDouble())).toFloat()
                drawLine(
                    IngweTeal,
                    Offset(pivot.x - dx * vaneLen * 0.45f, pivot.y - dy * vaneLen * 0.45f),
                    Offset(pivot.x + dx * vaneLen * 0.55f, pivot.y + dy * vaneLen * 0.55f),
                    r * 0.075f,
                    StrokeCap.Round,
                )
                drawCircle(IngweSurface, r * 0.022f, pivot)
            }
        }

        // Readout in the turbine eye
        Row(
            Modifier.align(Alignment.Center).padding(bottom = 14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("${positionPct.roundToInt()}", style = ReadoutSmall.copy(fontSize = (size.value * 0.13f).sp), color = IngweTextPrimary)
            Text("%", style = LabelStyle.copy(fontSize = 10.sp), color = IngweTextTertiary, modifier = Modifier.padding(bottom = 2.dp))
        }
        Text(
            "VGT vane",
            style = LabelStyle,
            color = IngweTextSecondary,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
