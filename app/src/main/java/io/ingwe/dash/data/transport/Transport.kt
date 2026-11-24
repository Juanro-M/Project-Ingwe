package io.ingwe.dash.data.transport

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the physical link to the OBD adapter.
 *
 * Implementations:
 *   - [MockTransport]  — deterministic simulation for unit tests (no hardware needed)
 *   - [BleTransport]   — real BLE UART (M3)
 *
 * Contract:
 *   - [connect] must be called before [send].
 *   - [send] is NOT thread-safe; the ELM driver serialises calls via its command queue.
 *   - Each call to [send] returns a Flow that emits raw lines from the adapter
 *     and completes when the ELM327 ">" prompt is received.
 *   - [disconnect] may be called from any coroutine; subsequent [send] calls throw [TransportException].
 */
interface Transport {

    /** Current connection state as a hot Flow. */
    val connectionState: Flow<ConnectionState>

    /** Open the link to the adapter. Suspends until connected or throws [TransportException]. */
    suspend fun connect()

    /**
     * Send a raw AT or OBD command string (no CR/LF — the transport appends "\r").
     *
     * @param command The raw command, e.g. "010C1" or "ATZ"
     * @return A cold Flow that emits each line of the response and completes on ">"
     */
    fun send(command: String): Flow<String>

    /** Close the link. Idempotent. */
    suspend fun disconnect()
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

class TransportException(message: String, cause: Throwable? = null) : Exception(message, cause)
