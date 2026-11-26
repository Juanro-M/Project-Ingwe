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
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
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
import io.ingwe.dash.ui.pages.GearboxRow
import io.ingwe.dash.ui.pages.SourceTag
import io.ingwe.dash.data.service.ServiceEntry
import io.ingwe.dash.data.service.ServiceStore
import io.ingwe.dash.ui.service.LogSheet
import io.ingwe.dash.ui.service.ServicePage
import io.ingwe.dash.ui.theme.*
import kotlinx.coroutines.launch

/** Pages in order of how often they're likely to be used (tabs and swipe follow this order). Icons are 24×24 SVG paths. */
private enum class ClusterView(val label: String, val icon: String) {
    CLUSTER("Cluster", "M4 16a8 8 0 1 1 16 0M12 16l4-5"),                                             // everyday driving
    ECONOMY("Economy", "M5 19c0-8 6-13 14-13 0 8-5 13-13 13M5 19l7-7"),                                // fuel, range, trip
    DRIVELINE("Driveline", "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M5.6 18.4l2.1-2.1M16.3 7.7l2.1-2.1"), // gear, ATF, load
    OFFROAD("Off-road", "M3 19l6-10 4 6 2-3 6 7z"),                                                     // off the tar
    AIR("Air", "M3 9h11a3 3 0 1 0-3-3M3 14h15a3 3 0 1 1-3 3"),                                           // altitude, airflow
    ENGINE("Engine", "M4 10h3l2-3h6v3h3v8h-3l-2 2H9l-2-3H4z"),                                            // glow plugs & injectors
    SERVICE("Service", "M14.5 6.5a4 4 0 0 0-5 5L4 17l3 3 5.5-5.5a4 4 0 0 0 5-5l-2.5 2.5-2.5-.5-.5-2.5z"), // maintenance log
}

/**
 * Main instrument cluster for the VW Amarok 3.0 V6 TDI.
 *
 * Pages (swipe or tap the tabs), most-used first:
 *   Cluster   — tacho, boost (+ target), speed; VGT / coolant / oil sub-dials; EGT, charge air, torque, battery
 *   Economy   — instant consumption, 30 s history, fuel tank & range, trip computer
 *   Driveline — tacho with gear in the hub; ZF 8HP load, torque, lockup, ATF; coolant & oil
 *   Off-road  — roll and pitch clinometers; 4Motion steering, per-wheel slip, diff state, torque split
 *   Air       — animated air path, altitude, outside air, airflow health
 *   Engine    — V6 cylinders with glow plugs & injector corrections, rail, EGR
 *
 * All values come from [VehicleRepository.state]; the source (demo / mock ELM /
 * adapter) is chosen in the demo bar.
 */
@Composable
fun DashboardScreen(navController: NavController, faultStore: FaultStore, repo: VehicleRepository, serviceStore: ServiceStore, initialPage: Int = 0) {
    var boostUnit by rememberSaveable { mutableStateOf(BoostUnit.BAR) }
    val pager = rememberPagerState(initialPage = initialPage, pageCount = { ClusterView.entries.size })
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

    // Sweep: a spirited drive loop (launch, shifts, braking, kickdown) so every gauge moves like it would on the road.
    LaunchedEffect(autoSweep, showDemoControls) {
        if (autoSweep && showDemoControls) repo.runSpiritedDrive()
    }

    // Service log sheet (drawn over everything) and the odometer it pre-fills.
    var sheet by remember { mutableStateOf<SheetRequest?>(null) }
    val serviceData by serviceStore.data.collectAsState()
    val odoKm = if (!serviceData.useManualKm && !car.odometerKm.isNaN()) car.odometerKm.toInt() else serviceData.manualKm

    // Peak hold is updated as a side effect, never during composition.
    var peakBoostKPa by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    LaunchedEffect(car.boostKPa) {
        if (!car.boostKPa.isNaN() && (peakBoostKPa.isNaN() || car.boostKPa > peakBoostKPa)) peakBoostKPa = car.boostKPa
    }

    val faults = faultStore.codes

    Box(Modifier.fillMaxSize()) {
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
            TabDock(selected = view, onSelect = { scope.launch { pager.animateScrollToPage(it.ordinal) } })
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
                FaultsButton(count = faults.size, onClick = { navController.navigate(Screen.Dtc.route) })
                HeaderLink("Terminal", IngweTextSecondary) { navController.navigate(Screen.Terminal.route) }
                HeaderLink(
                    text = "Demo",
                    color = if (showDemoControls) IngweAccent else IngweTextSecondary,
                    onClick = { showDemoControls = !showDemoControls },
                )
            }
        }

        // ── Ignition state ───────────────────────────────────────────────────
        if (car.ignition != IgnitionState.RUNNING) {
            Spacer(Modifier.height(12.dp))
            IgnitionBanner(car, asleep = link.asleep, onWake = repo::wake)
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
                    ClusterView.SERVICE -> ServicePage(car.odometerKm, serviceStore, onEdit = { sheet = SheetRequest(it) })
                }
            }
        }

        // ── Footer (trip stats only on the driving pages; no divider line) ────
        if (view != ClusterView.SERVICE) Row(
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

    sheet?.let { req ->
        LogSheet(
            entry = req.entry,
            odometerKm = odoKm,
            onSave = { serviceStore.save(it); sheet = null },
            onDelete = { serviceStore.delete(it); sheet = null },
            onClose = { sheet = null },
        )
    }
    }
}

/** Which entry the log sheet is open for (null entry = a new one). */
private class SheetRequest(val entry: ServiceEntry?)

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
            Panel(Modifier.fillMaxWidth(), title = "Gearbox", trailing = { SourceTag("VW UDS · unconfirmed") }) {
                GearboxRow(car.gear, car.lockup)
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

/**
 * Page tabs as one rounded dock (CarPlay-like, kept quiet): a line icon over each label and a
 * soft capsule that slides under the active page. Icons are built once and cached; the capsule
 * position is animated and read in the layout phase only, so switching pages doesn't recompose the row.
 */
@Composable
private fun TabDock(selected: ClusterView, onSelect: (ClusterView) -> Unit) {
    val icons = remember { ClusterView.entries.associateWith { strokeIcon(it.icon) } }
    val bounds = remember { mutableStateMapOf<ClusterView, Pair<Int, Int>>() } // x, width in px
    val target = bounds[selected]
    val pillX by animateIntAsState(target?.first ?: 0, spring(dampingRatio = 0.8f, stiffness = 500f), label = "pillX")
    val pillW by animateIntAsState(target?.second ?: 0, spring(dampingRatio = 0.8f, stiffness = 500f), label = "pillW")
    val pillColor = IngweSurfaceHigh
    val padPx = with(LocalDensity.current) { 4.dp.roundToPx() }
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(IngweSurface)
            .drawBehind {
                if (pillW > 0) drawRoundRect(pillColor, Offset(pillX.toFloat(), 4.dp.toPx()), Size(pillW.toFloat(), size.height - 8.dp.toPx()), CornerRadius(12.dp.toPx()))
            }
            .padding(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ClusterView.entries.forEach { tab ->
                val isSelected = tab == selected
                Column(
                    Modifier
                        .onPlaced { bounds[tab] = (it.positionInParent().x.toInt() + padPx) to it.size.width }
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(tab) }
                        .widthIn(min = 68.dp)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icons.getValue(tab), contentDescription = null, tint = if (isSelected) IngweAccent else IngweTextTertiary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(tab.label, style = LabelStyle.copy(fontSize = 13.sp), color = if (isSelected) IngweTextPrimary else IngweTextTertiary)
                }
            }
        }
    }
}

/** A 24×24 line icon from SVG path data, stroked like the rest of the UI. */
private fun strokeIcon(path: String): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = addPathNodes(path),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()

/**
 * Faults link. With codes stored it becomes a soft red capsule with a count badge, and a thin
 * light glint passes across it every few seconds. Between glints nothing animates, so the
 * frame clock rests (no constant redraw on the head unit).
 */
@Composable
private fun FaultsButton(count: Int, onClick: () -> Unit) {
    if (count == 0) {
        HeaderLink("Faults", IngweTextSecondary, onClick)
        return
    }
    val glint = remember { Animatable(-1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_500)
            glint.snapTo(-0.4f)
            glint.animateTo(1.3f, tween(1_000, easing = FastOutSlowInEasing))
            glint.snapTo(-1f)
        }
    }
    val alert = IngweAlert
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(alert.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .drawWithContent {
                drawContent()
                val g = glint.value
                if (g > -0.5f) {
                    val w = size.width * 0.3f
                    val x = g * size.width
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent), startX = x, endX = x + w),
                        topLeft = Offset(x, 0f), size = Size(w, size.height),
                    )
                }
            }
            .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Faults", style = LabelStyle.copy(fontSize = 13.sp), color = alert)
        Spacer(Modifier.width(7.dp))
        Box(Modifier.heightIn(min = 18.dp).widthIn(min = 18.dp).clip(RoundedCornerShape(9.dp)).background(alert).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
            Text("$count", style = LabelStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = Color.White)
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
private fun IgnitionBanner(car: VehicleState, asleep: Boolean, onWake: () -> Unit) {
    val v = car.batteryV
    val batteryColor = when {
        v.isNaN() -> IngweTextTertiary
        car.ignition == IgnitionState.OFF && v < 12.2f -> IngweAlert   // resting battery getting flat
        car.ignition == IgnitionState.OFF && v < 12.4f -> IngweCaution
        car.ignition == IgnitionState.CRANKING && v < 9.6f -> IngweAlert // weak battery under starter load
        else -> IngweTeal
    }
    val detail = when (car.ignition) {
        IgnitionState.OFF -> if (asleep) "Adapter asleep to protect the battery · no traffic to the car" else "Engine computer asleep · only the adapter's battery reading is live"
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
        if (asleep) {
            Spacer(Modifier.width(12.dp))
            HeaderLink("Wake adapter", IngweAccent, onWake)
        }
    }
}