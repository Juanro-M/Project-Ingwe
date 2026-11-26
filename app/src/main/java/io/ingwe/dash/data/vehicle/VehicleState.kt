package io.ingwe.dash.data.vehicle

import androidx.compose.runtime.Immutable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

enum class LockupState(val label: String) { LOCKED("Locked"), SLIP("Slipping"), OPEN("Open") }
enum class DiffState(val label: String) { OPEN("Open"), LOCKED("Locked") }
enum class GlowPhase { OFF, PRE, AFTER }

/**
 * Key position as far as we can tell from the bus.
 *  OFF          — engine ECU silent. Only the adapter's own voltage reading (ATRV) works.
 *  ON           — ignition on, engine stopped: ECU answers, rpm ≈ 0.
 *  CRANKING     — starter turning: 0 < rpm < 400.
 *  RUNNING      — engine running.
 */
enum class IgnitionState(val label: String) { OFF("Ignition off"), ON("Engine off · ignition on"), CRANKING("Cranking"), RUNNING("Running") }

/** Per-wheel slip %, front-left → rear-right. */
@Immutable
data class WheelSlip(val fl: Float, val fr: Float, val rl: Float, val rr: Float) {
    val max get() = maxOf(fl, fr, rl, rr)
}

/**
 * Everything the dashboard shows, as one immutable snapshot.
 *
 * A Float of NaN — or a null for the structured fields — means "not available
 * from this source"; gauges render it as "–" rather than inventing a value.
 * Marked @Immutable so Compose can skip recomposing anything whose inputs didn't change.
 */
@Immutable
data class VehicleState(
    val rpm: Float = NaN,
    val speedKmH: Float = NaN,
    val boostKPa: Float = NaN,
    val targetBoostKPa: Float = NaN,
    val engineLoadPct: Float = NaN,
    val torqueNm: Float = NaN,
    val coolantC: Float = NaN,
    val oilC: Float = NaN,
    val batteryV: Float = NaN,
    val fuelRateLh: Float = NaN,
    val fuelLevelPct: Float = NaN,
    val egtC: Float = NaN,
    val chargeAirC: Float = NaN,
    val intakeC: Float = NaN,
    val vgtPct: Float = NaN,
    val mafGs: Float = NaN,
    val mafExpectedGs: Float = NaN,
    val ambientC: Float = NaN,
    val baroKPa: Float = NaN,
    val railBar: Float = NaN,
    /** ECU's requested rail pressure (not a standard PID; VW UDS, not yet mapped). */
    val railTargetBar: Float = NaN,
    val egrPct: Float = NaN,
    val atfTempC: Float = NaN,
    val gear: String? = null,
    val lockup: LockupState? = null,
    val steeringDeg: Float = NaN,
    val pitchDeg: Float = NaN,
    val rollDeg: Float = NaN,
    val wheelSlip: WheelSlip? = null,
    val rearDiff: DiffState? = null,
    val rearTorquePct: Float = NaN,
    /** mg/stroke correction per cylinder 1…6 (VW UDS, not yet mapped). */
    val injectorCorrections: List<Float>? = null,
    /** Ω per glow plug 1…6; null entry = open circuit (VW UDS, not yet mapped). */
    val glowOhms: List<Float?>? = null,
    val glowPhase: GlowPhase = GlowPhase.OFF,
    val glowSecondsLeft: Float = 0f,
    val ignition: IgnitionState = IgnitionState.RUNNING,
    /** Odometer from OBD-II PID A6 when the ECU supports it (km). */
    val odometerKm: Float = NaN,
) {
    /** Altitude from the barometer (international standard atmosphere). */
    val altitudeM: Float get() = if (baroKPa.isNaN()) NaN else (44330.0 * (1 - (baroKPa / 101.325).pow(0.1903))).toFloat()

    /** Air density relative to sea level at 15 °C, in %. */
    val airDensityPct: Float get() =
        if (baroKPa.isNaN() || ambientC.isNaN()) NaN else (baroKPa / 101.325f) * (288.15f / (273.15f + ambientC)) * 100f

    /** Compressor outlet temperature, estimated from the pressure ratio (no sensor on most engines). */
    val compressorOutC: Float get() {
        if (boostKPa.isNaN() || baroKPa.isNaN() || ambientC.isNaN()) return NaN
        val t1 = ambientC + 273.15f
        val ratio = max(1f, boostKPa / baroKPa)
        return (t1 + t1 * (ratio.toDouble().pow(0.2857).toFloat() - 1f) / 0.70f) - 273.15f // 70 % compressor efficiency
    }

    /** Instant consumption in L/100 km, or NaN when (nearly) stationary. */
    val litresPer100: Float get() = if (speedKmH.isNaN() || fuelRateLh.isNaN() || speedKmH < 5f) NaN else fuelRateLh / speedKmH * 100f

    companion object {
        private const val NaN = Float.NaN
        const val DEMO_ODOMETER_KM = 134_000f
        private val GEAR_UPSHIFT_KMH = floatArrayOf(15f, 32f, 50f, 70f, 92f, 112f, 130f)

        /**
         * Plausible full vehicle state for a throttle position (0 = idle, 1 = full load).
         * [nowSec] / [coldStartAtSec] drive the demo glow-plug sequence.
         */
        /** Ignition off: the ECU is asleep, only the adapter's battery reading is available. */
        fun ignitionOff(batteryV: Float) = VehicleState(batteryV = batteryV, ignition = IgnitionState.OFF)

        /** Demo for a key position; RUNNING uses the throttle simulation. */
        fun simulated(throttle: Float, nowSec: Double, coldStartAtSec: Double?, key: IgnitionState): VehicleState = when (key) {
            IgnitionState.OFF -> ignitionOff(12.6f).copy(odometerKm = DEMO_ODOMETER_KM)
            IgnitionState.ON, IgnitionState.CRANKING -> simulated(0f, nowSec, coldStartAtSec).copy(
                ignition = key,
                rpm = if (key == IgnitionState.CRANKING) 220f else 0f,
                speedKmH = 0f, boostKPa = 83.4f, targetBoostKPa = 83.4f, engineLoadPct = 0f, torqueNm = 0f,
                fuelRateLh = 0f, mafGs = 0f, mafExpectedGs = 0f,
                railBar = if (key == IgnitionState.CRANKING) 240f else 0f,
                railTargetBar = if (key == IgnitionState.CRANKING) 300f else 0f,
                batteryV = if (key == IgnitionState.CRANKING) 10.4f else 12.3f,
                egtC = 40f, vgtPct = 0f, egrPct = 0f, gear = "P",
                wheelSlip = WheelSlip(0f, 0f, 0f, 0f),
            )
            IgnitionState.RUNNING -> simulated(throttle, nowSec, coldStartAtSec)
        }

        fun simulated(throttle: Float, nowSec: Double = 0.0, coldStartAtSec: Double? = null): VehicleState {
            val t = throttle.coerceIn(0f, 1f)
            val speed = t * 135f
            val gearIndex = GEAR_UPSHIFT_KMH.indexOfFirst { speed < it }.let { if (it == -1) 8 else it + 1 }
            val slip = t * 18f
            val wheels = WheelSlip(fl = slip * 0.45f, fr = slip * 0.6f, rl = slip * 0.95f, rr = slip * 1.15f)
            val rearSlipBias = (wheels.rl + wheels.rr) / 2f - (wheels.fl + wheels.fr) / 2f
            val sinceCold = coldStartAtSec?.let { nowSec - it } ?: Double.MAX_VALUE
            val phase = when {
                sinceCold < 6 -> GlowPhase.PRE
                sinceCold < 186 -> GlowPhase.AFTER
                else -> GlowPhase.OFF
            }
            val maf = 9f + t * 215f
            val cac = 30f + t * 28f
            return VehicleState(
                rpm = 800f + t * 3500f,
                speedKmH = speed,
                boostKPa = 83.4f - 5f + t * 165f,    // absolute; boost = this − baro
                targetBoostKPa = 83.4f + t * 170f,
                engineLoadPct = min(100f, 15f + t * 75f),
                torqueNm = 110f + t * 470f,
                coolantC = if (phase == GlowPhase.OFF) 88f + t * 8f else min(60f, 12f + sinceCold.toFloat() * 0.25f),
                oilC = 92f + t * 14f,
                batteryV = 14.2f - t * 0.3f,
                fuelRateLh = 1.2f + t * 14.5f,
                fuelLevelPct = 62f,
                egtC = 310f + t * 470f,
                chargeAirC = cac,
                intakeC = cac + 2f,
                vgtPct = 35f + t * 55f,
                mafGs = maf,
                mafExpectedGs = maf * (1.02f + t * 0.03f),
                ambientC = 24f,
                baroKPa = 83.4f,
                railBar = 290f + t * 1480f,
                railTargetBar = (290f + t * 1480f) * 1.012f,
                egrPct = max(0f, 45f - t * 55f),
                atfTempC = 82f + t * 14f,
                gear = "D$gearIndex",
                lockup = if (t > 0.9f) LockupState.SLIP else LockupState.LOCKED,
                steeringDeg = t * 30f - 15f,
                pitchDeg = t * 18f - 4f,
                rollDeg = t * 14f - 3f,
                wheelSlip = wheels,
                rearDiff = if (t > 0.8f) DiffState.LOCKED else DiffState.OPEN,
                rearTorquePct = (60f - rearSlipBias * 1.5f).coerceIn(35f, 65f),
                injectorCorrections = listOf(0.4f, -0.8f, 1.1f, -0.3f, 2.3f, -0.6f)
                    .mapIndexed { i, v -> v + (sin(nowSec * 0.7 + i * 1.9) * 0.15).toFloat() },
                glowOhms = listOf(null, 0.62f, 0.58f, 0.61f, 0.63f, 0.60f), // cyl 1 open (demo fault P0671)
                odometerKm = DEMO_ODOMETER_KM,
                glowPhase = phase,
                glowSecondsLeft = when (phase) {
                    GlowPhase.PRE -> (6 - sinceCold).toFloat()
                    GlowPhase.AFTER -> (186 - sinceCold).toFloat()
                    GlowPhase.OFF -> 0f
                },
            )
        }

        /** Map polled signal values (by signals.json id) onto the dashboard model. */
        fun fromSignals(v: Map<String, Float>): VehicleState {
            fun f(id: String) = v[id] ?: NaN
            val gearNum = v["trans_gear"]?.toInt()
            val rpm = v["rpm"]
            return VehicleState(
                ignition = when {
                    rpm == null -> IgnitionState.ON
                    rpm < 50f -> IgnitionState.ON
                    rpm < 400f -> IgnitionState.CRANKING
                    else -> IgnitionState.RUNNING
                },
                rpm = f("rpm"),
                speedKmH = f("vehicle_speed"),
                boostKPa = f("boost_actual"),
                targetBoostKPa = f("boost_target"),
                engineLoadPct = f("engine_load"),
                torqueNm = f("engine_torque"),
                coolantC = f("coolant_temp"),
                oilC = f("oil_temp"),
                batteryV = f("battery_voltage"),
                fuelRateLh = f("fuel_rate"),
                fuelLevelPct = f("fuel_level"),
                egtC = f("egt_pre_turbo"),
                chargeAirC = f("charge_air_temp"),
                intakeC = f("intake_air_temp"),
                vgtPct = f("turbo_vgt"),
                mafGs = f("maf"),
                ambientC = f("ambient_temp"),
                baroKPa = f("baro"),
                railBar = f("fuel_rail"),
                egrPct = f("egr_commanded"),
                atfTempC = f("trans_fluid_temp"),
                gear = gearNum?.let { "D$it" },
                lockup = v["trans_lockup"]?.toInt()?.let { LockupState.entries.getOrNull(it) },
                steeringDeg = f("steering_angle"),
                pitchDeg = f("pitch_angle"),
                rollDeg = f("roll_angle"),
                odometerKm = f("odometer"),
            )
        }
    }
}
