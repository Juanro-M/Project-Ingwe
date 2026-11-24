package io.ingwe.dash.data.vehicle

import androidx.compose.runtime.Immutable
import io.ingwe.dash.data.elm.ElmDriver
import io.ingwe.dash.data.poll.PollScheduler
import io.ingwe.dash.data.signals.SignalDefinition
import io.ingwe.dash.data.transport.MockTransport
import io.ingwe.dash.data.transport.Transport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class DataSource(val label: String) { DEMO("Demo"), MOCK_ELM("Mock ELM"), ADAPTER("Adapter") }

/** What the header/footer show about the data link. */
data class LinkStatus(val text: String, val ok: Boolean, val hz: Float = 0f)

/** Trip computer + 30-second consumption history, updated twice a second. */
@Immutable
data class Economy(
    val distanceKm: Float = 0f,
    val seconds: Float = 0f,
    val fuelUsedL: Float = 0f,
    val idleSeconds: Float = 0f,
    /** Last 60 samples (30 s) of L/100 km; NaN = idling / no data. Oldest first. */
    val history: List<Float> = emptyList(),
) {
    val averageL100: Float get() = if (distanceKm > 0.1f) fuelUsedL / distanceKm * 100f else Float.NaN
    val averageKmH: Float get() = if (seconds > 1f) distanceKm / (seconds / 3600f) else Float.NaN
}

/**
 * Single source of truth for live vehicle data.
 *
 * The UI only ever collects [state] — a StateFlow, which is *conflated*: if the UI
 * is busy, it skips straight to the newest snapshot instead of working through a
 * backlog. That keeps the screen current even when a frame is slow.
 *
 * [run] is started by the UI while the app is visible and cancelled when it isn't,
 * so nothing polls the car (or burns CPU) in the background.
 */
class VehicleRepository(
    private val loadSignals: suspend () -> List<SignalDefinition>,
    private val adapterTransport: () -> Transport,
) {
    val source = MutableStateFlow(DataSource.DEMO)
    val throttle = MutableStateFlow(0.55f)
    val coldStartAtSec = MutableStateFlow<Double?>(null)
    /** Demo key position (Demo and Mock ELM sources). */
    val key = MutableStateFlow(IgnitionState.RUNNING)

    private val _state = MutableStateFlow(VehicleState.simulated(0.55f))
    val state: StateFlow<VehicleState> = _state.asStateFlow()

    private val _link = MutableStateFlow(LinkStatus("Demo data", ok = true))
    val link: StateFlow<LinkStatus> = _link.asStateFlow()

    private val _economy = MutableStateFlow(Economy(distanceKm = 182.4f, seconds = 9660f, fuelUsedL = 19.6f, idleSeconds = 1260f))
    val economy: StateFlow<Economy> = _economy.asStateFlow()

    fun resetTrip() { _economy.value = Economy() }

    suspend fun run() = coroutineScope {
        launch { trackEconomy() }
        source.collectLatest { src ->
            when (src) {
                DataSource.DEMO -> runDemo()
                DataSource.MOCK_ELM -> runElm(
                    MockTransport(
                        responseDelayMs = 18,
                        liveData = { pid -> MockEcu.encode(demoState(), pid) },
                        ecuAwake = { key.value != IgnitionState.OFF },
                        batteryVolts = { demoState().batteryV.takeIf { !it.isNaN() } ?: 12.6f },
                    ),
                    label = "Mock ELM",
                )
                DataSource.ADAPTER -> runElm(adapterTransport(), label = "Adapter")
            }
        }
    }

    private fun nowSec() = System.nanoTime() / 1e9

    private fun demoState() = VehicleState.simulated(throttle.value, nowSec(), coldStartAtSec.value, key.value)

    /** Demo: 20 updates a second straight from the simulation (the gauges' springs smooth the rest). */
    private suspend fun runDemo() {
        _link.value = LinkStatus("Demo data · ECU 7E0", ok = true)
        while (true) {
            _state.value = demoState()
            delay(50)
        }
    }

    /** ELM source: connect, then poll forever; on failure, show why and retry with backoff. */
    private suspend fun runElm(transport: Transport, label: String) {
        var backoffMs = 1_000L
        while (true) {
            val driver = ElmDriver(transport)
            try {
                _link.value = LinkStatus("$label · connecting…", ok = false)
                driver.connect()
                backoffMs = 1_000L
                while (true) {
                    if (!driver.ecuAwake()) {
                        // Ignition off: the ECU is asleep. Read only the adapter's battery voltage,
                        // slowly, and check every 2 s whether the key has been turned.
                        val volts = driver.readVoltage() ?: Float.NaN
                        _state.value = VehicleState.ignitionOff(volts)
                        _link.value = LinkStatus("$label · ignition off", ok = true)
                        delay(2_000)
                        continue
                    }
                    // Ignition on: rediscover supported PIDs (they can differ by key state) and poll.
                    var silentCycles = 0
                    try {
                        PollScheduler(driver, loadSignals()).run { snap ->
                            silentCycles = if (snap.values.isEmpty()) silentCycles + 1 else 0
                            if (silentCycles >= 5) throw EcuAsleep()
                            _state.value = VehicleState.fromSignals(snap.values).withDemoExtras(label)
                            _link.value = LinkStatus("$label · ${snap.liveSignals}/${snap.totalSignals} signals", ok = true, hz = snap.cycleHz)
                        }
                    } catch (e: EcuAsleep) {
                        // Key turned off mid-drive — fall back to the ignition-off loop.
                    }
                }
            } catch (e: CancellationException) {
                runCatching { driver.disconnect() }
                throw e
            } catch (e: Exception) {
                _link.value = LinkStatus("$label · ${e.message ?: "error"}", ok = false)
                runCatching { driver.disconnect() }
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(15_000L)
            }
        }
    }

    /**
     * Mock ELM only reproduces the engine ECU. Off-road, gearbox and glow/injector
     * data come from other modules, so in mock mode we fill them from the demo to
     * keep those pages alive. On the real adapter they stay empty until mapped.
     */
    private fun VehicleState.withDemoExtras(label: String): VehicleState {
        if (label != "Mock ELM" || ignition != IgnitionState.RUNNING) return this
        val d = demoState()
        return copy(
            gear = d.gear, lockup = d.lockup, atfTempC = d.atfTempC,
            steeringDeg = d.steeringDeg, pitchDeg = d.pitchDeg, rollDeg = d.rollDeg,
            wheelSlip = d.wheelSlip, rearDiff = d.rearDiff, rearTorquePct = d.rearTorquePct,
            mafExpectedGs = d.mafExpectedGs, railTargetBar = d.railTargetBar, injectorCorrections = d.injectorCorrections,
            glowOhms = d.glowOhms, glowPhase = d.glowPhase, glowSecondsLeft = d.glowSecondsLeft,
        )
    }

    /** Integrates distance and fuel twice a second, and keeps the 30 s history. */
    private suspend fun trackEconomy() {
        while (true) {
            delay(500)
            val s = _state.value
            val e = _economy.value
            val dt = 0.5f
            val speed = if (s.speedKmH.isNaN()) 0f else s.speedKmH
            val rate = if (s.fuelRateLh.isNaN()) 0f else s.fuelRateLh
            val history = (e.history + s.litresPer100).takeLast(60)
            _economy.value = e.copy(
                distanceKm = e.distanceKm + speed * dt / 3600f,
                seconds = e.seconds + dt,
                fuelUsedL = e.fuelUsedL + rate * dt / 3600f,
                idleSeconds = e.idleSeconds + if (speed < 3f) dt else 0f,
                history = history,
            )
        }
    }
}

/** Thrown inside the poll loop when the ECU stops answering (ignition switched off). */
private class EcuAsleep : Exception()
