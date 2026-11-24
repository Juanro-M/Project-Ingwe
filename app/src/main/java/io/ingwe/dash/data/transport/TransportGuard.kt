package io.ingwe.dash.data.transport

/**
 * Validates an ELM327/OBD command before it is transmitted.
 *
 * Rules:
 * 1. Whitespace is stripped before inspection. The ELM327 ignores spaces, so
 *    "0 4" reaches the ECU as Mode 04 and must be judged as such.
 * 2. AT / ST commands are adapter configuration, not OBD service requests,
 *    and are always allowed.
 * 3. Anything else must be pure hex with at least one full service byte, and
 *    that service byte must be in [ServiceAllowlist.active]. Malformed input is
 *    rejected rather than passed through to the adapter.
 *
 * Throws [IllegalArgumentException] if the command is blocked.
 */
object TransportGuard {

    private val WHITESPACE = Regex("\\s+")
    private val ADAPTER_PREFIX = Regex("^(AT|ST)", RegexOption.IGNORE_CASE)
    private val HEX_ONLY = Regex("^[0-9A-Fa-f]+$")

    /**
     * @throws IllegalArgumentException if the command is empty, malformed, or a blocked OBD service.
     */
    fun validateCommand(command: String) {
        val normalized = command.replace(WHITESPACE, "")
        require(normalized.isNotEmpty()) { "Empty command" }

        if (ADAPTER_PREFIX.containsMatchIn(normalized)) return

        require(normalized.length >= 2 && HEX_ONLY.matches(normalized)) {
            "SAFETY BLOCK: '$command' is not a valid OBD request (expected hex service byte)."
        }

        val serviceHex = normalized.take(2).uppercase()
        val serviceInt = serviceHex.toInt(16)
        require(serviceInt in ServiceAllowlist.active) {
            "SAFETY BLOCK: OBD service 0x$serviceHex is not on the read-only allowlist. " +
                "Allowed: ${ServiceAllowlist.active.sorted().map { "0x%02X".format(it) }}"
        }
    }
}
