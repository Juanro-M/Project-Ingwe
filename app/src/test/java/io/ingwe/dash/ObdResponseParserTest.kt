package io.ingwe.dash

import io.ingwe.dash.data.obd.ObdResponseParser
import io.ingwe.dash.data.obd.SimpleExprEval
import org.junit.Assert.*
import org.junit.Test

class ObdResponseParserTest {

    @Test fun `parse single frame RPM response`() {
        val result = ObdResponseParser.parse(listOf("7E8 04 41 0C 0D 24", ">"))
        assertTrue(result is ObdResponseParser.ObdResult.Success)
        val s = result as ObdResponseParser.ObdResult.Success
        assertEquals(0x01, s.serviceId)
        assertEquals(0x0C, s.pid)
        assertEquals(2, s.bytes.size)
        assertEquals(0x0D.toByte(), s.bytes[0])
        assertEquals(0x24.toByte(), s.bytes[1])
    }

    @Test fun `parse NO DATA`() {
        val result = ObdResponseParser.parse(listOf("NO DATA", ">"))
        assertTrue(result is ObdResponseParser.ObdResult.NoData)
    }

    @Test fun `parse CAN ERROR`() {
        val result = ObdResponseParser.parse(listOf("CAN ERROR", ">"))
        assertTrue(result is ObdResponseParser.ObdResult.Error)
    }

    @Test fun `parse coolant temp`() {
        // 7E8 03 41 05 7D — coolant = 0x7D - 40 = 125 - 40 = 85°C
        val result = ObdResponseParser.parse(listOf("7E8 03 41 05 7D", ">"))
        val s = result as ObdResponseParser.ObdResult.Success
        val temp = ObdResponseParser.applyFormula("A-40", s.bytes)
        assertNotNull(temp)
        assertEquals(85.0, temp!!, 0.01)
    }

    @Test fun `formula evaluator - RPM`() {
        val bytes = byteArrayOf(0x0D, 0x24.toByte())
        val rpm = ObdResponseParser.applyFormula("((A*256)+B)/4", bytes)
        assertNotNull(rpm)
        assertEquals(841.0, rpm!!, 0.01)
    }

    @Test fun `formula evaluator - voltage`() {
        // 14.1V → 14100 → A=0x37=55, B=0x14=20
        val bytes = byteArrayOf(0x37, 0x14)
        val v = ObdResponseParser.applyFormula("((A*256)+B)/1000", bytes)
        assertNotNull(v)
        assertEquals(14.1, v!!, 0.01)
    }

    @Test fun `parse VIN with ELM header line`() {
        val result = ObdResponseParser.parse(
            listOf("014", "7E8 10 14 49 02 01 31 56 57", "7E8 21 46 45 32 31 43 30 34", "7E8 22 4D 30 30 30 30 30 31", ">")
        )
        val s = result as ObdResponseParser.ObdResult.Success
        assertEquals(0x09, s.serviceId)
        assertEquals(0x02, s.pid)
    }

    @Test fun `parse UDS negative response`() {
        val result = ObdResponseParser.parse(listOf("7E8 03 7F 22 31", ">"))
        assertTrue(result is ObdResponseParser.ObdResult.Error)
        assertTrue((result as ObdResponseParser.ObdResult.Error).message.contains("0x31"))
    }

    @Test fun `formula returns null when referenced byte is missing`() {
        // EGT formula needs C and D; a 2-byte response must not silently read them as 0.
        assertNull(ObdResponseParser.applyFormula("((C*256)+D)*0.1-40", byteArrayOf(0x11, 0x00)))
    }

    @Test fun `formula with decimal multiplier`() {
        val v = ObdResponseParser.applyFormula("((A*256)+B)*0.05", byteArrayOf(0x00, 0x1E))
        assertEquals(1.5, v!!, 0.0001)
    }

    @Test fun `SimpleExprEval - rejects malformed input`() {
        assertThrows(IllegalArgumentException::class.java) { SimpleExprEval.eval("(1+2") }
        assertThrows(IllegalArgumentException::class.java) { SimpleExprEval.eval("1+2)") }
        assertThrows(IllegalArgumentException::class.java) { SimpleExprEval.eval("1/0") }
        assertThrows(IllegalArgumentException::class.java) { SimpleExprEval.eval("E-40") }
        assertEquals(-5.0, SimpleExprEval.eval("-(2+3)"), 0.001)
    }

    @Test fun `SimpleExprEval - basic arithmetic`() {
        assertEquals(85.0,  SimpleExprEval.eval("125-40"), 0.001)
        assertEquals(841.0, SimpleExprEval.eval("((13*256)+36)/4"), 0.001)
        assertEquals(14.1,  SimpleExprEval.eval("((55*256)+20)/1000"), 0.001)
        assertEquals(20.0,  SimpleExprEval.eval("51*100/255"), 0.001)
    }
}
