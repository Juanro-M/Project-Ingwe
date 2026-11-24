package io.ingwe.dash.ui.components.gauges

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.*
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ingwe.dash.ui.theme.*
import kotlin.math.*

/**
 * Unit for the Boost Gauge.
 */
enum class BoostUnit(val label: String, val minVal: Float, val maxVal: Float, val zeroOffsetFraction: Float) {
    BAR("BAR", -1.0f, 2.5f, 1.0f / 3.5f),   // -1.0 to +2.5 bar (3.5 bar span, zero is at ~28.5%)
    PSI("PSI", -15.0f, 35.0f, 15.0f / 50.0f) // -15 to +35 psi (50 psi span, zero is at 30%)
}

/**
 * Sleek, circular Canvas-based automotive dial gauge with customizable ticks,
 * color-coded zones, smooth tapered needle, center hub cap, and digital readout.
 */
@Composable
fun CircularDialGauge(
    value: Float,
    min: Float,
    max: Float,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
    size: Dp = 190.dp,
    startAngle: Float = 135f,
    sweepAngle: Float = 270f,
    majorTickStep: Float = 1f,
    minorTicksPerMajor: Int = 4,
    warningThreshold: Float? = null,
    dangerThreshold: Float? = null,
    zeroThreshold: Float? = null,
    primaryColor: Color = IngwePrimary,
    warningColor: Color = IngweWarning,
    dangerColor: Color = IngweError,
    negativeZoneColor: Color = Color(0xFF00E5FF),
    peakValue: Float? = null,
    valueFormatter: (Float) -> String = { "%.0f".format(it) },
    tickFormatter: ((Float) -> String)? = null,
    subtitle: String? = null,
    onHeaderClick: (() -> Unit)? = null,
) {
    val clampedValue = value.coerceIn(min, max)
    val animatedValue by animateFloatAsState(
        targetValue = clampedValue,
        animationSpec = tween(durationMillis = 250),
        label = "gauge_needle_anim"
    )

    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E2229),
                        Color(0xFF111317),
                        Color(0xFF090A0C)
                    )
                )
            )
            .border(
                width = 2.dp,
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF3A3F4B),
                        Color(0xFF1A1C20),
                        Color(0xFF4A5160),
                        Color(0xFF1A1C20),
                        Color(0xFF3A3F4B)
                    )
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.width / 2f
            val dialRadius = radius * 0.90f
            val arcStrokeWidth = radius * 0.075f

            // 1. Draw outer subtle track arc
            drawArc(
                color = Color(0xFF1A1D24),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - dialRadius, center.y - dialRadius),
                size = Size(dialRadius * 2, dialRadius * 2),
                style = Stroke(width = arcStrokeWidth, cap = StrokeCap.Round)
            )

            // 2. Draw colored zone arcs
            val totalSpan = max - min
            fun valueToAngle(v: Float): Float {
                val fraction = (v - min) / totalSpan
                return startAngle + fraction * sweepAngle
            }

            // Negative zone (for boost/vacuum)
            if (zeroThreshold != null && zeroThreshold > min) {
                val zeroAngle = valueToAngle(zeroThreshold)
                val negSweep = zeroAngle - startAngle
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(negativeZoneColor.copy(alpha = 0.3f), negativeZoneColor),
                        center = center
                    ),
                    startAngle = startAngle,
                    sweepAngle = negSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - dialRadius, center.y - dialRadius),
                    size = Size(dialRadius * 2, dialRadius * 2),
                    style = Stroke(width = arcStrokeWidth, cap = StrokeCap.Butt)
                )
            }

            // Warning zone arc
            if (warningThreshold != null && warningThreshold < max) {
                val warnAngle = valueToAngle(warningThreshold)
                val dangerStart = dangerThreshold ?: max
                val dangerAngle = valueToAngle(dangerStart)
                val warnSweep = dangerAngle - warnAngle
                drawArc(
                    color = warningColor.copy(alpha = 0.8f),
                    startAngle = warnAngle,
                    sweepAngle = warnSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - dialRadius, center.y - dialRadius),
                    size = Size(dialRadius * 2, dialRadius * 2),
                    style = Stroke(width = arcStrokeWidth, cap = StrokeCap.Butt)
                )
            }

            // Danger / Redline zone arc
            if (dangerThreshold != null && dangerThreshold < max) {
                val dangerAngle = valueToAngle(dangerThreshold)
                val dangerSweep = (startAngle + sweepAngle) - dangerAngle
                drawArc(
                    color = dangerColor,
                    startAngle = dangerAngle,
                    sweepAngle = dangerSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - dialRadius, center.y - dialRadius),
                    size = Size(dialRadius * 2, dialRadius * 2),
                    style = Stroke(width = arcStrokeWidth, cap = StrokeCap.Round)
                )
            }

            // Active value fill arc
            val currentFraction = (animatedValue - min) / totalSpan
            val currentAngle = currentFraction * sweepAngle
            if (currentAngle > 0.5f) {
                val activeBrush = if (zeroThreshold != null && animatedValue < zeroThreshold) {
                    Brush.sweepGradient(
                        colors = listOf(negativeZoneColor.copy(alpha = 0.5f), negativeZoneColor),
                        center = center
                    )
                } else {
                    Brush.sweepGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.6f), primaryColor, warningColor),
                        center = center
                    )
                }

                drawArc(
                    brush = activeBrush,
                    startAngle = startAngle,
                    sweepAngle = currentAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - dialRadius, center.y - dialRadius),
                    size = Size(dialRadius * 2, dialRadius * 2),
                    style = Stroke(width = arcStrokeWidth * 0.85f, cap = StrokeCap.Round)
                )
            }

            // 3. Draw Ticks & Numbers
            val majorTickCount = ((max - min) / majorTickStep).roundToInt()
            val innerMajorRadius = dialRadius - radius * 0.15f
            val innerMinorRadius = dialRadius - radius * 0.08f
            val textRadius = dialRadius - radius * 0.28f

            for (i in 0..majorTickCount) {
                val tickVal = min + i * majorTickStep
                if (tickVal > max + 0.001f) break
                val tickFraction = (tickVal - min) / totalSpan
                val angleDeg = startAngle + tickFraction * sweepAngle
                val angleRad = Math.toRadians(angleDeg.toDouble())

                val cosA = cos(angleRad).toFloat()
                val sinA = sin(angleRad).toFloat()

                val tickColor = when {
                    dangerThreshold != null && tickVal >= dangerThreshold -> dangerColor
                    warningThreshold != null && tickVal >= warningThreshold -> warningColor
                    zeroThreshold != null && tickVal < zeroThreshold -> negativeZoneColor
                    else -> IngweOnSurface.copy(alpha = 0.75f)
                }

                // Major tick line
                val outerP = Offset(center.x + dialRadius * cosA, center.y + dialRadius * sinA)
                val innerP = Offset(center.x + innerMajorRadius * cosA, center.y + innerMajorRadius * sinA)
                drawLine(
                    color = tickColor,
                    start = innerP,
                    end = outerP,
                    strokeWidth = radius * 0.024f,
                    cap = StrokeCap.Round
                )

                // Tick number label
                val labelText = tickFormatter?.invoke(tickVal) ?: run {
                    if (majorTickStep >= 1f) "%.0f".format(tickVal) else "%.1f".format(tickVal)
                }
                val textLayout = textMeasurer.measure(
                    text = AnnotatedString(labelText),
                    style = TextStyle(
                        color = tickColor.copy(alpha = 0.9f),
                        fontSize = (radius * 0.11f).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                )
                val textPos = Offset(
                    center.x + textRadius * cosA - (textLayout.size.width / 2f),
                    center.y + textRadius * sinA - (textLayout.size.height / 2f)
                )
                drawText(textLayout, topLeft = textPos)

                // Minor ticks between majors
                if (i < majorTickCount) {
                    val minorStep = majorTickStep / (minorTicksPerMajor + 1)
                    for (m in 1..minorTicksPerMajor) {
                        val minorVal = tickVal + m * minorStep
                        if (minorVal > max) break
                        val minorFraction = (minorVal - min) / totalSpan
                        val mAngleDeg = startAngle + minorFraction * sweepAngle
                        val mAngleRad = Math.toRadians(mAngleDeg.toDouble())
                        val mCosA = cos(mAngleRad).toFloat()
                        val mSinA = sin(mAngleRad).toFloat()

                        val mOuterP = Offset(center.x + dialRadius * mCosA, center.y + dialRadius * mSinA)
                        val mInnerP = Offset(center.x + innerMinorRadius * mCosA, center.y + innerMinorRadius * mSinA)
                        drawLine(
                            color = tickColor.copy(alpha = 0.35f),
                            start = mInnerP,
                            end = mOuterP,
                            strokeWidth = radius * 0.012f
                        )
                    }
                }
            }

            // 4. Peak hold marker
            if (peakValue != null && peakValue in min..max) {
                val peakAngle = valueToAngle(peakValue)
                val peakRad = Math.toRadians(peakAngle.toDouble())
                val peakCos = cos(peakRad).toFloat()
                val peakSin = sin(peakRad).toFloat()
                val pStart = Offset(center.x + (dialRadius - radius * 0.12f) * peakCos, center.y + (dialRadius - radius * 0.12f) * peakSin)
                val pEnd = Offset(center.x + dialRadius * peakCos, center.y + dialRadius * peakSin)
                drawLine(
                    color = dangerColor,
                    start = pStart,
                    end = pEnd,
                    strokeWidth = radius * 0.035f,
                    cap = StrokeCap.Round
                )
            }

            // 5. Draw Tapered Needle
            val needleAngle = startAngle + currentFraction * sweepAngle
            val needleLength = dialRadius * 0.90f
            val needleBaseWidth = radius * 0.05f

            rotate(degrees = needleAngle - 90f, pivot = center) {
                val needlePath = Path().apply {
                    moveTo(center.x - needleBaseWidth, center.y)
                    lineTo(center.x, center.y - needleLength)
                    lineTo(center.x + needleBaseWidth, center.y)
                    lineTo(center.x, center.y + radius * 0.18f) // counterweight
                    close()
                }

                // Needle shadow
                drawPath(
                    path = needlePath,
                    color = Color.Black.copy(alpha = 0.45f)
                )

                // Needle body
                val needleColor = when {
                    dangerThreshold != null && animatedValue >= dangerThreshold -> dangerColor
                    zeroThreshold != null && animatedValue < zeroThreshold -> negativeZoneColor
                    else -> primaryColor
                }

                drawPath(
                    path = needlePath,
                    brush = Brush.linearGradient(
                        colors = listOf(needleColor.copy(alpha = 0.85f), needleColor, Color.White),
                        start = Offset(center.x, center.y + radius * 0.18f),
                        end = Offset(center.x, center.y - needleLength)
                    )
                )

                // Needle glowing tip highlight
                drawCircle(
                    color = Color.White,
                    radius = radius * 0.02f,
                    center = Offset(center.x, center.y - needleLength * 0.95f)
                )
            }

            // 6. Center Hub cap (layered metallic effect)
            val hubRadius = radius * 0.22f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF383E4B), Color(0xFF1E2127), Color(0xFF0C0E11)),
                    center = center
                ),
                radius = hubRadius,
                center = center
            )
            drawCircle(
                color = primaryColor.copy(alpha = 0.4f),
                radius = hubRadius * 0.45f,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.2f),
                radius = hubRadius * 0.2f,
                center = center
            )
        }

        // 7. Center-Lower Digital Readout & Label
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = size * 0.16f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (onHeaderClick != null) Modifier.clickable { onHeaderClick() } else Modifier
            ) {
                Text(
                    text = label,
                    color = IngweOnSurface.copy(alpha = 0.7f),
                    fontSize = (size.value * 0.065f).sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                if (subtitle != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "[$subtitle]",
                        color = primaryColor,
                        fontSize = (size.value * 0.055f).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = valueFormatter(animatedValue),
                    color = when {
                        dangerThreshold != null && animatedValue >= dangerThreshold -> dangerColor
                        warningThreshold != null && animatedValue >= warningThreshold -> warningColor
                        zeroThreshold != null && animatedValue < zeroThreshold -> negativeZoneColor
                        else -> IngweOnSurface
                    },
                    fontSize = (size.value * 0.14f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unit,
                    color = primaryColor,
                    fontSize = (size.value * 0.065f).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = (size.value * 0.02f).dp)
                )
            }

            if (peakValue != null) {
                Text(
                    text = "PK ${valueFormatter(peakValue)}",
                    color = dangerColor.copy(alpha = 0.85f),
                    fontSize = (size.value * 0.05f).sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Dedicated Sleek Boost Gauge with BAR ⇄ PSI toggle, vacuum/boost zones,
 * overboost warning redline, and peak boost memory.
 */
@Composable
fun BoostGauge(
    boostKPa: Float,
    unit: BoostUnit,
    onToggleUnit: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 210.dp,
    peakKPa: Float = boostKPa
) {
    // 100 kPa is approx 1 atm (ambient)
    // Gauge pressure in Bar: (kPa - 101.3) / 100
    // Gauge pressure in PSI: ((kPa - 101.3) * 0.145038)
    val gaugePressureBar = ((boostKPa - 100f) / 100f).coerceIn(-1.0f, 2.5f)
    val gaugePressurePsi = (gaugePressureBar * 14.5038f).coerceIn(-15.0f, 35.0f)

    val peakBar = ((peakKPa - 100f) / 100f).coerceIn(-1.0f, 2.5f)
    val peakPsi = (peakBar * 14.5038f).coerceIn(-15.0f, 35.0f)

    val isBar = unit == BoostUnit.BAR
    val displayVal = if (isBar) gaugePressureBar else gaugePressurePsi
    val peakVal = if (isBar) peakBar else peakPsi
    val minVal = if (isBar) -1.0f else -15.0f
    val maxVal = if (isBar) 2.5f else 35.0f
    val majorStep = if (isBar) 0.5f else 10.0f
    val warnThreshold = if (isBar) 1.8f else 26.0f
    val dangerThreshold = if (isBar) 2.2f else 31.0f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Boost gauge unit toggle switch pill
        Surface(
            color = Color(0xFF14171D),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, IngweOutline),
            modifier = Modifier
                .clickable { onToggleUnit() }
                .padding(bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BOOST",
                    color = IngweOnSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isBar) "BAR" else "PSI",
                    color = IngwePrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "⇄",
                    color = IngweOnSurface.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
        }

        CircularDialGauge(
            value = displayVal,
            min = minVal,
            max = maxVal,
            label = "BOOST",
            unit = unit.label,
            size = size,
            majorTickStep = majorStep,
            minorTicksPerMajor = if (isBar) 4 else 1,
            zeroThreshold = 0f,
            warningThreshold = warnThreshold,
            dangerThreshold = dangerThreshold,
            negativeZoneColor = Color(0xFF00E5FF),
            primaryColor = IngwePrimary,
            warningColor = IngweWarning,
            dangerColor = IngweError,
            peakValue = peakVal,
            valueFormatter = {
                if (isBar) {
                    (if (it >= 0) "+%.2f" else "%.2f").format(it)
                } else {
                    (if (it >= 0) "+%.1f" else "%.1f").format(it)
                }
            },
            tickFormatter = {
                if (isBar) "%.1f".format(it) else "%.0f".format(it)
            },
            subtitle = if (displayVal < 0) "VAC" else "BOOST",
            onHeaderClick = onToggleUnit
        )
    }
}

/**
 * Dedicated Sleek Tachometer (RPM) Gauge tailored for Amarok Diesel (0-6000 RPM, 4500 Redline).
 */
@Composable
fun TachometerGauge(
    rpm: Float,
    modifier: Modifier = Modifier,
    size: Dp = 210.dp,
    peakRpm: Float? = null
) {
    CircularDialGauge(
        value = rpm,
        min = 0f,
        max = 6000f,
        label = "TACHOMETER",
        unit = "RPM",
        size = size,
        majorTickStep = 1000f,
        minorTicksPerMajor = 3,
        warningThreshold = 4000f,
        dangerThreshold = 4500f, // Amarok 2.0 BiTDI / 3.0 V6 TDI redline zone
        primaryColor = IngwePrimary,
        warningColor = IngweWarning,
        dangerColor = IngweError,
        peakValue = peakRpm,
        valueFormatter = { "%.0f".format(it) },
        tickFormatter = { "%.0f".format(it / 1000f) }, // Show 0, 1, 2, 3, 4, 5, 6
        subtitle = "x1000",
        modifier = modifier
    )
}

/**
 * Dedicated Sleek Speedometer Gauge (0-220 km/h).
 */
@Composable
fun SpeedometerGauge(
    speedKmH: Float,
    modifier: Modifier = Modifier,
    size: Dp = 190.dp
) {
    CircularDialGauge(
        value = speedKmH,
        min = 0f,
        max = 220f,
        label = "SPEED",
        unit = "km/h",
        size = size,
        majorTickStep = 20f,
        minorTicksPerMajor = 1,
        warningThreshold = 140f,
        dangerThreshold = 180f,
        primaryColor = Color(0xFF00E5FF),
        warningColor = IngweWarning,
        dangerColor = IngweError,
        valueFormatter = { "%.0f".format(it) },
        modifier = modifier
    )
}

/**
 * Sleek, curved compact arc meter for auxiliary vehicle signals (Coolant, Oil, Load, Battery).
 */
@Composable
fun CompactArcMeter(
    label: String,
    value: Float,
    unit: String,
    min: Float,
    max: Float,
    modifier: Modifier = Modifier,
    normalMin: Float? = null,
    normalMax: Float? = null,
    warnMax: Float? = null,
    dangerMax: Float? = null,
    customColor: Color = IngwePrimary,
    formatPattern: String = "%.0f"
) {
    val clampedValue = value.coerceIn(min, max)
    val fraction = ((clampedValue - min) / (max - min)).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(targetValue = fraction, label = "compact_arc")

    val stateColor = when {
        dangerMax != null && value >= dangerMax -> IngweError
        warnMax != null && value >= warnMax -> IngweWarning
        normalMin != null && value < normalMin -> Color(0xFF00E5FF) // Cool/Cold
        else -> customColor
    }

    Card(
        modifier = modifier
            .padding(4.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262A33)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = IngweOnSurface.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatPattern.format(value),
                        color = stateColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = unit,
                        color = IngweOnSurface.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Graduated bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1E222A))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedFraction)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(stateColor.copy(alpha = 0.6f), stateColor)
                            )
                        )
                )
            }

            Spacer(Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatPattern.format(min),
                    color = IngweOnSurface.copy(alpha = 0.35f),
                    fontSize = 9.sp
                )
                Text(
                    text = formatPattern.format(max),
                    color = IngweOnSurface.copy(alpha = 0.35f),
                    fontSize = 9.sp
                )
            }
        }
    }
}

/**
 * Dedicated ZF 8HP Driveline Status Card:
 * Shows current gear (D1-D8, S1-S8, R), Torque Converter Lockup,
 * ATF Fluid Temperature, and Real Engine Output Torque (Nm).
 */
@Composable
fun TransmissionDrivelineCard(
    gear: String,
    lockupState: String, // "LOCKED", "SLIP", "OPEN"
    atfTempC: Float,
    engineTorqueNm: Float,
    modifier: Modifier = Modifier
) {
    val atfColor = when {
        atfTempC >= 115f -> IngweError
        atfTempC >= 105f -> IngweWarning
        atfTempC < 65f -> Color(0xFF00E5FF)
        else -> IngweSecondary
    }

    val lockupColor = when (lockupState.uppercase()) {
        "LOCKED" -> IngweSecondary
        "SLIP" -> IngweWarning
        else -> Color(0xFF00E5FF)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13161F)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262D3D)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ZF 8HP DRIVELINE",
                    color = IngweOnSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Surface(
                    color = lockupColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, lockupColor.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "LOCKUP: $lockupState",
                        color = lockupColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Gear & Torque Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Gear Display
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "GEAR ",
                        color = IngweOnSurface.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = gear,
                        color = IngwePrimary,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Torque Display
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "OUTPUT TORQUE",
                        color = IngweOnSurface.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "%.0f".format(engineTorqueNm),
                            color = IngweOnSurface,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "Nm",
                            color = IngwePrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ATF Temp Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ATF FLUID TEMP",
                    color = IngweOnSurface.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "%.0f °C".format(atfTempC),
                    color = atfColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(4.dp))
            val atfFraction = ((atfTempC - 40f) / 100f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF1E2430))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(atfFraction)
                        .clip(RoundedCornerShape(3.dp))
                        .background(atfColor)
                )
            }
        }
    }
}

/**
 * Dedicated 4Motion Off-Road Telemetry Card:
 * Steering Angle with left/right visual pointer, Pitch & Roll Inclinometers,
 * and 4-wheel slip detection.
 */
@Composable
fun OffRoadTelemetryCard(
    steeringAngleDeg: Float,
    pitchDeg: Float,
    rollDeg: Float,
    wheelSlipPct: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13161F)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262D3D)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "4MOTION OFF-ROAD DYNAMICS",
                    color = IngweOnSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Surface(
                    color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "4WD ACTIVE",
                        color = Color(0xFF00E5FF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 3 Columns: Steering Angle | Pitch | Roll
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Steering
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "STEERING",
                        color = IngweOnSurface.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${if (steeringAngleDeg > 0) "R " else if (steeringAngleDeg < 0) "L " else ""}${abs(steeringAngleDeg).roundToInt()}°",
                        color = Color(0xFF00E5FF),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Pitch
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PITCH",
                        color = IngweOnSurface.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${if (pitchDeg > 0) "▲ +" else if (pitchDeg < 0) "▼ " else ""}${abs(pitchDeg).roundToInt()}°",
                        color = if (abs(pitchDeg) > 25f) IngweWarning else IngweOnSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Roll
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ROLL",
                        color = IngweOnSurface.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${abs(rollDeg).roundToInt()}°",
                        color = if (abs(rollDeg) > 20f) IngweError else IngweOnSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Wheel slip bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRACTION WHEEL SLIP",
                    color = IngweOnSurface.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "%.1f %%".format(wheelSlipPct),
                    color = if (wheelSlipPct > 15f) IngweWarning else IngweSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(3.dp))
            val slipFrac = (wheelSlipPct / 30f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF1E2430))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(slipFrac)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (wheelSlipPct > 15f) IngweWarning else IngweSecondary)
                )
            }
        }
    }
}

