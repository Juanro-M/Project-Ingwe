package io.ingwe.dash.data.transport

/**
 * Validates an ELM327/OBD command before it is transmitted.
 *
 * Rules:
 * 1. AT commands (starting with "AT" or "ST") are always allowed — they are
 *    adapter configuration, not OBD service requests.
 * 2. OBD/UDS commands start with a 2-hex-digit service byte. The service byte
 *    must be in [ServiceAllowlist.active].
 *
 * Throws [IllegalArgumentException] if the command is blocked.
 */
object TransportGuard {

    private val AT_PATTERN = Regex("^(AT|ST|ag)", RegexOption.IGNORE_CASE)

    /**
     * @throws IllegalArgumentException if the command is a blocked OBD service.
     */
    fun validateCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("Empty command")

        // AT / ST adapter commands are always allowed
        if (AT_PATTERN.containsMatchIn(trimmed)) return

        // Extract the first two hex chars as the service byte
        if (trimmed.length < 2) return // too short to have a service byte
        val serviceHex = trimmed.take(2)
        val serviceInt = serviceHex.toIntOrNull(16)
            ?: return // not a valid hex service byte — let the driver handle it

        require(serviceInt in ServiceAllowlist.active) {
            "SAFETY BLOCK: OBD service 0x${serviceHex.uppercase()} is not on the read-only allowlist. " +
            "Allowed: ${ServiceAllowlist.active.map { "0x%02X".format(it) }}"
        }
    }
}
