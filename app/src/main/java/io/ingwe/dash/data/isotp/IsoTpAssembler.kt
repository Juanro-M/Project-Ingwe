package io.ingwe.dash.data.isotp

/**
 * Reassembles ISO-TP (ISO 15765-2) multi-frame responses from ELM327 output.
 *
 * ELM327 returns each CAN frame as a space-separated hex string line, e.g.:
 *   "7E8 10 14 49 02 01 31 56 57"  ← First Frame (FF), length=0x14=20 bytes
 *   "7E8 21 46 45 32 31 43 30 34"  ← Consecutive Frame (CF) with SN=1
 *   "7E8 22 4D 30 30 30 30 30 31"  ← CF SN=2
 *
 * Single frames look like:
 *   "7E8 06 41 0C 0D 24"           ← Single Frame (SF), length=6 (but we use byte 1)
 *
 * This assembler collects lines emitted by [Transport.send] and returns the
 * full reassembled payload bytes, stripping the PCI byte and frame headers.
 *
 * Usage:
 *   val assembler = IsoTpAssembler()
 *   lines.forEach { assembler.feed(it) }
 *   val payload: ByteArray = assembler.payload()
 */
class IsoTpAssembler {

    private val frames = mutableListOf<String>()
    private var expectedLength: Int = -1
    private var collectedBytes = mutableListOf<Byte>()
    private var done = false

    /**
     * Feed a raw ELM327 response line. Call repeatedly until [isComplete] is true.
     * Lines that are not hex frame data (e.g. "OK", "SEARCHING...") are ignored.
     */
    fun feed(line: String) {
        if (done) return
        val trimmed = line.trim()
        if (trimmed.isEmpty() || !trimmed[0].isHexDigit()) return

        val parts = trimmed.split(" ").filter { it.isNotEmpty() }
        // ELM with ATH1: first token is CAN ID (e.g. "7E8"), rest is frame data
        val dataBytes: List<Int> = if (parts.size > 1 && parts[0].length == 3) {
            parts.drop(1).mapNotNull { it.toIntOrNull(16) }
        } else {
            parts.mapNotNull { it.toIntOrNull(16) }
        }

        if (dataBytes.isEmpty()) return
        val pci = dataBytes[0]

        when {
            // Single Frame: PCI bits 7-4 = 0x0
            (pci and 0xF0) == 0x00 -> {
                val len = pci and 0x0F
                collectedBytes.addAll(dataBytes.drop(1).take(len).map { it.toByte() })
                done = true
            }
            // First Frame: PCI bits 7-4 = 0x1
            (pci and 0xF0) == 0x10 -> {
                val len = ((pci and 0x0F) shl 8) or dataBytes[1]
                expectedLength = len
                collectedBytes.addAll(dataBytes.drop(2).map { it.toByte() })
            }
            // Consecutive Frame: PCI bits 7-4 = 0x2
            (pci and 0xF0) == 0x20 -> {
                collectedBytes.addAll(dataBytes.drop(1).map { it.toByte() })
                if (expectedLength > 0 && collectedBytes.size >= expectedLength) {
                    done = true
                }
            }
        }
    }

    /** True once all frames of a multi-frame response (or any single frame) are received. */
    val isComplete: Boolean get() = done

    /**
     * Returns the reassembled OBD/UDS payload bytes.
     * For a Mode 01 response "41 0C 0D 24", returns [0x41, 0x0C, 0x0D, 0x24].
     */
    fun payload(): ByteArray {
        val limit = if (expectedLength > 0) expectedLength else collectedBytes.size
        return collectedBytes.take(limit).toByteArray()
    }

    fun reset() {
        frames.clear()
        collectedBytes.clear()
        expectedLength = -1
        done = false
    }

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
