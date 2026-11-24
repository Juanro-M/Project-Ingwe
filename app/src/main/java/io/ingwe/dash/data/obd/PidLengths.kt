package io.ingwe.dash.data.obd

/**
 * Data length (bytes after the PID) of standard Mode 01 PIDs, from SAE J1979.
 *
 * Needed to split a *batched* response: when we ask for "01 0C 0D 05" the ECU
 * replies with one message "41 0C a b 0D c 05 d" and nothing marks where one PID's
 * data ends — you have to know each length. PIDs missing from this table are never
 * batched (they're requested on their own).
 */
object PidLengths {
    private val LENGTHS: Map<Int, Int> = mapOf(
        // Support bitmaps
        0x00 to 4, 0x20 to 4, 0x40 to 4, 0x60 to 4, 0x80 to 4, 0xA0 to 4, 0xC0 to 4,
        0x04 to 1, // engine load
        0x05 to 1, // coolant temperature
        0x0B to 1, // intake manifold absolute pressure
        0x0C to 2, // engine speed
        0x0D to 1, // vehicle speed
        0x0F to 1, // intake air temperature
        0x10 to 2, // mass air flow
        0x11 to 1, // throttle position
        0x1F to 2, // run time since start
        0x21 to 2, // distance with MIL on
        0x23 to 2, // fuel rail gauge pressure (diesel)
        0x2C to 1, // commanded EGR
        0x2D to 1, // EGR error
        0x2F to 1, // fuel tank level
        0x31 to 2, // distance since codes cleared
        0x33 to 1, // barometric pressure
        0x42 to 2, // control module voltage
        0x46 to 1, // ambient air temperature
        0x5C to 1, // engine oil temperature
        0x5E to 2, // engine fuel rate
        0x61 to 1, // driver demand torque %
        0x62 to 1, // actual engine torque %
        0x63 to 2, // engine reference torque
        0x6F to 3, // turbo compressor inlet pressure
        0x70 to 10, // boost pressure control
        0x71 to 6, // variable geometry turbo control
        0x77 to 5, // charge air cooler temperature
        0x78 to 9, // exhaust gas temperature bank 1
    )

    fun of(pid: Int): Int? = LENGTHS[pid]
    fun isKnown(pid: Int) = pid in LENGTHS
}
