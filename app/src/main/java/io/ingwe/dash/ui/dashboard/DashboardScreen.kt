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
import io.ingwe.dash.data.vehicle.DataSource
import io.ingwe.dash.data.vehicle.IgnitionState
import io.ingwe.dash.data.vehicle.LockupState
import io.ingwe.dash.data.vehicle.VehicleRepository
import io.ingwe.dash.data.vehicle.VehicleState
import io.ingwe.dash.ui.components.gauges.*
import io.ingwe.dash.ui.dtc.FaultStore
import io.ingwe.dash.ui.navigation.Screen
import io.ingwe.dash.ui.pages.AirPage
import io.ingwe.dash.ui.pages.EconomyPage
import io.ingwe.dash.ui.pages.EnginePage
import io.ingwe.dash.ui.theme.*
import kotlinx.coroutines.launch

private enum class ClusterView(val label: String) {
    CLUSTER("Cluster"),
    DRIVELINE("Driveline"),
    OFFROAD("Off-road"),
    ECONOMY("Economy"),
    AIR("Air"),
    ENGINE("Engine"),
}

/**
 * Main instrument cluster for the VW Amarok 3.0 V6 TDI.
 *
 * Pages (swipe or tap the tabs):
 *   Cluster   — tacho, boost (+ target), speed; VGT / coolant / oil sub-dials; EGT, charge air, torque, battery
 *   Driveline — tacho with gear in the hub; ZF 8HP load, torque, lockup, ATF; coolant & oil
 *   Off-road  — roll and pitch clinometers; 4Motion steering, per-wheel slip, diff state, torque split
 *   Economy   — instant consumption, 30 s history, fuel tank & range, trip computer
 *   Air       — animated air path, altitude, outside air, airflow health
 *   Engine    — V6 cylinders with glow plugs & injector corrections, rail, EGR
 *
 * All values come from [VehicleRepository.state]; the source (demo / mock ELM /
 * adapter) is chosen in the demo bar.
 */
@Composable
fun DashboardScreen(navController: NavController, faultStore: FaultStore, repo: VehicleRepository) {
    var boostUnit by rememberSaveable { mutableStateOf(BoostUnit.BAR) }
    val pager = rememberPagerState(pageCount = { ClusterView.entries.size })
    val view = ClusterView.entries[pager.currentPage]
    val scope = rememberCoroutineScope()

    var showDemoControls by rememberSaveable { mutableStateOf(false) }
    var autoSweep by remember { mutableStateOf(false) }
    val throttle by repo.throttle.collectAsState()
    val source by repo.source.collectAsState()
    val coldStart by repo.coldStartAtSec.collectAsState()
    val link by repo.link.collectAsState()
    val economy by repo.economy.collectAsState()
    val car by repo.state.collectAsState()
    val key by repo.key.collectAsState()

    // Stress test: drive the throttle idle ↔ full continuously so every gauge moves every frame.
    LaunchedEffect(autoSweep, showDemoControls) {
        if (!autoSweep || !showDemoControls) return@LaunchedEffect
        val a = Animatable(repo.throttle.value)
        while (true) {
            a.animateTo(1f, tween(1600, easing = FastOutSlowInEasing)) { repo.throttle.value = value }
            a.animateTo(0f, tween(1600, easing = FastOutSlowInEasing)) { repo.throttle.value = value }
        }
    }

    // Peak hold is updated as a side effect, never during composition.
    var peakBoostKPa by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    LaunchedEffect(car.boostKPa) {
        if (!car.boostKPa.isNaN() && (peakBoostKPa.isNaN() || car.boostKPa > peakBoostKPa)) peakBoostKPa = car.boostKPa
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
            Column {
                Text("Amarok 3.0 V6 TDI", style = LabelStyle.copy(fontSize = 14.sp), color = IngweTextPrimary)
                Spacer(Modifier.height(2.dp))
                StatusDot(text = link.text, color = if (source == DataSource.DEMO) IngweCaution else if (link.ok) IngweOk else IngweAlert)
            }
            Spacer(Modifier.weight(1f))
            ViewTabs(selected = view, onSelect = { scope.launch { pager.animateScrollToPage(it.ordinal) } })
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SegmentedControl(
                    options = listOf(false to "Dark", true to "Light"),
                    selected = IngweThemeState.isLight,
                    onSelect = { IngweThemeState.isLight = it },
                )
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

        // ── Ignition state ───────────────────────────────────────────────────
        if (car.ignition != IgnitionState.RUNNING) {
            Spacer(Modifier.height(12.dp))
            IgnitionBanner(car)
        }

        // ── Demo bar ─────────────────────────────────────────────────────────
        AnimatedVisibility(visible = showDemoControls) {
            DemoControls(
                source = source,
                onSource = { repo.source.value = it },
                throttle = throttle,
                onThrottle = { autoSweep = false; repo.throttle.value = it },
                autoSweep = autoSweep,
                onAutoSweep = { autoSweep = it },
                coldStart = coldStart != null,
                onColdStart = { on -> repo.coldStartAtSec.value = if (on) System.nanoTime() / 1e9 else null },
                key = key,
                onKey = { repo.key.value = it },
            )
        }

        // ── Body ─────────────────────────────────────────────────────────────
        // Only the visible page (plus its neighbour mid-swipe) is composed, so hidden
        // pages — including their animations — cost nothing.
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            pageSpacing = 24.dp,
        ) { page ->
            Box(Modifier.fillMaxSize().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                when (ClusterView.entries[page]) {
                    ClusterView.CLUSTER -> ClusterBody(car, boostUnit, peakBoostKPa)
                    ClusterView.DRIVELINE -> DrivelineBody(car)
                    ClusterView.OFFROAD -> OffRoadBody(car)
                    ClusterView.ECONOMY -> EconomyPage(car, economy, onResetTrip = repo::resetTrip)
                    ClusterView.AIR -> AirPage(car, boostUnit)
                    ClusterView.ENGINE -> EnginePage(car)
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
            FooterStat("Fuel", "${car.fuelRateLh.fmt("%.1f")} L/h")
            FooterStat(
                "Peak boost",
                if (peakBoostKPa.isNaN()) "–" else "${formatBoost(boostFromKPa(peakBoostKPa, boostUnit, car.baroKPa), boostUnit)} ${boostUnit.label}",
            )
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
            if (showDemoControls) FrameRateStat()
            else FooterStat("Polling", if (source == DataSource.DEMO) "demo 20 Hz" else if (link.hz > 0f) "%.1f Hz".format(link.hz) else "–")
        }
    }
}

/* ─────────────────────────────────────────────────────────────────────────────
 * Views
 * ──────────────────────────────────────────────────────────────────────────── */

@Composable
private fun ClusterBody(car: VehicleState, unit: BoostUnit, peakKPa: Float) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TachometerGauge(rpm = car.rpm, size = 224.dp)
            BoostGauge(
                boostKPa = car.boostKPa,
                peakKPa = if (peakKPa.isNaN()) car.boostKPa else peakKPa,
                unit = unit,
                size = 256.dp,
                targetKPa = car.targetBoostKPa,
                baroKPa = if (car.baroKPa.isNaN()) 100f else car.baroKPa,
            )
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
private fun DrivelineBody(car: VehicleState) {
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
                gear = car.gear ?: "–",
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
private fun OffRoadBody(car: VehicleState) {
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
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        ClusterView.entries.forEach { tab ->
            val isSelected = tab == selected
            Column(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
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
    source: DataSource,
    onSource: (DataSource) -> Unit,
    throttle: Float,
    onThrottle: (Float) -> Unit,
    autoSweep: Boolean,
    onAutoSweep: (Boolean) -> Unit,
    coldStart: Boolean,
    onColdStart: (Boolean) -> Unit,
    key: IgnitionState,
    onKey: (IgnitionState) -> Unit,
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
        SegmentedControl(
            options = DataSource.entries.map { it to it.label },
            selected = source,
            onSelect = onSource,
        )
        Spacer(Modifier.width(16.dp))
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
        SegmentedControl(options = listOf(true to "Sweep"), selected = autoSweep, onSelect = { onAutoSweep(!autoSweep) })
        Spacer(Modifier.width(8.dp))
        SegmentedControl(options = listOf(true to "Cold start"), selected = coldStart, onSelect = { onColdStart(!coldStart) })
        Spacer(Modifier.width(8.dp))
        SegmentedControl(
            options = listOf(IgnitionState.OFF to "Off", IgnitionState.ON to "On", IgnitionState.CRANKING to "Crank", IgnitionState.RUNNING to "Run"),
            selected = key,
            onSelect = onKey,
        )
    }
}

/**
 * Explains what's (not) live for the current key position. With the ignition off the
 * engine computer sleeps, so the only reading is battery voltage, measured by the
 * adapter itself at the OBD port.
 */
@Composable
private fun IgnitionBanner(car: VehicleState) {
    val v = car.batteryV
    val batteryColor = when {
        v.isNaN() -> IngweTextTertiary
        car.ignition == IgnitionState.OFF && v < 12.2f -> IngweAlert   // resting battery getting flat
        car.ignition == IgnitionState.OFF && v < 12.4f -> IngweCaution
        car.ignition == IgnitionState.CRANKING && v < 9.6f -> IngweAlert // weak battery under starter load
        else -> IngweTeal
    }
    val detail = when (car.ignition) {
        IgnitionState.OFF -> "Engine computer asleep · only the adapter's battery reading is live"
        IgnitionState.ON -> "Readings live · engine values at rest"
        IgnitionState.CRANKING -> "Starter turning · watch the voltage dip"
        IgnitionState.RUNNING -> ""
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(IngweSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (car.ignition == IgnitionState.CRANKING) IngweCaution else IngweTextTertiary))
        Spacer(Modifier.width(10.dp))
        Text(car.ignition.label, style = LabelStyle.copy(fontSize = 13.sp), color = IngweTextPrimary)
        Spacer(Modifier.width(12.dp))
        Text(detail, style = LabelStyle.copy(fontSize = 13.sp), color = IngweTextSecondary, modifier = Modifier.weight(1f), maxLines = 1)
        Text("Battery  ", style = LabelStyle.copy(fontSize = 13.sp), color = IngweTextSecondary)
        Text("${v.fmt("%.1f")} V", style = ReadoutSmall, color = batteryColor)
    }
}