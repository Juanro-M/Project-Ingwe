package io.ingwe.dash.data.obd

import io.ingwe.dash.data.isotp.IsoTpAssembler

/**
 * Parses raw ELM327 response lines into typed OBD results.
 *
 * All responses go through [IsoTpAssembler] for multi-frame reassembly,
 * then the payload bytes are decoded per OBD-II mode/PID.
 */
object ObdResponseParser {

    sealed class ObdResult {
        data class Success(val serviceId: Int, val pid: Int, val bytes: ByteArray) : ObdResult()
        data class NoData(val reason: String = "NO DATA") : ObdResult()
        data class Error(val message: String) : ObdResult()
    }

    /**
     * Parse a list of raw ELM lines (from Transport.send) into an [ObdResult].
     */
    fun parse(lines: List<String>): ObdResult {
        // Check for adapter-level errors first
        val joined = lines.joinToString(" ").uppercase()
        return when {
            "NO DATA" in joined -> ObdResult.NoData()
            "CAN ERROR" in joined -> ObdResult.Error("CAN ERROR")
            "BUS INIT" in joined -> ObdResult.Error("BUS INIT ERROR")
            "UNABLE TO CONNECT" in joined -> ObdResult.Error("UNABLE TO CONNECT")
            "STOPPED" in joined -> ObdResult.Error("STOPPED")
            else -> {
                val assembler = IsoTpAssembler()
                lines.forEach { assembler.feed(it) }
                val payload = assembler.payload()
                if (payload.isEmpty()) return ObdResult.NoData("Empty response")

                val serviceResponse = payload[0].toInt() and 0xFF
                // UDS / OBD negative response: 7F <requested service> <NRC>
                if (serviceResponse == 0x7F) {
                    val nrc = payload.getOrNull(2)?.toInt()?.and(0xFF)
                    return ObdResult.Error(
                        if (nrc != null) "Negative response, NRC 0x%02X".format(nrc) else "Negative response"
                    )
                }
                if (serviceResponse < 0x40) return ObdResult.Error("Not a response frame: 0x%02X".format(serviceResponse))
                if (payload.size < 2) return ObdResult.Error("Response too short: ${payload.size} bytes")
                val serviceId = serviceResponse - 0x40 // response byte = request + 0x40
                val pid = payload[1].toInt() and 0xFF
                val dataBytes = payload.drop(2).toByteArray()
                ObdResult.Success(serviceId, pid, dataBytes)
            }
        }
    }

    private val ERROR_MARKERS = arrayOf("NO DATA", "CAN ERROR", "BUS INIT", "UNABLE TO CONNECT", "STOPPED", "ERROR")

    /**
     * Fast path used by the poller: the reassembled payload bytes, or null if the
     * adapter reported an error / no data, or the ECU sent a negative response.
     */
    fun payload(lines: List<String>): ByteArray? {
        for (line in lines) {
            val upper = line.uppercase()
            if (ERROR_MARKERS.any { it in upper }) return null
        }
        val assembler = IsoTpAssembler()
        for (line in lines) assembler.feed(line)
        val payload = assembler.payload()
        if (payload.isEmpty() || (payload[0].toInt() and 0xFF) == 0x7F) return null
        return payload
    }

    /**
     * Split a (possibly batched) Mode 01 payload "41 0C a b 0D c …" into PID → data bytes.
     * Stops at the first PID whose length isn't known (see [PidLengths]).
     */
    fun splitMode01(payload: ByteArray): Map<Int, ByteArray> {
        if (payload.isEmpty() || (payload[0].toInt() and 0xFF) != 0x41) return emptyMap()
        val out = LinkedHashMap<Int, ByteArray>(8)
        var i = 1
        while (i < payload.size) {
            val pid = payload[i].toInt() and 0xFF
            val len = PidLengths.of(pid) ?: break
            if (i + 1 + len > payload.size) break
            out[pid] = payload.copyOfRange(i + 1, i + 1 + len)
            i += 1 + len
        }
        return out
    }

    private val formulaCache = java.util.concurrent.ConcurrentHashMap<String, Formula>()

    /**
     * Apply a signal formula to raw data bytes.
     * Supported formula variables: A, B, C, D … (bytes 0, 1, 2, 3 …).
     * Returns null if the formula references a byte the response didn't contain,
     * or if the formula is malformed — never a silently wrong value.
     * Formulas are compiled once and cached (see [Formula]).
     */
    fun applyFormula(formula: String, bytes: ByteArray): Double? {
        if (bytes.isEmpty()) return null
        return try {
            formulaCache.getOrPut(formula) { Formula.compile(formula) }.evaluate(bytes)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
