package io.ingwe.dash.data.transport

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Simulates an ELM327/STN adapter connected to a 2018 VW Amarok 3.0 V6 TDI.
 *
 * Designed for unit tests and development WITHOUT a physical OBD adapter.
 * It mimics:
 *   - ELM327 init sequence (ATZ, ATE0, etc.)
 *   - Mode 01 PID support bitmasks (realistic subset for a diesel ECU)
 *   - Mode 01 live data responses (RPM, speed, coolant, etc.)
 *   - ISO-TP multi-frame responses (for responses > 7 bytes)
 *   - NO DATA for unsupported PIDs
 *   - SEARCHING... delay on first CAN connect
 *   - CAN ERROR simulation (toggled via [simulateCanError])
 *
 * Response timing: [responseDelayMs] (default 50 ms) mimics adapter latency.
 * Override [customResponses] to inject specific raw responses in tests.
 */
class MockTransport(
    private val responseDelayMs: Long = 50L,
) : Transport {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: Flow<ConnectionState> = _connectionState

    /** Set to true in a test to simulate a CAN ERROR response. */
    var simulateCanError: Boolean = false

    /** Set to true to simulate SEARCHING... delay on PID requests. */
    var simulateSearching: Boolean = false

    /** Override specific raw hex commands → raw response lines. */
    val customResponses: MutableMap<String, List<String>> = mutableMapOf()

    private var connected = false

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
            delay(responseDelayMs)

            if (simulateCanError) {
                emit("CAN ERROR")
                emit(">")
                return@flow
            }

            val key = command.trim().uppercase()

            // Custom response override
            customResponses[key]?.let { lines ->
                lines.forEach { emit(it) }
                emit(">")
                return@flow
            }

            // Built-in response table
            val lines = RESPONSES[key] ?: listOf("NO DATA")

            if (simulateSearching && !key.startsWith("AT") && !key.startsWith("ST")) {
                emit("SEARCHING...")
                delay(500)
            }

            lines.forEach { emit(it) }
            emit(">")
        }
    }

    override suspend fun disconnect() {
        connected = false
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    companion object {
        /**
         * Canned ELM327 responses.
         *
         * Format: command (uppercase, trimmed) → list of response lines.
         * The ">" prompt is appended by [send], not here.
         *
         * PID support bitmask for 7E8 (engine ECU):
         *   PIDs 01-20: 0xBE3EB811  → RPM✓ Speed✓ Load✓ Coolant✓ MAF✓ MAP✓
         *   PIDs 21-40: 0xA0150000  → IAT✓ FuelRailPressure✓ (0x23)
         *   PIDs 41-60: 0xFED00000  → Voltage✓(0x42), OilTemp✓(0x5C), FuelRate✓(0x5E)
         *   PIDs 61-80: 0xE0000000  → Boost✓(0x6F), EGT✓(0x78)
         *   PIDs 81-A0: 0x20000000  → DPF✓(0x7C)
         *
         * Actual simulated values:
         *   RPM    = 850  (idle)
         *   Speed  = 0
         *   Load   = 20%
         *   Coolant= 85°C
         *   IAT    = 30°C
         *   MAF    = 8.5 g/s
         *   MAP    = 101 kPa (atmospheric, idle)
         *   Voltage= 14.1 V
         */
        private val RESPONSES: Map<String, List<String>> = mapOf(

            // ── Adapter init ──────────────────────────────────────────────
            "ATZ"       to listOf("ELM327 v1.5"),
            "ATE0"      to listOf("OK"),
            "ATL0"      to listOf("OK"),
            "ATS0"      to listOf("OK"),
            "ATH1"      to listOf("OK"),
            "ATSP6"     to listOf("OK"),
            "ATCAF1"    to listOf("OK"),
            "ATSH7E0"   to listOf("OK"),
            "ATCRA7E8"  to listOf("OK"),
            "ATAT1"     to listOf("OK"),
            "ATST64"    to listOf("OK"),

            // ── Mode 09 — VIN ─────────────────────────────────────────────
            // ISO-TP multi-frame: VIN = "1VWFE21C04M000001" (fake)
            "0902"      to listOf(
                "014",
                "7E8 10 14 49 02 01 31 56 57",
                "7E8 21 46 45 32 31 43 30 34",
                "7E8 22 4D 30 30 30 30 30 31"
            ),

            // ── Mode 01 PID support bitmasks ──────────────────────────────
            "010001"    to listOf("7E8 06 41 00 BE 3E B8 11"),  // PIDs 01-20
            "012001"    to listOf("7E8 06 41 20 A0 15 00 00"),  // PIDs 21-40
            "014001"    to listOf("7E8 06 41 40 FE D0 00 00"),  // PIDs 41-60
            "016001"    to listOf("7E8 06 41 60 E0 00 00 00"),  // PIDs 61-80
            "018001"    to listOf("7E8 04 41 80 20 00 00 00"),  // PIDs 81-A0
            "01A001"    to listOf("NO DATA"),                   // PIDs A1-C0 not supported

            // ── Mode 01 live data (idle, engine warm) ─────────────────────
            // RPM: 0x0C — Formula: ((A*256)+B)/4 — 850 rpm → A=0x0D, B=0x24
            "010C1"     to listOf("7E8 04 41 0C 0D 24"),

            // Speed: 0x0D — Formula: A — 0 km/h
            "010D1"     to listOf("7E8 03 41 0D 00"),

            // Engine load: 0x04 — Formula: A*100/255 — 20% → A=0x33
            "01041"     to listOf("7E8 03 41 04 33"),

            // Coolant temp: 0x05 — Formula: A-40 — 85°C → A=0x7D
            "01051"     to listOf("7E8 03 41 05 7D"),

            // IAT: 0x0F — Formula: A-40 — 30°C → A=0x46
            "010F1"     to listOf("7E8 03 41 0F 46"),

            // MAF: 0x10 — Formula: ((A*256)+B)/100 g/s — 8.5 g/s → 850 = 0x0352
            "01101"     to listOf("7E8 04 41 10 03 52"),

            // MAP: 0x0B — Formula: A kPa — 101 kPa → A=0x65
            "010B1"     to listOf("7E8 03 41 0B 65"),

            // Fuel rail pressure: 0x23 — Formula: ((A*256)+B)*10 kPa — 1800 bar→ skip for now
            "01231"     to listOf("7E8 04 41 23 1B 58"),

            // Battery voltage: 0x42 — Formula: ((A*256)+B)/1000 V — 14.1V → 14100 = 0x3714
            "01421"     to listOf("7E8 04 41 42 37 14"),

            // Oil temp: 0x5C — Formula: A-40 — 90°C → A=0x82
            "015C1"     to listOf("7E8 03 41 5C 82"),

            // Fuel rate: 0x5E — Formula: ((A*256)+B)*0.05 L/h — idle 1.5 L/h → 30=0x001E
            "015E1"     to listOf("7E8 04 41 5E 00 1E"),

            // Boost: 0x6F — Formula per spec: 3 bytes; B=absolute kPa, C=relative kPa
            "016F1"     to listOf("7E8 05 41 6F 00 65 00"),

            // EGT: 0x78 — multi-frame; sensor 1 temp = 350°C → 3500 + 40 = 3540 = 0x0DD4
            "01781"     to listOf(
                "7E8 10 09 41 78 11 00 0D D4",
                "7E8 21 00 00 00 00 00 00 00"
            ),

            // DPF: 0x7C — multi-frame; soot load 10%, ash 2%
            "017C1"     to listOf(
                "7E8 10 0A 41 7C 00 0A 02 00",
                "7E8 21 00 00 00 00 00 00 00"
            ),

            // Batched request example (RPM + Speed + Load + Coolant)
            "010C0D04054"   to listOf(
                "7E8 06 41 0C 0D 24 00",
                "7E8 06 41 0D 00 04 33",
                "7E8 04 41 05 7D 00 00"
            ),

            // ── Mode 03 — Stored DTCs ─────────────────────────────────────
            "03"        to listOf("7E8 02 43 00"), // No stored DTCs

            // ── Mode 07 — Pending DTCs ────────────────────────────────────
            "07"        to listOf("7E8 02 47 00"), // No pending DTCs

            // ── Mode 0A — Permanent DTCs ──────────────────────────────────
            "0A"        to listOf("7E8 02 4A 00"), // No permanent DTCs

            // ── UDS 0x19 0x02 — Read DTCs with status mask ────────────────
            "190201"    to listOf("7E8 03 59 02 00"), // No DTCs

            // ── UDS 0x22 — ReadDataByIdentifier (DID scanner) ─────────────
            // Real DIDs are unknown; return a fake response for 0x1000
            "221000"    to listOf("7E8 05 62 10 00 AB CD"),
            // All others: NRC 0x31 (requestOutOfRange)
        )
    }
}
