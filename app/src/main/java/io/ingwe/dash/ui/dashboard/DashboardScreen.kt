package io.ingwe.dash.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.ingwe.dash.ui.components.gauges.*
import io.ingwe.dash.ui.navigation.Screen
import io.ingwe.dash.ui.theme.*

/**
 * Modern Motorsport & OEM Instrument Cluster Dashboard for VW Amarok.
 *
 * Dedicated Views:
 *   1. TURBO & CLUSTER: Tachometer, Boost Gauge (BAR/PSI with Target vs Actual), Speedometer, EGT, Charge Air, VGT
 *   2. ZF DRIVELINE: Gear (D1-D8), Torque Converter Lockup, ATF Fluid Temp, Engine Torque (Nm), Load
 *   3. 4MOTION OFF-ROAD: Steering Angle, Pitch & Roll Inclinometers, Wheel Slip Differential
 */
@Composable
fun DashboardScreen(navController: NavController) {

    // Boost configuration: unit toggle (BAR vs PSI)
    var boostUnit by remember { mutableStateOf(BoostUnit.BAR) }

    // Dashboard view modes: "TURBO", "DRIVELINE", "OFFROAD"
    var activeTab by remember { mutableStateOf("TURBO") }

    // Simulation / throttle state (0f = Idle, 1f = Full Throttle Boost)
    var throttleInput by remember { mutableFloatStateOf(0.55f) }
    var showSimControls by remember { mutableStateOf(false) }

    // Simulated dynamic engine & vehicle parameters
    val simulatedRpm = 800f + throttleInput * 3500f
    val simulatedBoostKPa = 95f + throttleInput * 165f // Actual Boost: -0.05 bar up to +1.60 bar (23.2 PSI)
    val simulatedTargetBoostKPa = 100f + throttleInput * 170f // Target Boost commanded by ECM
    val simulatedSpeed = throttleInput * 135f
    val simulatedLoad = (15f + throttleInput * 75f).coerceIn(0f, 100f)
    val simulatedCoolant = 88f + (throttleInput * 8f)
    val simulatedOil = 92f + (throttleInput * 14f)
    val simulatedBattery = 14.2f - (throttleInput * 0.3f)
    val simulatedFuelRate = 1.2f + throttleInput * 14.5f

    // ── Turbo Data ──
    val simulatedEgtC = 310f + throttleInput * 470f // 310°C idle up to 780°C full pull
    val simulatedChargeAirC = 30f + throttleInput * 28f // Intercooler outlet temp
    val simulatedVgtDutyPct = 35f + throttleInput * 55f // VNT vane position

    // ── ZF 8HP Driveline Data ──
    val simulatedGear = when {
        simulatedSpeed < 15f -> "D1"
        simulatedSpeed < 32f -> "D2"
        simulatedSpeed < 50f -> "D3"
        simulatedSpeed < 70f -> "D4"
        simulatedSpeed < 92f -> "D5"
        simulatedSpeed < 112f -> "D6"
        simulatedSpeed < 130f -> "D7"
        else -> "D8"
    }
    val simulatedLockup = if (throttleInput > 0.85f && simulatedSpeed < 50f) "SLIP" else "LOCKED"
    val simulatedAtfTemp = 82f + (throttleInput * 14f)
    val simulatedTorqueNm = 110f + throttleInput * 470f // up to 580 Nm

    // ── 4Motion Off-Road Data (#6) ──
    val simulatedSteeringAngle = (throttleInput * 30f - 15f) // Steering angle in degrees
    val simulatedPitch = (throttleInput * 18f - 4f)         // Inclinometer Pitch angle
    val simulatedRoll = 6f                                  // Inclinometer Roll angle
    val simulatedWheelSlip = (throttleInput * 8.5f)         // Slip %

    // Peak boost tracking (in kPa abs)
    var peakBoostKPa by remember { mutableFloatStateOf(245f) }
    if (simulatedBoostKPa > peakBoostKPa) {
        peakBoostKPa = simulatedBoostKPa
    }

    val activeDtcCount = 3
    val sampleRate = "10.4 sps"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0C0E12),
                        Color(0xFF07080A),
                        Color(0xFF050507)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // ── Top Navigation & Vehicle Header ────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(label = "OBD-II LIVE", isConnected = true)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "VW AMAROK 3.0 V6 TDI",
                        color = IngweOnSurface.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 2.sp
                    )
                }

                // Cockpit Page Switcher Tabs
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(Color(0xFF141720), shape = RoundedCornerShape(8.dp))
                        .padding(2.dp)
                ) {
                    listOf(
                        "TURBO" to "TURBO CLUSTER",
                        "DRIVELINE" to "ZF 8HP",
                        "OFFROAD" to "4MOTION"
                    ).forEach { (key, label) ->
                        val selected = activeTab == key
                        Surface(
                            color = if (selected) IngwePrimary else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { activeTab = key }
                        ) {
                            Text(
                                text = label,
                                color = if (selected) IngweOnPrimary else IngweOnSurface.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Unit & Sim Toggles
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFF161A22),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A313E)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            boostUnit = if (boostUnit == BoostUnit.BAR) BoostUnit.PSI else BoostUnit.BAR
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "BOOST: ", color = IngweOnSurface.copy(alpha = 0.5f), fontSize = 11.sp)
                            Text(text = boostUnit.label, color = IngwePrimary, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(text = "/", color = IngweOnSurface.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Surface(
                        color = if (showSimControls) IngwePrimary.copy(alpha = 0.2f) else Color(0xFF161A22),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (showSimControls) IngwePrimary else Color(0xFF2A313E)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { showSimControls = !showSimControls }
                    ) {
                        Text(
                            text = if (showSimControls) "TEST: ON" else "TEST: OFF",
                            color = if (showSimControls) IngwePrimary else IngweOnSurface.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── DTC Fault Alert Banner ─────────────────────────────────────
            Surface(
                color = if (activeDtcCount > 0) Color(0xFF2D1414) else Color(0xFF102016),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (activeDtcCount > 0) IngweError.copy(alpha = 0.7f) else IngweSecondary.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(Screen.Dtc.route) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (activeDtcCount > 0) IngweError else IngweSecondary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (activeDtcCount > 0)
                                "WARNING: $activeDtcCount ERROR CODES DETECTED: P0299, P0401, P2002"
                            else
                                "SYSTEMS NORMAL - NO FAULT CODES STORED",
                            color = if (activeDtcCount > 0) Color(0xFFFF8A80) else IngweSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    Text(
                        text = "VIEW & CLEAR DTCs",
                        color = if (activeDtcCount > 0) IngweError else IngweSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            // Interactive simulation throttle controls
            AnimatedVisibility(visible = showSimControls) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .background(Color(0xFF141720), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "SIMULATED THROTTLE & LOAD DRIVE:",
                            color = IngweOnSurface.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("IDLE" to 0.05f, "CRUISE" to 0.40f, "BOOST" to 0.75f, "WOT" to 1.0f).forEach { (lbl, preset) ->
                                Surface(
                                    color = if (kotlin.math.abs(throttleInput - preset) < 0.08f) IngwePrimary else Color(0xFF1F2430),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.clickable { throttleInput = preset }
                                ) {
                                    Text(
                                        text = lbl,
                                        color = if (kotlin.math.abs(throttleInput - preset) < 0.08f) IngweOnPrimary else IngweOnSurface,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    Slider(
                        value = throttleInput,
                        onValueChange = { throttleInput = it },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = IngwePrimary,
                            activeTrackColor = IngwePrimary,
                            inactiveTrackColor = Color(0xFF2B313F)
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Primary View Body ──────────────────────────────────────────
            when (activeTab) {
                "TURBO" -> {
                    // 1. TURBO & CLUSTER VIEW
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Tachometer
                        TachometerGauge(
                            rpm = simulatedRpm,
                            size = 195.dp,
                            peakRpm = 4200f
                        )

                        // Center: Boost Gauge with Bar / PSI toggle
                        BoostGauge(
                            boostKPa = simulatedBoostKPa,
                            unit = boostUnit,
                            onToggleUnit = {
                                boostUnit = if (boostUnit == BoostUnit.BAR) BoostUnit.PSI else BoostUnit.BAR
                            },
                            size = 215.dp,
                            peakKPa = peakBoostKPa
                        )

                        // Right: Speedometer
                        SpeedometerGauge(
                            speedKmH = simulatedSpeed,
                            size = 195.dp
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    // Turbo & Thermal Aux telemetry pods
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CompactArcMeter(
                            label = "EGT PRE-TURBO",
                            value = simulatedEgtC,
                            unit = "°C",
                            min = 200f,
                            max = 900f,
                            warnMax = 720f,
                            dangerMax = 780f,
                            customColor = IngwePrimary,
                            modifier = Modifier.weight(1f)
                        )

                        CompactArcMeter(
                            label = "CHARGE AIR",
                            value = simulatedChargeAirC,
                            unit = "°C",
                            min = 0f,
                            max = 90f,
                            warnMax = 55f,
                            dangerMax = 70f,
                            customColor = Color(0xFF00E5FF),
                            modifier = Modifier.weight(1f)
                        )

                        CompactArcMeter(
                            label = "VGT VANE POS",
                            value = simulatedVgtDutyPct,
                            unit = "%",
                            min = 0f,
                            max = 100f,
                            customColor = IngweSecondary,
                            modifier = Modifier.weight(1f)
                        )

                        CompactArcMeter(
                            label = "ENG TORQUE",
                            value = simulatedTorqueNm,
                            unit = "Nm",
                            min = 0f,
                            max = 650f,
                            warnMax = 580f,
                            customColor = IngwePrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                "DRIVELINE" -> {
                    // 2. ZF 8HP DRIVELINE VIEW
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Big Boost Gauge
                        BoostGauge(
                            boostKPa = simulatedBoostKPa,
                            unit = boostUnit,
                            onToggleUnit = {
                                boostUnit = if (boostUnit == BoostUnit.BAR) BoostUnit.PSI else BoostUnit.BAR
                            },
                            size = 210.dp,
                            peakKPa = peakBoostKPa,
                            modifier = Modifier.weight(1f)
                        )

                        // Right: Full ZF 8HP Status Card
                        Column(
                            modifier = Modifier.weight(1.3f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TransmissionDrivelineCard(
                                gear = simulatedGear,
                                lockupState = simulatedLockup,
                                atfTempC = simulatedAtfTemp,
                                engineTorqueNm = simulatedTorqueNm
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CompactArcMeter(
                                    label = "COOLANT",
                                    value = simulatedCoolant,
                                    unit = "°C",
                                    min = 50f,
                                    max = 130f,
                                    normalMin = 80f,
                                    normalMax = 98f,
                                    warnMax = 105f,
                                    dangerMax = 115f,
                                    customColor = IngweSecondary,
                                    modifier = Modifier.weight(1f)
                                )

                                CompactArcMeter(
                                    label = "OIL TEMP",
                                    value = simulatedOil,
                                    unit = "°C",
                                    min = 50f,
                                    max = 150f,
                                    normalMin = 85f,
                                    normalMax = 110f,
                                    warnMax = 125f,
                                    dangerMax = 138f,
                                    customColor = IngwePrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                "OFFROAD" -> {
                    // 3. 4MOTION OFF-ROAD VIEW (#6)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Speed & Tach small combo
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BoostGauge(
                                boostKPa = simulatedBoostKPa,
                                unit = boostUnit,
                                onToggleUnit = {
                                    boostUnit = if (boostUnit == BoostUnit.BAR) BoostUnit.PSI else BoostUnit.BAR
                                },
                                size = 185.dp,
                                peakKPa = peakBoostKPa
                            )
                        }

                        // Right: Off-Road Telemetry Card
                        Column(
                            modifier = Modifier.weight(1.4f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OffRoadTelemetryCard(
                                steeringAngleDeg = simulatedSteeringAngle,
                                pitchDeg = simulatedPitch,
                                rollDeg = simulatedRoll,
                                wheelSlipPct = simulatedWheelSlip
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CompactArcMeter(
                                    label = "ENG TORQUE",
                                    value = simulatedTorqueNm,
                                    unit = "Nm",
                                    min = 0f,
                                    max = 650f,
                                    customColor = IngwePrimary,
                                    modifier = Modifier.weight(1f)
                                )

                                CompactArcMeter(
                                    label = "BATTERY",
                                    value = simulatedBattery,
                                    unit = "V",
                                    min = 10f,
                                    max = 16f,
                                    normalMin = 13f,
                                    customColor = IngweSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = IngweOutline)
            Spacer(Modifier.height(6.dp))

            // ── Bottom Navigation Bar ──────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { navController.navigate(Screen.Terminal.route) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = IngweOnSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C3240)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("TERMINAL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { navController.navigate(Screen.Dtc.route) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeDtcCount > 0) IngweError.copy(alpha = 0.25f) else Color(0xFF1E222C)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (activeDtcCount > 0) IngweError else Color(0xFF2C3240)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "DTCs",
                                color = if (activeDtcCount > 0) IngweError else IngweOnSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (activeDtcCount > 0) {
                                Spacer(Modifier.width(6.dp))
                                Surface(color = IngweError, shape = CircleShape) {
                                    Text(
                                        text = "$activeDtcCount",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "FUEL: ${"%.1f".format(simulatedFuelRate)} L/h",
                        color = IngweOnSurface.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = sampleRate,
                        color = IngweSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, isConnected: Boolean) {
    val color = if (isConnected) IngweSecondary else IngweError
    Surface(
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
