package io.ingwe.dash

import io.ingwe.dash.data.elm.ElmDriver
import io.ingwe.dash.data.isotp.IsoTpAssembler
import io.ingwe.dash.data.obd.Formula
import io.ingwe.dash.data.obd.ObdResponseParser
import io.ingwe.dash.data.poll.PollScheduler
import io.ingwe.dash.data.poll.PollSnapshot
import io.ingwe.dash.data.signals.SignalDefinition
import io.ingwe.dash.data.transport.MockTransport
import io.ingwe.dash.data.vehicle.MockEcu
import io.ingwe.dash.data.vehicle.VehicleState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ObdPipelineTest {

    // ── Compiled formulas ────────────────────────────────────────────────────

    @Test fun `compiled formula matches hand calculation`() {
        val f = Formula.compile("((A*256)+B)/4")
        assertEquals(850.0, f.evaluate(byteArrayOf(0x0D, 0x48))!!, 0.001)
        assertEquals(1, f.maxByteIndex)
    }

    @Test fun `compiled formula returns null for short responses`() {
        assertNull(Formula.compile("((B*256)+C)/10-40").evaluate(byteArrayOf(0x01, 0x0D)))
    }

    @Test fun `compiled formula handles unary minus and precedence`() {
        assertEquals(-5.0, Formula.compile("-(2+3)").evaluate(byteArrayOf())!!, 0.001)
        assertEquals(14.0, Formula.compile("2+3*4").evaluate(byteArrayOf())!!, 0.001)
    }

    @Test fun `malformed formula is rejected at compile time`() {
        assertThrows(IllegalArgumentException::class.java) { Formula.compile("(A+1") }
    }

    // ── Frames and batching ──────────────────────────────────────────────────

    @Test fun `assembler reads frames without spaces`() {
        val asm = IsoTpAssembler()
        asm.feed("7E8064100BE3EB811")
        assertArrayEquals(byteArrayOf(0x41, 0x00, 0xBE.toByte(), 0x3E, 0xB8.toByte(), 0x11), asm.payload())
    }

    @Test fun `assembler reads multi-frame without spaces`() {
        val asm = IsoTpAssembler()
        asm.feed("7E8101449020131565")
        // malformed length above must not crash; real frames follow
        asm.reset()
        asm.feed("7E81014490201315657")
        asm.feed("7E82146453231433034")
        asm.feed("7E8224D303030303031")
        assertTrue(asm.isComplete)
        assertEquals(20, asm.payload().size)
    }

    @Test fun `batched mode 01 payload splits by PID length`() {
        val payload = byteArrayOf(0x41, 0x0C, 0x0D, 0x48, 0x0D, 0x00, 0x05, 0x7D)
        val split = ObdResponseParser.splitMode01(payload)
        assertEquals(setOf(0x0C, 0x0D, 0x05), split.keys)
        assertArrayEquals(byteArrayOf(0x0D, 0x48), split[0x0C])
    }

    // ── Driver against the mock adapter ──────────────────────────────────────

    private suspend fun connected(mock: MockTransport = MockTransport(responseDelayMs = 0)): Pair<ElmDriver, MockTransport> {
        val driver = ElmDriver(mock)
        driver.connect()
        return driver to mock
    }

    @Test fun `driver initialises and reads supported PIDs`() = runTest {
        val (driver, _) = connected()
        assertEquals("ELM327 v1.5", driver.adapterVersion)
        val supported = driver.supportedPids()
        assertTrue(0x0C in supported)
        assertTrue(0x78 in supported)
        assertFalse(0x01 in supported)
    }

    @Test fun `six PIDs come back from one round trip, including a multi-frame one`() = runTest {
        val (driver, mock) = connected()
        val before = mock.commandCount
        val values = driver.readPids(listOf(0x0C, 0x0D, 0x05, 0x0B, 0x78, 0x42))
        assertEquals(1, mock.commandCount - before) // one request, not six
        assertEquals(6, values.size)
        assertEquals(314.0, Formula.compile("((B*256)+C)/10-40").evaluate(values[0x78]!!)!!, 0.01)
    }

    @Test fun `header is only switched when the ECU changes`() = runTest {
        val (driver, mock) = connected()
        val before = mock.commandCount
        driver.readPids(listOf(0x0C))
        driver.readPids(listOf(0x0D))
        assertEquals(2, mock.commandCount - before) // no ATSH re-sent between them
    }

    @Test fun `UDS DID read round-trips`() = runTest {
        val (driver, mock) = connected()
        mock.didData[0x11A8] = byteArrayOf(0x7A)
        assertArrayEquals(byteArrayOf(0x7A), driver.readDid("7E1", 0x11A8))
        assertNull(driver.readDid("7E1", 0x1002)) // negative response → null
    }

    @Test fun `ignition off - ECU silent but adapter voltage still readable`() = runTest {
        var awake = false
        val mock = MockTransport(responseDelayMs = 0, ecuAwake = { awake }, batteryVolts = { 12.4f })
        val (driver, _) = connected(mock)
        assertFalse(driver.ecuAwake())
        assertEquals(12.4f, driver.readVoltage()!!, 0.01f)
        awake = true // key turned
        assertTrue(driver.ecuAwake())
    }

    @Test fun `ignition state is derived from rpm`() {
        assertEquals(io.ingwe.dash.data.vehicle.IgnitionState.ON, VehicleState.fromSignals(mapOf("rpm" to 0f)).ignition)
        assertEquals(io.ingwe.dash.data.vehicle.IgnitionState.CRANKING, VehicleState.fromSignals(mapOf("rpm" to 220f)).ignition)
        assertEquals(io.ingwe.dash.data.vehicle.IgnitionState.RUNNING, VehicleState.fromSignals(mapOf("rpm" to 850f)).ignition)
    }

    // ── Scheduler ────────────────────────────────────────────────────────────

    private fun sig(id: String, pid: String, formula: String, fast: Boolean, service: String = "01", ecu: String = "7E0") =
        SignalDefinition(id, id, ecu, service, pid, formula, "", fastHz = fast)

    @Test fun `scheduler polls, decodes, and parks unsupported signals`() = runTest {
        val (driver, _) = connected()
        val signals = listOf(
            sig("rpm", "0C", "((A*256)+B)/4", fast = true),
            sig("speed", "0D", "A", fast = true),
            sig("coolant", "05", "A-40", fast = false),
            sig("gear", "1002", "A", fast = false, service = "22", ecu = "7E1"), // mock doesn't answer
        )
        val snaps = mutableListOf<PollSnapshot>()
        try {
            PollScheduler(driver, signals).run { snaps += it; if (snaps.size == 40) throw CancellationException("done") }
        } catch (_: CancellationException) {}
        val last = snaps.last()
        assertEquals(850f, last.values["rpm"]!!, 0.5f)
        assertEquals(85f, last.values["coolant"]!!, 0.5f)
        assertNull(last.values["gear"])
        assertEquals(3, last.liveSignals) // gear parked after repeated no-answer
    }

    @Test fun `mock ECU encodes what the formulas decode`() = runTest {
        val state = VehicleState.simulated(0.75f)
        val mock = MockTransport(responseDelayMs = 0, liveData = { pid -> MockEcu.encode(state, pid) })
        val (driver, _) = connected(mock)
        val values = driver.readPids(listOf(0x0C, 0x0B, 0x5E))
        assertEquals(state.rpm, Formula.compile("((A*256)+B)/4").evaluate(values[0x0C]!!)!!.toFloat(), 1f)
        assertEquals(state.boostKPa, values[0x0B]!![0].toInt().and(0xFF).toFloat(), 1f)
        assertEquals(state.fuelRateLh, Formula.compile("((A*256)+B)*0.05").evaluate(values[0x5E]!!)!!.toFloat(), 0.05f)
    }
}
