package io.ingwe.dash.data.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * BLE UART transport for ELM327/STN adapters.
 *
 * Supports generic UART service UUIDs:
 *   FFF0 / FFF1 (write) / FFF2 (notify) — common Chinese BLE OBD adapters
 *   FFE0 / FFE1 (write+notify)           — HM-10 based adapters
 *   Nordic UART: 6E400001-B5A3-F393-E0A9-E50E24DCCA9E
 *                TX: 6E400002, RX notify: 6E400003
 *
 * Additionally probes known adapter-specific UUIDs:
 *   Vgate vLinker MC+ / iCar Pro: ADB20100-... (probed at runtime)
 *   OBDLink CX: specific UUID added in M3 once confirmed
 *
 * NOTE: This is a STUB for M1. Full implementation in M3.
 */
class BleTransport(private val context: Context) : Transport {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: Flow<ConnectionState> = _connectionState

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private val lineChannel = Channel<String>(capacity = Channel.UNLIMITED)

    override suspend fun connect() {
        // M3: implement BLE scan → connect → discover services → find UART chars
        _connectionState.value = ConnectionState.ERROR
        throw TransportException("BleTransport: not yet implemented (M3)")
    }

    override fun send(command: String): Flow<String> {
        TransportGuard.validateCommand(command)
        return flow {
            throw TransportException("BleTransport: not yet implemented (M3)")
        }
    }

    override suspend fun disconnect() {
        gatt?.close()
        gatt = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    companion object {
        private const val TAG = "Argus/BleTransport"

        // Known BLE UART service / characteristic UUIDs
        // Write characteristic: adapter reads OBD commands from here
        // Notify characteristic: adapter sends responses here

        val KNOWN_UART_PROFILES: List<UartProfile> = listOf(
            UartProfile(
                name = "FFF0 (generic)",
                serviceUuid   = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"),
                writeUuid     = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb"),
                notifyUuid    = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"),
            ),
            UartProfile(
                name = "FFE0 (HM-10)",
                serviceUuid   = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"),
                writeUuid     = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),
                notifyUuid    = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),
            ),
            UartProfile(
                name = "Nordic UART",
                serviceUuid   = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
                writeUuid     = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"),
                notifyUuid    = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"),
            ),
            // Vgate vLinker MC+ / iCar Pro — to be confirmed in M3
            UartProfile(
                name = "Vgate BEF0 (tentative)",
                serviceUuid   = UUID.fromString("0000bef0-0000-1000-8000-00805f9b34fb"),
                writeUuid     = UUID.fromString("0000bef2-0000-1000-8000-00805f9b34fb"),
                notifyUuid    = UUID.fromString("0000bef1-0000-1000-8000-00805f9b34fb"),
            ),
        )

        // Client Characteristic Configuration Descriptor UUID (standard BLE)
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    data class UartProfile(
        val name: String,
        val serviceUuid: UUID,
        val writeUuid: UUID,
        val notifyUuid: UUID,
    )
}
