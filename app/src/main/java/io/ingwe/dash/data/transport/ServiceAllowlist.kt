package io.ingwe.dash.data.transport

/**
 * SAFETY: Read-only OBD/UDS service allowlist.
 *
 * Only these service bytes may be sent to the adapter. Any attempt to send
 * a command whose first hex byte is not in this set is rejected immediately
 * with an [IllegalArgumentException] — before the bytes ever reach the BLE
 * or serial layer.
 *
 * Mode 04 (clear codes) and UDS 0x14 are intentionally excluded from the
 * default allowlist. They are added only when the user explicitly opts in
 * via Settings AND confirms in a dialog at call time.
 *
 * This is enforced by [TransportGuard.validateCommand] which is called by
 * every Transport implementation.
 *
 * Unit test: TransportAllowlistTest.kt — proves each blocked service is rejected.
 */
object ServiceAllowlist {

    /** Services always permitted (read-only diagnostics). */
    val DEFAULT: Set<Int> = setOf(
        0x01, // Mode 01 — Live data (OBD-II)
        0x03, // Mode 03 — Stored DTCs
        0x07, // Mode 07 — Pending DTCs
        0x09, // Mode 09 — Vehicle info (VIN etc.)
        0x0A, // Mode 0A — Permanent DTCs
        0x19, // UDS ReadDTCInformation
        0x22, // UDS ReadDataByIdentifier
    )

    /**
     * Services that clear fault codes. Only added to [active] when the user
     * deliberately enables "Allow clear codes" in Settings.
     */
    val CLEAR_CODES: Set<Int> = setOf(
        0x04, // Mode 04 — Clear/reset emission DTCs
        0x14, // UDS ClearDiagnosticInformation
    )

    @Volatile
    private var clearCodesEnabled: Boolean = false

    /** Call this (from Settings screen) to unlock clear-code commands. Requires separate dialog confirmation at use-site. */
    fun enableClearCodes(enabled: Boolean) { clearCodesEnabled = enabled }

    val active: Set<Int>
        get() = if (clearCodesEnabled) DEFAULT + CLEAR_CODES else DEFAULT
}
