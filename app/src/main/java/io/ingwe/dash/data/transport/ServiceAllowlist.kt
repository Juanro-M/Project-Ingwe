package io.ingwe.dash.data.transport

/**
 * SAFETY: Read-only OBD/UDS service allowlist. Fixed at compile time.
 *
 * Only these service bytes may ever reach the vehicle. Everything else —
 * clearing codes (0x04, UDS 0x14), session control (0x10), reset (0x11),
 * security access (0x27), writing (0x2E), actuator tests (0x2F), routines (0x31),
 * programming — is rejected by [TransportGuard] before the bytes leave the phone.
 *
 * There is deliberately NO way to unlock more services at runtime: Ingwe never
 * changes anything in the car. Clearing fault codes is left to a workshop tool.
 *
 * Unit test: TransportAllowlistTest.kt — proves each blocked service is rejected.
 */
object ServiceAllowlist {

    /** The only services Ingwe may send. All are read-only by definition. */
    val active: Set<Int> = setOf(
        0x01, // OBD Mode 01 — live data
        0x03, // OBD Mode 03 — read stored fault codes
        0x07, // OBD Mode 07 — read pending fault codes
        0x09, // OBD Mode 09 — vehicle info (VIN etc.)
        0x0A, // OBD Mode 0A — read permanent fault codes
        0x19, // UDS ReadDTCInformation (read-only)
        0x22, // UDS ReadDataByIdentifier (read-only)
    )
}
