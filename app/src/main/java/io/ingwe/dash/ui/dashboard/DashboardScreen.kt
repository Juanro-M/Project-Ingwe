package io.ingwe.dash.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.ingwe.dash.ui.components.gauges.*
import io.ingwe.dash.ui.dtc.FaultStore
import io.ingwe.dash.ui.navigation.Screen
import io.ingwe.dash.ui.theme.*
import kotlinx.coroutines.launch

private enum class ClusterView(val label: String) {
    CLUSTER("Cluster"),
    DRIVELINE("Driveline"),
    OFFROAD("Off-road"),
}

/**
 * Main instrument cluster for the VW Amarok 3.0 V6 TDI.
 *
 * Views:
 *   Cluster   — tacho, boost, speed; VGT / coolant / oil sub-dials; EGT, charge air, torque, battery
 *   Driveline — tacho with gear in the hub; ZF 8HP load, torque, lockup, ATF; coolant & oil
 *   Off-road  — roll and pitch clinometers; 4Motion steering, per-wheel slip, diff state, torque split
 *
 * Swipe left/right (or tap the tabs) to move between pages.
 *
 * M1: values come from [SimulatedVehicle], driven by the demo throttle.
 */
@Composable
fun DashboardScreen(navController: NavController, faultStore: FaultStore) {
    var boostUnit by rememberSaveable { mutableStateOf(BoostUnit.BAR) }
    // Swipe left/right between the three pages; the tabs follow and also drive it.
    val pager = rememberPagerState(pageCount = { ClusterView.entries.size })
    val view = ClusterView.entries[pager.currentPage]
    val scope = rememberCoroutineScope()
    var throttle by rememberSaveable { mutableFloatStateOf(0.55f) }
    var showDemoControls by rememberSaveable { mutableStateOf(false) }
    var autoSweep by remember { mutableStateOf(false) }

    // Stress test: drive the throttle idle ↔ full continuously so every gauge moves every frame.
    LaunchedEffect(autoSweep, showDemoControls) {
        if (!autoSweep || !showDemoControls) return@LaunchedEffect
        val a = Animatable(throttle)
        while (true) {
            a.animateTo(1f, tween(1600, easing = FastOutSlowInEasing)) { throttle = value }
            a.animateTo(0f, tween(1600, easing = FastOutSlowInEasing)) { throttle = value }
        }
    }

    val car = remember(throttle) { SimulatedVehicle.at(throttle) }

    // Peak hold is updated as a side effect, never during composition.
    var peakBoostKPa by rememberSaveable { mutableFloatStateOf(car.boostKPa) }
    LaunchedEffect(car.boostKPa) {
        if (car.boostKPa > peakBoostKPa) peakBoostKPa = car.boostKPa
    }

    val faults = faultStore.codes

    Column(
        Modifier
            .fillMaxSize()
            .background(IngweBackground)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Amarok 3.0 V6 TDI", style = LabelStyle.copy(fontSize = 14.sp), color = IngweTextPrimary)
                Spacer(Modifier.height(2.dp))
                StatusDot(text = "Demo data · ECU 7E0", color = IngweCaution)
            }

            ViewTabs(selected = view, onSelect = { scope.launch { pager.animateScrollToPage(it.ordinal) } })

            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SegmentedControl(
                    options = listOf(BoostUnit.BAR to "bar", BoostUnit.PSI to "psi"),
                    selected = boostUnit,
                    onSelect = { boostUnit = it },
                )
                HeaderLink(
                    text = if (faults.isEmpty()) "Faults" else "Faults  ${faults.size}",
                    color = if (faults.isEmpty()) IngweTextSecondary else IngweAlert,
                    onClick = { navController.navigate(Screen.Dtc.route) },
                )
                HeaderLink("Terminal", IngweTextSecondary) { navController.navigate(Screen.Terminal.route) }
                HeaderLink(
                    text = "Demo",
                    color = if (showDemoControls) IngweAccent else IngweTextSecondary,
                    onClick = { showDemoControls = !showDemoControls },
                )
            }
        }

        // ── Fault notice ─────────────────────────────────────────────────────
        if (faults.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(IngweSurface)
                    .clickable { navController.navigate(Screen.Dtc.route) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(IngweAlert))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${faults.size} fault code${if (faults.size == 1) "" else "s"} stored",
                    style = LabelStyle.copy(fontSize = 13.sp),
                    color = IngweTextPrimary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = faults.joinToString("  ") { it.code },
                    style = ReadoutSmall.copy(fontSize = 13.sp),
                    color = IngweTextSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text("Review", style = LabelStyle.copy(fontSize = 13.sp), color = IngweAccent)
            }
        }

        // ── Demo throttle ────────────────────────────────────────────────────
        AnimatedVisibility(visible = showDemoControls) {
            DemoControls(
                throttle = throttle,
                onThrottle = { autoSweep = false; throttle = it },
                autoSweep = autoSweep,
                onAutoSweep = { autoSweep = it },
            )
        }

        // ── Body ─────────────────────────────────────────────────────────────
        // Only the visible page is composed (plus its neighbour mid-swipe), so hidden
        // pages cost nothing.
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            pageSpacing = 24.dp,
        ) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (ClusterView.entries[page]) {
                    ClusterView.CLUSTER -> ClusterBody(car, boostUnit, peakBoostKPa)
                    ClusterView.DRIVELINE -> DrivelineBody(car)
                    ClusterView.OFFROAD -> OffRoadBody(car)
                }
            }
        }

        // ── Footer ───────────────────────────────────────────────────────────
        HorizontalDivider(color = IngweHairline)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FooterStat("Fuel", "%.1f L/h".format(car.fuelRateLh))
            FooterStat("Peak boost", "${formatBoost(boostFromKPa(peakBoostKPa, boostUnit), boostUnit)} ${boostUnit.label}")
            Text(
                "Reset peak",
                style = LabelStyle,
                color = IngweTextTertiary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { peakBoostKPa = car.boostKPa }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            if (showDemoControls) FrameRateStat() else FooterStat("Polling", "10.4 Hz")
        }
    }
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Views
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
private fun ClusterBody(car: SimulatedVehicle, unit: BoostUnit, peakKPa: Float) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TachometerGauge(rpm = car.rpm, size = 224.dp)
            BoostGauge(boostKPa = car.boostKPa, peakKPa = peakKPa, unit = unit, size = 256.dp, targetKPa = car.targetBoostKPa)
            SpeedometerGauge(speedKmH = car.speedKmH, size = 224.dp)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                LinearMeter("Exhaust gas, pre-turbo", car.egtC, "°C", 200f, 900f, cautionAt = 720f, alertAt = 780f)
                LinearMeter("Charge air", car.chargeAirC, "°C", 0f, 90f, cautionAt = 55f, alertAt = 70f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                VgtVaneGauge(positionPct = car.vgtPct)
                CoolantGauge(tempC = car.coolantC)
                OilTempGauge(tempC = car.oilC)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                LinearMeter("Engine torque", car.torqueNm, "Nm", 0f, 700f, cautionAt = 620f)
                LinearMeter("Battery", car.batteryV, "V", 10f, 16f, coolBelow = 12.4f, format = "%.1f")
            }
        }
    }
}

@Composable
private fun DrivelineBody(car: SimulatedVehicle) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Sport-style tacho: rpm with the current gear in the hub (amber; caution when the converter slips)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            TachometerGauge(
                rpm = car.rpm,
                size = 330.dp,
                gear = car.gear,
                gearColor = if (car.lockup == LockupState.SLIP) IngweCaution else IngweAccent,
            )
        }
        Column(Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TransmissionPanel(
                engineLoadPct = car.engineLoadPct,
                lockup = car.lockup,
                atfTempC = car.atfTempC,
                engineTorqueNm = car.torqueNm,
                modifier = Modifier.fillMaxWidth(),
            )
            Panel(Modifier.fillMaxWidth(), title = "Engine temperatures") {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    LinearMeter("Coolant", car.coolantC, "°C", 50f, 130f, Modifier.weight(1f), coolBelow = 80f, cautionAt = 105f, alertAt = 115f)
                    LinearMeter("Oil", car.oilC, "°C", 50f, 150f, Modifier.weight(1f), coolBelow = 85f, cautionAt = 125f, alertAt = 138f)
                }
            }
        }
    }
}

@Composable
private fun OffRoadBody(car: SimulatedVehicle) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Clinometer(ClinometerKind.ROLL, car.rollDeg, Modifier.fillMaxWidth())
            Clinometer(ClinometerKind.PITCH, car.pitchDeg, Modifier.fillMaxWidth())
        }
        OffRoadPanel(
            steeringAngleDeg = car.steeringDeg,
            wheelSlip = car.wheelSlip,
            rearDiff = car.rearDiff,
            rearTorquePct = car.rearTorquePct,
            modifier = Modifier.weight(1.35f),
        )
    }
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Header / footer pieces
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
private fun ViewTabs(selected: ClusterView, onSelect: (ClusterView) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ClusterView.entries.forEach { tab ->
            val isSelected = tab == selected
            Column(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    tab.label,
                    style = LabelStyle.copy(fontSize = 14.sp),
                    color = if (isSelected) IngweTextPrimary else IngweTextTertiary,
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .width(18.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (isSelected) IngweAccent else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun HeaderLink(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = LabelStyle.copy(fontSize = 13.sp),
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun FooterStat(label: String, value: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, style = LabelStyle, color = IngweTextTertiary)
        Spacer(Modifier.width(8.dp))
        Text(value, style = ReadoutSmall.copy(fontSize = 13.sp), color = IngweTextSecondary)
    }
}

/**
 * Live frame rate and worst frame time over the last second. Only shown in demo
 * mode: measuring keeps the frame clock ticking, which costs a little power.
 */
@Composable
private fun FrameRateStat() {
    var fps by remember { mutableIntStateOf(0) }
    var worstMs by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var last = 0L
        var windowStart = 0L
        var frames = 0
        var worstNs = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    frames++
                    worstNs = maxOf(worstNs, now - last)
                }
                last = now
                if (windowStart == 0L) windowStart = now
                if (now - windowStart >= 1_000_000_000L) {
                    fps = frames
                    worstMs = (worstNs / 1_000_000L).toInt()
                    frames = 0
                    worstNs = 0L
                    windowStart = now
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text("Frame rate", style = LabelStyle, color = IngweTextTertiary)
        Spacer(Modifier.width(8.dp))
        Text(
            "$fps fps · worst $worstMs ms",
            style = ReadoutSmall.copy(fontSize = 13.sp),
            color = when {
                fps >= 50 -> IngweTeal
                fps >= 30 -> IngweCaution
                else -> IngweAlert
            },
        )
    }
}

@Composable
private fun DemoControls(
    throttle: Float,
    onThrottle: (Float) -> Unit,
    autoSweep: Boolean,
    onAutoSweep: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(IngweSurface)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Throttle", style = LabelStyle, color = IngweTextSecondary)
        Spacer(Modifier.width(10.dp))
        Text(
            "%3d%%".format((throttle * 100).toInt()),
            style = ReadoutSmall,
            color = IngweTextPrimary,
            modifier = Modifier.width(44.dp),
        )
        Slider(
            value = throttle,
            onValueChange = onThrottle,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = IngweTextPrimary,
                activeTrackColor = IngweAccent,
                inactiveTrackColor = IngweHairline,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        SegmentedControl(
            options = listOf(0.05f to "Idle", 0.40f to "Cruise", 0.75f to "Boost", 1.0f to "Full"),
            selected = if (autoSweep) -1f else listOf(0.05f, 0.40f, 0.75f, 1.0f).firstOrNull { kotlin.math.abs(it - throttle) < 0.02f } ?: -1f,
            onSelect = onThrottle,
        )
        Spacer(Modifier.width(8.dp))
        SegmentedControl(
            options = listOf(true to "Sweep"),
            selected = autoSweep,
            onSelect = { onAutoSweep(!autoSweep) },
        )
    }
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Simulation
 * ──────────────────────────────────────────────────────────────────────────── */

/** Plausible vehicle state for a given throttle position (0 = idle, 1 = full load). */
internal data class SimulatedVehicle(
    val rpm: Float,
    val boostKPa: Float,
    val targetBoostKPa: Float,
    val engineLoadPct: Float,
    val speedKmH: Float,
    val coolantC: Float,
    val oilC: Float,
    val batteryV: Float,
    val fuelRateLh: Float,
    val egtC: Float,
    val chargeAirC: Float,
    val vgtPct: Float,
    val torqueNm: Float,
    val gear: String,
    val lockup: LockupState,
    val atfTempC: Float,
    val steeringDeg: Float,
    val pitchDeg: Float,
    val rollDeg: Float,
    val wheelSlip: WheelSlip,
    val rearDiff: DiffState,
    val rearTorquePct: Float,
) {
    companion object {
        private val GEAR_UPSHIFT_KMH = floatArrayOf(15f, 32f, 50f, 70f, 92f, 112f, 130f)

        fun at(throttle: Float): SimulatedVehicle {
            val t = throttle.coerceIn(0f, 1f)
            val speed = t * 135f
            val gearIndex = GEAR_UPSHIFT_KMH.indexOfFirst { speed < it }.let { if (it == -1) 8 else it + 1 }
            // Rear wheels slip more under load; the Torsen centre diff biases torque
            // away from the slipping axle (nominal 40:60). Rear locker engages at heavy load.
            val slip = t * 18f
            val wheels = WheelSlip(fl = slip * 0.45f, fr = slip * 0.6f, rl = slip * 0.95f, rr = slip * 1.15f)
            val rearSlipBias = (wheels.rl + wheels.rr) / 2f - (wheels.fl + wheels.fr) / 2f
            return SimulatedVehicle(
                rpm = 800f + t * 3500f,
                boostKPa = 95f + t * 165f,
                targetBoostKPa = 100f + t * 170f, // ECU request runs slightly ahead of actual
                engineLoadPct = (15f + t * 75f).coerceAtMost(100f),
                speedKmH = speed,
                coolantC = 88f + t * 8f,
                oilC = 92f + t * 14f,
                batteryV = 14.2f - t * 0.3f,
                fuelRateLh = 1.2f + t * 14.5f,
                egtC = 310f + t * 470f,
                chargeAirC = 30f + t * 28f,
                vgtPct = 35f + t * 55f,
                torqueNm = 110f + t * 470f,
                gear = "D$gearIndex",
                // Converter unlocks under heavy load (the old `speed < 50` guard made this unreachable).
                lockup = if (t > 0.9f) LockupState.SLIP else LockupState.LOCKED,
                atfTempC = 82f + t * 14f,
                steeringDeg = t * 30f - 15f,
                pitchDeg = t * 18f - 4f,
                rollDeg = t * 14f - 3f,
                wheelSlip = wheels,
                rearDiff = if (t > 0.8f) DiffState.LOCKED else DiffState.OPEN,
                rearTorquePct = (60f - rearSlipBias * 1.5f).coerceIn(35f, 65f),
            )
        }
    }
}
