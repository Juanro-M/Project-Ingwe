package io.ingwe.dash.data.transport

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Simulates an ELM327 adapter plugged into a VW Amarok 3.0 V6 TDI engine ECU.
 *
 * Used by the unit tests and by the app's "Mock ELM" data source, so the full
 * pipeline (driver → ISO-TP → parser → formulas → gauges) runs without hardware.
 *
 * Behaves like the real thing where it matters:
 *  - Honours ATS0/ATS1 (spaces) and ATH0/ATH1 (CAN IDs); ATZ resets them.
 *  - Answers ANY Mode 01 request, single or batched ("010C0D0B1"), composing a
 *    correct ISO-TP reply — single frame, or first + consecutive frames when long.
 *  - Builds the 0x00/0x20/… support bitmaps from the PIDs it actually knows.
 *  - Answers UDS 0x22 for DIDs in [didData]; NO DATA otherwise.
 *  - Optional [liveData] provider: when set, Mode 01 values come from it (the app
 *    feeds it from the demo throttle, so the slider drives the "ECU").
 *
 * Response timing: [responseDelayMs] mimics adapter + bus latency.
 * [customResponses] overrides exact commands in tests.
 */
class MockTransport(
    private val responseDelayMs: Long = 50L,
    private val liveData: ((pid: Int) -> ByteArray?)? = null,
    /** False = ignition off: the ECU stays silent, but ATRV (adapter voltage) still answers. */
    private val ecuAwake: () -> Boolean = { true },
    private val batteryVolts: () -> Float = { 12.6f },
) : Transport {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: Flow<ConnectionState> = _connectionState

    /** Set to true in a test to simulate a CAN ERROR response. */
    var simulateCanError: Boolean = false

    /** Set to true to simulate SEARCHING... delay on PID requests. */
    var simulateSearching: Boolean = false

    /** Override specific raw hex commands → raw response lines. */
    val customResponses: MutableMap<String, List<String>> = mutableMapOf()

    /** UDS DIDs this ECU answers (DID → data bytes). */
    val didData: MutableMap<Int, ByteArray> = mutableMapOf()

    /** Number of commands received — lets tests count round trips. */
    var commandCount = 0
        private set

    private var connected = false
    private var spaces = true
    private var headers = true

    override suspend fun connect() {
        _connectionState.value = ConnectionState.CONNECTING
        delay(100)
        connected = true
        _connectionState.value = ConnectionState.CONNECTED
    }

    override fun send(command: String): Flow<String> {
        // Safety check applies even to mock
        TransportGuard.validateCommand(command)
        check(connected) { "MockTransport: not connected" }

        return flow {
            commandCount++
            delay(responseDelayMs)

            if (simulateCanError) {
                emit("CAN ERROR")
                emit(">")
                return@flow
            }

            val key = command.replace(" ", "").trim().uppercase()

            customResponses[key]?.let { lines ->
                lines.forEach { emit(format(it)) }
                emit(">")
                return@flow
            }

            if (key.startsWith("AT") || key.startsWith("ST")) {
                emit(handleAt(key))
                emit(">")
                return@flow
            }

            if (simulateSearching) {
                emit("SEARCHING...")
                delay(500)
            }

            val lines = when {
                !ecuAwake() -> listOf("NO DATA")
                key.startsWith("01") && (liveData != null || key !in RESPONSES) -> mode01(key)
                key.startsWith("22") && key.length >= 6 -> mode22(key)
                else -> RESPONSES[key] ?: listOf("NO DATA")
            }
            lines.forEach { emit(format(it)) }
            emit(">")
        }
    }

    override suspend fun disconnect() {
        connected = false
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun handleAt(key: String): String = when (key) {
        "ATZ" -> { spaces = true; headers = true; "ELM327 v1.5" }
        "ATS0" -> { spaces = false; "OK" }
        "ATS1" -> { spaces = true; "OK" }
        "ATH0" -> { headers = false; "OK" }
        "ATH1" -> { headers = true; "OK" }
        "ATI" -> "ELM327 v1.5"
        "ATRV" -> "%.1fV".format(java.util.Locale.US, batteryVolts())
        else -> "OK"
    }

    /** "01" + PID pairs + optional response-count digit. */
    private fun mode01(key: String): List<String> {
        val body = key.substring(2)
        val pidHex = if (body.length % 2 == 1) body.dropLast(1) else body
        val pids = pidHex.chunked(2).mapNotNull { it.toIntOrNull(16) }
        val payload = ArrayList<Byte>()
        payload += 0x41.toByte()
        for (pid in pids) {
            val data = dataFor(pid) ?: continue
            payload += pid.toByte()
            data.forEach { payload += it }
        }
        return if (payload.size == 1) listOf("NO DATA") else isoTpFrames(payload)
    }

    private fun mode22(key: String): List<String> {
        val did = key.substring(2, 6).toIntOrNull(16) ?: return listOf("NO DATA")
        val data = didData[did] ?: return listOf("7E8 03 7F 22 31") // requestOutOfRange
        val payload = ArrayList<Byte>()
        payload += 0x62.toByte(); payload += (did ushr 8).toByte(); payload += did.toByte()
        data.forEach { payload += it }
        return isoTpFrames(payload)
    }

    private fun dataFor(pid: Int): ByteArray? {
        if (pid % 0x20 == 0) return supportBitmap(pid)
        return liveData?.invoke(pid) ?: STATIC_DATA[pid]
    }

    /** Bit 31 = base+1 … bit 0 = base+32 (which also means "next range exists"). */
    private fun supportBitmap(base: Int): ByteArray? {
        val known = STATIC_DATA.keys
        if (base != 0 && known.none { it in (base + 1)..(base + 0x20) }) return null
        var mask = 0L
        for (pid in known) if (pid in (base + 1)..(base + 0x20)) mask = mask or (1L shl (32 - (pid - base)))
        if (known.any { it > base + 0x20 }) mask = mask or 1L
        return byteArrayOf((mask ushr 24).toByte(), (mask ushr 16).toByte(), (mask ushr 8).toByte(), mask.toByte())
    }

    /** Encode a payload as ELM-style CAN frames from 7E8 (always written with spaces; [format] strips them). */
    private fun isoTpFrames(payload: List<Byte>): List<String> {
        val bytes = payload.map { it.toInt() and 0xFF }
        fun line(frame: List<Int>) = "7E8 " + frame.joinToString(" ") { "%02X".format(it) }
        if (bytes.size <= 7) return listOf(line(listOf(bytes.size) + bytes))
        val out = mutableListOf(line(listOf(0x10 or (bytes.size ushr 8), bytes.size and 0xFF) + bytes.take(6)))
        var i = 6
        var sn = 1
        while (i < bytes.size) {
            val chunk = bytes.subList(i, minOf(i + 7, bytes.size))
            out += line(listOf(0x20 or sn) + chunk + List(7 - chunk.size) { 0 })
            i += 7
            sn = (sn + 1) and 0x0F
        }
        return out
    }

    /** Apply the current ATS / ATH settings to a frame line. */
    private fun format(line: String): String {
        val tokens = line.split(' ')
        val isFrame = tokens.size > 1 && tokens.all { t -> t.isNotEmpty() && t.all { it.isDigit() || it in 'A'..'F' } }
        if (!isFrame) return line
        val kept = if (!headers && tokens[0].length == 3) tokens.drop(1) else tokens
        return if (spaces) kept.joinToString(" ") else kept.joinToString("")
    }

    companion object {
        /**
         * Idle, warm engine (Mode 01 data bytes, after the PID).
         *   RPM 850, 0 km/h, load 20 %, coolant 85 °C, IAT 30 °C, MAF 8.5 g/s,
         *   MAP 101 kPa, rail 300 bar, 14.1 V, oil 90 °C, 1.5 L/h, fuel 62 %,
         *   baro 83 kPa (~1,600 m), ambient 24 °C, EGR 45 %, EGT 314 °C.
         */
        val STATIC_DATA: Map<Int, ByteArray> = mapOf(
            0x04 to b(0x33),
            0x05 to b(0x7D),
            0x0B to b(0x65),
            0x0C to b(0x0D, 0x48),
            0x0D to b(0x00),
            0x0F to b(0x46),
            0x10 to b(0x03, 0x52),
            0x23 to b(0x0B, 0xB8),
            0x2C to b(0x73),
            0x2F to b(0x9E),
            0x33 to b(0x53),
            0x42 to b(0x37, 0x14),
            0x46 to b(0x40),
            0x5C to b(0x82),
            0x5E to b(0x00, 0x1E),
            0x62 to b(0x8C),
            0x6F to b(0x01, 0x65, 0x00),
            0x70 to b(0x03, 0x0C, 0xA0, 0x0C, 0x80, 0, 0, 0, 0, 0),
            0x71 to b(0x03, 0x59, 0x59, 0, 0, 0),
            0x77 to b(0x01, 0x46, 0, 0, 0),
            0x78 to b(0x01, 0x0D, 0xD4, 0, 0, 0, 0, 0, 0),
        )

        private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

        /** Exact canned replies kept from M1 (tests rely on these). */
        private val RESPONSES: Map<String, List<String>> = mapOf(
            // Mode 09 — VIN, ISO-TP multi-frame with the ELM byte-count header line
            "0902" to listOf(
                "014",
                "7E8 10 14 49 02 01 31 56 57",
                "7E8 21 46 45 32 31 43 30 34",
                "7E8 22 4D 30 30 30 30 30 31",
            ),
            // RPM: ((A*256)+B)/4 → 0x0D24 = 841 rpm (legacy value used by tests)
            "010C1" to listOf("7E8 04 41 0C 0D 24"),
            "010D1" to listOf("7E8 03 41 0D 00"),
            "03" to listOf("7E8 02 43 00"),   // no stored DTCs
            "07" to listOf("7E8 02 47 00"),   // no pending DTCs
            "0A" to listOf("7E8 02 4A 00"),   // no permanent DTCs
            "190201" to listOf("7E8 03 59 02 00"),
        )
    }
}
