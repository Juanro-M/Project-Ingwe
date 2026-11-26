package io.ingwe.dash.data.transport

/**
 * SAFETY: validates every command before it is sent to the adapter.
 *
 * Rules:
 * 1. Whitespace is stripped first. The ELM327 ignores it, so "0 4" reaches the
 *    car as Mode 04 and must be judged as such.
 * 2. Adapter commands are allowed only if they're on an explicit allowlist of
 *    harmless setup / status commands ([ADAPTER_ALLOWED]). Everything else is
 *    blocked — including STN "ST…" extensions (e.g. STPX can transmit arbitrary
 *    raw frames), monitor-all (ATMA), programmable parameters (ATPP, writes the
 *    adapter's memory) and wake-up messages (ATWM).
 * 3. Anything else must be pure hex with a full service byte that's in
 *    [ServiceAllowlist.active] (read-only services only).
 *
 * Throws [IllegalArgumentException] if the command is blocked.
 */
object TransportGuard {

    private val WHITESPACE = Regex("\\s+")
    private val HEX_ONLY = Regex("^[0-9A-F]+$")

    /**
     * The only adapter commands Ingwe may send. None of them puts anything on
     * the CAN bus by itself; they configure how the adapter formats and addresses
     * the (read-only) requests, or read the adapter's own status.
     */
    private val ADAPTER_ALLOWED = Regex(
        "^AT(" +
            "Z|WS|D|I|@1|RV|DPN?|LP|" +       // reset, defaults, info, battery voltage, protocol, low-power sleep
            "E[01]|L[01]|S[01]|H[01]|" +       // echo, line feeds, spaces, headers
            "CAF[01]|AT[012]|" +               // CAN auto-format, adaptive timing
            "SPA?[0-9A-C]|" +                  // select protocol
            "ST[0-9A-F]{1,2}|" +               // response timeout
            "SH[0-9A-F]{3}|SH[0-9A-F]{6}|" +   // request header (which ECU we ask)
            "CRA(?:[0-9A-F]{3})?" +            // receive filter (which ECU we listen to)
            ")$",
    )

    /**
     * @throws IllegalArgumentException if the command is empty, malformed, or not explicitly allowed.
     */
    fun validateCommand(command: String) {
        val normalized = command.replace(WHITESPACE, "").uppercase()
        require(normalized.isNotEmpty()) { "Empty command" }

        if (normalized.startsWith("AT") || normalized.startsWith("ST")) {
            require(ADAPTER_ALLOWED.matches(normalized)) {
                "SAFETY BLOCK: adapter command '$command' is not on the read-only allowlist."
            }
            return
        }

        require(normalized.length >= 2 && HEX_ONLY.matches(normalized)) {
            "SAFETY BLOCK: '$command' is not a valid OBD request (expected hex service byte)."
        }

        val serviceHex = normalized.take(2)
        require(serviceHex.toInt(16) in ServiceAllowlist.active) {
            "SAFETY BLOCK: OBD service 0x$serviceHex is not on the read-only allowlist. " +
                "Allowed: ${ServiceAllowlist.active.sorted().map { "0x%02X".format(it) }}"
        }
    }
}
