package io.ingwe.dash.data.isotp

/**
 * Reassembles ISO-TP (ISO 15765-2) multi-frame responses from ELM327 output.
 *
 * ELM327 returns each CAN frame as a space-separated hex string line, e.g.:
 *   "014"                          ← ELM byte-count header (ATH0/ATCAF1), ignored
 *   "7E8 10 14 49 02 01 31 56 57"  ← First Frame (FF), length=0x14=20 bytes
 *   "7E8 21 46 45 32 31 43 30 34"  ← Consecutive Frame (CF) with SN=1
 *   "7E8 22 4D 30 30 30 30 30 31"  ← CF SN=2
 *
 * Single frames look like:
 *   "7E8 04 41 0C 0D 24"           ← Single Frame (SF), length=4
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

    private var expectedLength: Int = -1
    private val collectedBytes = mutableListOf<Byte>()
    private var nextSequence = 1
    private var done = false

    /**
     * Feed a raw ELM327 response line. Call repeatedly until [isComplete] is true.
     * Lines that are not hex frame data (e.g. "OK", "SEARCHING...", ">") are ignored,
     * as is the lone byte-count header line the ELM prints before multi-frame replies.
     */
    fun feed(line: String) {
        if (done) return
        var parts = line.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.any { !it.isHex() }) return

        if (parts.size == 1) {
            val token = parts[0]
            // A short lone token is the ELM byte-count header (e.g. "014"), not a frame.
            if (token.length < 4) return
            // Spaces off (ATS0): "7E8064100BE3EB811". Odd length = 3-char 11-bit CAN ID + byte pairs;
            // even length = no header, byte pairs only.
            val start = if (token.length % 2 == 1) 3 else 0
            parts = buildList {
                if (start == 3) add(token.substring(0, 3))
                var i = start
                while (i + 1 < token.length) { add(token.substring(i, i + 2)); i += 2 }
            }
        }

        // With ATH1 the first token is an 11-bit CAN ID ("7E8") or 29-bit ID ("18DAF110").
        val dataTokens = if (parts[0].length == 3 || parts[0].length == 8) parts.drop(1) else parts
        val dataBytes = dataTokens.mapNotNull { if (it.length <= 2) it.toIntOrNull(16) else null }
        if (dataBytes.isEmpty()) return
        val pci = dataBytes[0]

        when (pci and 0xF0) {
            // Single Frame
            0x00 -> {
                val len = pci and 0x0F
                collectedBytes.addAll(dataBytes.drop(1).take(len).map { it.toByte() })
                done = true
            }
            // First Frame
            0x10 -> {
                if (dataBytes.size < 2) return
                expectedLength = ((pci and 0x0F) shl 8) or dataBytes[1]
                collectedBytes.clear()
                collectedBytes.addAll(dataBytes.drop(2).map { it.toByte() })
                nextSequence = 1
            }
            // Consecutive Frame
            0x20 -> {
                if (expectedLength < 0) return // CF without a preceding FF
                val sequence = pci and 0x0F
                if (sequence != nextSequence) return // out-of-order / duplicate frame
                nextSequence = (nextSequence + 1) and 0x0F
                collectedBytes.addAll(dataBytes.drop(1).map { it.toByte() })
                if (collectedBytes.size >= expectedLength) done = true
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
        collectedBytes.clear()
        expectedLength = -1
        nextSequence = 1
        done = false
    }

    private fun String.isHex() = all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }

    // Note: with spaces off, 29-bit CAN IDs (8 chars) can't be told apart from a
    // header-less frame by length alone. The Amarok engine bus uses 11-bit IDs.
}
