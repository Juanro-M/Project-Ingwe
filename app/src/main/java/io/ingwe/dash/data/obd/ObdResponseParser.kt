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
                val serviceId = serviceResponse - 0x40 // response byte = request + 0x40
                if (payload.size < 2) return ObdResult.Error("Response too short: ${payload.size} bytes")
                val pid = payload[1].toInt() and 0xFF
                val dataBytes = payload.drop(2).toByteArray()
                ObdResult.Success(serviceId, pid, dataBytes)
            }
        }
    }

    /**
     * Apply a signal formula to raw data bytes.
     * Supported formula variables: A, B, C, D (bytes 0-3).
     * Returns Double or null if bytes are insufficient.
     *
     * NOTE: Formulae come from the signals.json file. This evaluator handles
     * the common patterns; complex formulae will be evaluated at M4.
     */
    fun applyFormula(formula: String, bytes: ByteArray): Double? {
        if (bytes.isEmpty()) return null
        val a = bytes.getOrNull(0)?.toInt()?.and(0xFF) ?: return null
        val b = bytes.getOrNull(1)?.toInt()?.and(0xFF) ?: 0
        val c = bytes.getOrNull(2)?.toInt()?.and(0xFF) ?: 0
        val d = bytes.getOrNull(3)?.toInt()?.and(0xFF) ?: 0

        return try {
            // Replace known variables and evaluate simple expressions
            val expr = formula
                .replace("A", a.toString())
                .replace("B", b.toString())
                .replace("C", c.toString())
                .replace("D", d.toString())
            SimpleExprEval.eval(expr)
        } catch (e: Exception) {
            null
        }
    }
}
