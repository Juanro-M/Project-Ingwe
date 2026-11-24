package io.ingwe.dash

import app.cash.turbine.test
import io.ingwe.dash.data.transport.MockTransport
import io.ingwe.dash.data.transport.ConnectionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MockTransportTest {

    private lateinit var transport: MockTransport

    @Before
    fun setUp() {
        transport = MockTransport(responseDelayMs = 0)
    }

    @Test fun `connection state transitions correctly`() = runTest {
        transport.connectionState.test {
            assertEquals(ConnectionState.DISCONNECTED, awaitItem())
            transport.connect()
            assertEquals(ConnectionState.CONNECTING, awaitItem())
            assertEquals(ConnectionState.CONNECTED, awaitItem())
            transport.disconnect()
            assertEquals(ConnectionState.DISCONNECTED, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `ATZ returns ELM327 version line`() = runTest {
        transport.connect()
        val lines = mutableListOf<String>()
        transport.send("ATZ").collect { lines.add(it) }
        assertTrue("ELM327 v1.5" in lines)
        assertTrue(">" in lines)
    }

    @Test fun `RPM PID returns correct frame`() = runTest {
        transport.connect()
        val lines = mutableListOf<String>()
        transport.send("010C1").collect { lines.add(it) }
        // Expect "7E8 04 41 0C 0D 24" and ">"
        assertTrue(lines.any { it.contains("41 0C") })
        assertTrue(">" in lines)
    }

    @Test fun `unsupported PID returns NO DATA`() = runTest {
        transport.connect()
        val lines = mutableListOf<String>()
        transport.send("01FF1").collect { lines.add(it) }
        assertTrue("NO DATA" in lines)
    }

    @Test fun `CAN ERROR simulation works`() = runTest {
        transport.connect()
        transport.simulateCanError = true
        val lines = mutableListOf<String>()
        transport.send("010C1").collect { lines.add(it) }
        assertTrue("CAN ERROR" in lines)
    }

    @Test fun `custom response override works`() = runTest {
        transport.connect()
        transport.customResponses["ATTEST"] = listOf("CUSTOM_RESPONSE")
        val lines = mutableListOf<String>()
        transport.send("ATTEST").collect { lines.add(it) }
        assertTrue("CUSTOM_RESPONSE" in lines)
    }

    @Test fun `blocked service throws before sending`() = runTest {
        transport.connect()
        val ex = runCatching { transport.send("04").collect { } }
        assertTrue(ex.isFailure)
        assertTrue(ex.exceptionOrNull() is IllegalArgumentException)
    }
}
