package io.ingwe.dash

import io.ingwe.dash.data.transport.ServiceAllowlist
import io.ingwe.dash.data.transport.TransportGuard
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SAFETY TEST: Proves the OBD service allowlist blocks all disallowed services.
 *
 * This test must ALWAYS pass. If it fails, the safety constraint is broken.
 */
class TransportAllowlistTest {

    // ── Blocked services ─────────────────────────────────────────────────────

    @Test fun `mode 02 freeze frame is blocked`() = assertBlocked("0201")
    @Test fun `mode 04 clear codes is blocked by default`() = assertBlocked("04")
    @Test fun `mode 05 O2 test is blocked`() = assertBlocked("05")
    @Test fun `mode 06 on-board test is blocked`() = assertBlocked("06")
    @Test fun `mode 08 bi-directional is blocked`() = assertBlocked("08")
    @Test fun `mode 10 UDS diagnostic session is blocked`() = assertBlocked("1003")
    @Test fun `mode 11 UDS ECU reset is blocked`() = assertBlocked("1101")
    @Test fun `mode 14 UDS clear DTC is blocked by default`() = assertBlocked("14FF FF FF")
    @Test fun `mode 27 UDS security access is blocked`() = assertBlocked("2701")
    @Test fun `mode 2E UDS write data is blocked`() = assertBlocked("2E1000AB")
    @Test fun `mode 2F UDS IO control is blocked`() = assertBlocked("2F")
    @Test fun `mode 31 UDS routine control is blocked`() = assertBlocked("31")

    // ── Bypass attempts ───────────────────────────────────────────────────────
    // The ELM327 ignores whitespace, so these all reach the ECU as Mode 04.

    @Test fun `mode 04 split by a space is blocked`() = assertBlocked("0 4")
    @Test fun `mode 04 split by a tab is blocked`() = assertBlocked("0\t4")
    @Test fun `mode 04 with leading spaces is blocked`() = assertBlocked("   04")
    @Test fun `UDS 0x14 split by spaces is blocked`() = assertBlocked("1 4 FF FF FF")
    @Test fun `single hex digit is rejected`() = assertBlocked("4")
    @Test fun `non-hex garbage is rejected`() = assertBlocked("Z4")
    @Test fun `empty command is rejected`() = assertBlocked("   ")

    // ── Allowed services ──────────────────────────────────────────────────────

    @Test fun `mode 01 with spaces is allowed`() = assertAllowed("01 0C 1")
    @Test fun `lowercase AT command is allowed`() = assertAllowed("atz")

    @Test fun `mode 01 live data is allowed`() = assertAllowed("010C1")
    @Test fun `mode 03 stored DTCs is allowed`() = assertAllowed("03")
    @Test fun `mode 07 pending DTCs is allowed`() = assertAllowed("07")
    @Test fun `mode 09 vehicle info is allowed`() = assertAllowed("0902")
    @Test fun `mode 0A permanent DTCs is allowed`() = assertAllowed("0A")
    @Test fun `UDS 0x19 read DTC info is allowed`() = assertAllowed("190201")
    @Test fun `UDS 0x22 read data by ID is allowed`() = assertAllowed("221000")

    // ── AT commands ───────────────────────────────────────────────────────────

    @Test fun `ATZ is always allowed`() = assertAllowed("ATZ")
    @Test fun `ATE0 is always allowed`() = assertAllowed("ATE0")
    @Test fun `ATSP6 is always allowed`() = assertAllowed("ATSP6")
    @Test fun `ATSH7E0 is always allowed`() = assertAllowed("ATSH7E0")
    @Test fun `ATCRA7E8 is always allowed`() = assertAllowed("ATCRA7E8")
    @Test fun `ATST is always allowed`() = assertAllowed("ATST64")

    @Test fun `ATRV battery voltage is allowed`() = assertAllowed("ATRV")
    @Test fun `ATLP adapter sleep is allowed`() = assertAllowed("ATLP")
    @Test fun `ATAT2 adaptive timing is allowed`() = assertAllowed("ATAT2")

    // ── Adapter commands that could bypass the read-only rule ──────────────────

    @Test fun `STN raw transmit STPX is blocked`() = assertBlocked("STPX h:7E0, d:04")
    @Test fun `any ST extension is blocked`() = assertBlocked("STDI")
    @Test fun `monitor all ATMA is blocked`() = assertBlocked("ATMA")
    @Test fun `programmable parameters ATPP is blocked`() = assertBlocked("ATPP 0C SV 23")
    @Test fun `wake-up messages ATWM are blocked`() = assertBlocked("ATWM 81 10 F1 3E")
    @Test fun `baud rate change ATBRD is blocked`() = assertBlocked("ATBRD 23")
    @Test fun `unknown AT command is blocked`() = assertBlocked("ATTEST")

    // ── Clearing codes is never possible ──────────────────────────────────────

    @Test fun `mode 04 can never be unlocked`() {
        assertTrue(0x04 !in ServiceAllowlist.active)
        assertBlocked("04")
    }

    @Test fun `UDS 0x14 can never be unlocked`() {
        assertTrue(0x14 !in ServiceAllowlist.active)
        assertBlocked("14FFFFFF")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun assertBlocked(cmd: String) {
        assertThrows(
            "Expected SAFETY BLOCK for command: $cmd",
            IllegalArgumentException::class.java,
        ) {
            TransportGuard.validateCommand(cmd)
        }
    }

    private fun assertAllowed(cmd: String) {
        // Should not throw
        TransportGuard.validateCommand(cmd)
        assertTrue(true)
    }
}
