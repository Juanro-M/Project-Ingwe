package io.ingwe.dash.data.vehicle

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Demo "Sweep": a spirited 22-second back-road loop instead of a robotic idle↔full ramp.
 * Launch, short-shift lift, brake into a bend, part throttle through it, punch out,
 * kickdown overtake, lift, short burst, brake hard — then round again.
 *
 * A tiny vehicle model turns the pedal into speed, picks ZF 8HP gears with a spirited
 * shift map (holds gears with the pedal down) and gives rpm that drops on every upshift.
 */
class DemoDrive {
    data class Sample(val speedKmH: Float, val rpm: Float, val gear: Int)

    private class Key(val at: Float, val throttle: Float, val ramp: Float, val brake: Boolean = false)

    private val script = listOf(
        Key(0.0f, 0.08f, 0.3f), Key(0.8f, 1.00f, 0.25f), Key(3.6f, 0.55f, 0.15f), Key(3.9f, 1.00f, 0.2f),
        Key(6.2f, 0.00f, 0.2f, brake = true), Key(7.6f, 0.30f, 0.5f), Key(9.6f, 0.92f, 0.35f),
        Key(11.8f, 0.45f, 0.6f), Key(13.0f, 1.00f, 0.15f), Key(15.6f, 0.62f, 0.5f),
        Key(16.8f, 0.00f, 0.3f), Key(18.0f, 0.70f, 0.45f), Key(19.4f, 0.00f, 0.3f, brake = true),
    )
    private val loopSec = 22f

    private var v = 0f            // m/s
    private var gear = 1
    private var sinceShift = 1f
    private var keyIndex = -1
    private var held = 0f
    private var keyStart = 0f
    var throttle = 0.08f; private set

    /** Advance by [dt] seconds at loop time [t]; returns the new drive sample. */
    fun step(t: Float, dt: Float): Sample {
        val lt = t % loopSec
        val k = script.indexOfLast { it.at <= lt }.coerceAtLeast(0)
        if (k != keyIndex) { keyIndex = k; held = throttle; keyStart = lt }
        val key = script[k]
        val p = min(1f, (lt - keyStart) / key.ramp)
        val tremor = if (key.throttle > 0.1f) (sin(t * 11.1) * 0.012 + sin(t * 27.0) * 0.008).toFloat() else 0f
        throttle = (held + (key.throttle - held) * ease(p) + tremor).coerceIn(0f, 1f)

        val r = RATIOS[gear - 1]
        val push = throttle * 6.4f * sqrt(r / RATIOS[0]) * (1f - min(1f, v / 62f))
        val drag = 0.00035f * v * v + 0.15f
        v = (v + (push - drag - if (key.brake) 7.5f else 0f) * dt).coerceIn(0f, 48f)

        sinceShift += dt
        val rpmNow = wheelRpm() * r * FINAL
        val up = 1700f + throttle * 2600f
        val down = 1050f + throttle * 1500f
        if (rpmNow > up && gear < 8 && sinceShift > 0.45f) { gear++; sinceShift = 0f }
        else if (rpmNow < down && gear > 1 && sinceShift > 0.3f) { gear--; sinceShift = 0f }

        val conv = if (gear <= 2) 800f + throttle * 1400f else 0f // torque converter slip pulling away
        return Sample(v * 3.6f, max(760f, max(wheelRpm() * RATIOS[gear - 1] * FINAL, conv)), gear)
    }

    private fun wheelRpm() = v / TYRE_M * 60f
    private fun ease(t: Float) = if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2

    private companion object {
        val RATIOS = floatArrayOf(4.71f, 3.14f, 2.11f, 1.67f, 1.29f, 1.0f, 0.84f, 0.67f)
        const val FINAL = 3.7f
        const val TYRE_M = 2.5f
    }
}
