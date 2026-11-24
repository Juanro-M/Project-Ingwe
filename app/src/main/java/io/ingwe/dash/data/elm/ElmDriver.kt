package io.ingwe.dash.data.elm

import io.ingwe.dash.data.obd.ObdResponseParser
import io.ingwe.dash.data.obd.PidLengths
import io.ingwe.dash.data.transport.Transport
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Talks to an ELM327 / STN adapter over any [Transport] and speaks OBD-II / UDS.
 *
 * Latency tricks (each one is explained in docs/HOW_IT_WORKS.md):
 *  - ATE0 / ATL0 / ATS0: no echo, no line feeds, no spaces — fewer bytes per reply.
 *  - ATSP6: fixed protocol (CAN 11-bit 500k), so the adapter never "SEARCHING…".
 *  - ATSH7E0 + ATCRA7E8: talk to the engine ECU only; other modules stay quiet.
 *  - ATAT2: aggressive adaptive timing — the adapter learns how fast the ECU answers.
 *  - Response-count suffix ("010C1"): the adapter returns the moment one reply
 *    arrives instead of waiting out its full timeout for more ECUs.
 *  - Batching: up to [MAX_PIDS_PER_REQUEST] PIDs in one request ("010C0D0B…").
 *
 * The ELM can only do one thing at a time, so every command goes through a [Mutex].
 */
class ElmDriver(
    private val transport: Transport,
    private val commandTimeoutMs: Long = 1_000,
) {
    private val lock = Mutex()
    private var header: String? = null

    var adapterVersion: String? = null
        private set

    suspend fun connect() {
        transport.connect()
        val reset = command("ATZ", timeoutMs = 3_000)
        adapterVersion = reset.firstOrNull { it.contains("ELM", true) || it.contains("STN", true) }
        for (cmd in INIT_SEQUENCE) command(cmd)
        header = null
        setHeader(ENGINE_ECU)
    }

    suspend fun disconnect() = transport.disconnect()

    /** Send one raw command and collect its reply lines (without the ">" prompt). */
    suspend fun command(cmd: String, timeoutMs: Long = commandTimeoutMs): List<String> = lock.withLock {
        withTimeout(timeoutMs) {
            transport.send(cmd).filter { it != ">" && it.isNotBlank() }.toList()
        }
    }

    /** Address requests to [ecu] (e.g. "7E0") and only listen to its reply ID (+8). Skipped if already set. */
    suspend fun setHeader(ecu: String) {
        if (header == ecu) return
        command("ATSH$ecu")
        command("ATCRA%03X".format(ecu.toInt(16) + 8))
        header = ecu
    }

    /**
     * Read up to six Mode 01 PIDs in one request. Returns PID → data bytes for the
     * PIDs that answered (missing = unsupported or no data).
     */
    suspend fun readPids(pids: List<Int>): Map<Int, ByteArray> {
        require(pids.size in 1..MAX_PIDS_PER_REQUEST) { "1..$MAX_PIDS_PER_REQUEST PIDs per request" }
        require(pids.size == 1 || pids.all { PidLengths.isKnown(it) }) { "Only PIDs with known lengths can be batched" }
        setHeader(ENGINE_ECU)
        val cmd = buildString(2 + pids.size * 2 + 1) {
            append("01")
            pids.forEach { append(HEX[it ushr 4]).append(HEX[it and 0xF]) }
            append('1') // response count: return as soon as the engine ECU answers
        }
        val payload = ObdResponseParser.payload(command(cmd)) ?: return emptyMap()
        val split = ObdResponseParser.splitMode01(payload)
        if (split.isEmpty() && pids.size == 1 && payload.size >= 2 && (payload[0].toInt() and 0xFF) == 0x41) {
            // Unknown-length PID asked on its own: everything after the PID byte is its data.
            return mapOf((payload[1].toInt() and 0xFF) to payload.copyOfRange(2, payload.size))
        }
        return split
    }

    /**
     * Battery voltage measured by the adapter itself at OBD pin 16 ("ATRV").
     * Works with the ignition OFF — no ECU involved — so it's the one live reading
     * we can show while the truck is parked.
     */
    suspend fun readVoltage(): Float? =
        command("ATRV").firstNotNullOfOrNull { line -> line.trim().removeSuffix("V").removeSuffix("v").toFloatOrNull() }

    /** True if the engine ECU answers at all (ignition on). */
    suspend fun ecuAwake(): Boolean = readPids(listOf(0x0C)).isNotEmpty()

    /** Mode 01 PIDs the engine ECU says it supports (walks the 0x00, 0x20, 0x40 … bitmaps). */
    suspend fun supportedPids(): Set<Int> {
        val out = HashSet<Int>()
        var base = 0x00
        while (base <= 0xE0) {
            val data = readPids(listOf(base))[base] ?: break
            if (data.size < 4) break
            for (bit in 0 until 32) {
                val byte = data[bit / 8].toInt() and 0xFF
                if (byte and (0x80 ushr (bit % 8)) != 0) out += base + bit + 1
            }
            if (base + 0x20 !in out) break // the last bit says whether the next range exists
            base += 0x20
        }
        return out
    }

    /** UDS ReadDataByIdentifier (service 0x22) on [ecu]. Returns the data bytes after the DID, or null. */
    suspend fun readDid(ecu: String, did: Int): ByteArray? {
        setHeader(ecu)
        val payload = ObdResponseParser.payload(command("22%04X1".format(did))) ?: return null
        if (payload.size < 3 || (payload[0].toInt() and 0xFF) != 0x62) return null
        val echoed = ((payload[1].toInt() and 0xFF) shl 8) or (payload[2].toInt() and 0xFF)
        return if (echoed == did) payload.copyOfRange(3, payload.size) else null
    }

    companion object {
        const val ENGINE_ECU = "7E0"
        const val MAX_PIDS_PER_REQUEST = 6
        private val HEX = "0123456789ABCDEF".toCharArray()

        val INIT_SEQUENCE = listOf(
            "ATE0",   // echo off
            "ATL0",   // line feeds off
            "ATS0",   // spaces off
            "ATH1",   // show CAN IDs (needed to reassemble multi-frame replies)
            "ATSP6",  // ISO 15765-4 CAN, 11-bit, 500 kbaud — no protocol search
            "ATCAF1", // CAN auto-formatting on
            "ATAT2",  // aggressive adaptive timing
        )
    }
}
