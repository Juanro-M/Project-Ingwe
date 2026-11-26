package io.ingwe.dash.data.poll

import io.ingwe.dash.data.elm.ElmDriver
import io.ingwe.dash.data.obd.Formula
import io.ingwe.dash.data.obd.PidLengths
import io.ingwe.dash.data.signals.SignalDefinition
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** One poll cycle's result: latest value per signal id, plus link health. */
data class PollSnapshot(
    val values: Map<String, Float>,
    val cycleHz: Float,
    val liveSignals: Int,
    val totalSignals: Int,
)

/**
 * Decides what to ask the ECU for, every cycle, so the fast gauges stay smooth.
 *
 * Each cycle:
 *  1. All FAST signals (rpm, boost, speed, torque…), batched 6 PIDs per request.
 *  2. ONE chunk of SLOW signals (temperatures, fuel level, baro…), rotating, so
 *     every slow signal is refreshed every few cycles without slowing the fast ones.
 *  3. Every [UDS_EVERY] cycles, ONE manufacturer (service 0x22) signal, rotating —
 *     these need a header switch to another ECU, which costs extra round trips.
 *
 * Signals the ECU doesn't support are dropped at start (from the support bitmaps);
 * signals that keep returning nothing are "parked" for a while instead of wasting
 * bus time every cycle.
 */
class PollScheduler(
    private val driver: ElmDriver,
    definitions: List<SignalDefinition>,
    private val clock: () -> Long = System::nanoTime,
) {
    private class Live(val def: SignalDefinition, val formula: Formula, val pid: Int) {
        var failures = 0
        var parkedUntilCycle = 0L
        val service = def.service.toInt(16)
    }

    private val all: List<Live> = definitions.mapNotNull { def ->
        val formula = runCatching { Formula.compile(def.formula) }.getOrNull() ?: return@mapNotNull null
        val pid = def.pid.toIntOrNull(16) ?: return@mapNotNull null
        Live(def, formula, pid)
    }

    private val values = HashMap<String, Float>(all.size * 2)
    private var cycle = 0L

    suspend fun run(onSnapshot: (PollSnapshot) -> Unit) {
        val supported = runCatching { driver.supportedPids() }.getOrDefault(emptySet())
        val mode01 = all.filter { it.service == 0x01 && (supported.isEmpty() || it.pid in supported) }
        val fast = mode01.filter { it.def.fastHz }
        val slow = mode01.filterNot { it.def.fastHz }
        val uds = all.filter { it.service == 0x22 }
        val active = fast.size + slow.size + uds.size

        val fastBatches = batches(fast)
        val slowBatches = batches(slow)
        var slowIndex = 0
        var udsIndex = 0
        var hz = 0f

        while (currentCoroutineContext().isActive) {
            val t0 = clock()

            for (batch in fastBatches) pollBatch(batch)
            if (slowBatches.isNotEmpty()) {
                pollBatch(slowBatches[slowIndex % slowBatches.size])
                slowIndex++
            }
            if (uds.isNotEmpty() && cycle % UDS_EVERY == 0L) {
                pollDid(uds[udsIndex % uds.size])
                udsIndex++
            }
            cycle++

            // Bus-load cap: never more than ~12 cycles a second, however fast the adapter is.
            // Keeps diagnostic traffic a tiny fraction of the CAN bus and leaves the ECU idle time.
            val busyMs = (clock() - t0) / 1_000_000L
            if (busyMs < MIN_CYCLE_MS) delay(MIN_CYCLE_MS - busyMs)

            val seconds = (clock() - t0).coerceAtLeast(1L) / 1e9f
            hz = if (hz == 0f) 1f / seconds else hz * 0.8f + (1f / seconds) * 0.2f // smoothed
            val parked = (fast + slow + uds).count { it.parkedUntilCycle > cycle }
            onSnapshot(PollSnapshot(HashMap(values), hz, active - parked, all.size))
        }
    }

    /** Groups signals into requests of up to 6 PIDs; PIDs of unknown length go alone. */
    private fun batches(signals: List<Live>): List<List<Live>> {
        val (batchable, alone) = signals.partition { PidLengths.isKnown(it.pid) }
        return batchable.chunked(ElmDriver.MAX_PIDS_PER_REQUEST) + alone.map { listOf(it) }
    }

    private suspend fun pollBatch(batch: List<Live>) {
        val due = batch.filter { it.parkedUntilCycle <= cycle }
        if (due.isEmpty()) return
        val replies = try {
            driver.readPids(due.map { it.pid }.distinct())
        } catch (e: TimeoutCancellationException) {
            emptyMap()
        }
        for (s in due) record(s, replies[s.pid]?.let { s.formula.evaluate(it) })
    }

    private suspend fun pollDid(s: Live) {
        if (s.parkedUntilCycle > cycle) return
        val bytes = try {
            driver.readDid(s.def.ecu, s.pid)
        } catch (e: TimeoutCancellationException) {
            null
        }
        record(s, bytes?.let { s.formula.evaluate(it) })
    }

    private fun record(s: Live, value: Double?) {
        if (value != null) {
            s.failures = 0
            values[s.def.id] = value.toFloat()
        } else if (++s.failures >= MAX_FAILURES) {
            s.failures = 0
            s.parkedUntilCycle = cycle + PARK_CYCLES
            values.remove(s.def.id)
        }
    }

    companion object {
        const val UDS_EVERY = 8L
        const val MIN_CYCLE_MS = 80L
        const val MAX_FAILURES = 3
        const val PARK_CYCLES = 300L // ~30 s at 10 Hz, then try again
    }
}
