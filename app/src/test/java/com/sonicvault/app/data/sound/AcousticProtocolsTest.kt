package com.sonicvault.app.data.sound

import com.sonicvault.app.domain.model.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for AcousticProtocols — protocol ID mapping, payload limits, and helpers.
 */
class AcousticProtocolsTest {

    @Test
    fun toGgwaveProtocol_audible() {
        assertEquals(AcousticProtocols.AUDIBLE_NORMAL, AcousticProtocols.toGgwaveProtocol(Protocol.AUDIBLE, useAudibleFast = false))
        assertEquals(AcousticProtocols.AUDIBLE_FAST, AcousticProtocols.toGgwaveProtocol(Protocol.AUDIBLE, useAudibleFast = true))
    }

    @Test
    fun toGgwaveProtocol_ultrasonic() {
        assertEquals(AcousticProtocols.ULTRASONIC_FAST, AcousticProtocols.toGgwaveProtocol(Protocol.ULTRASONIC, useAudibleFast = false))
        assertEquals(AcousticProtocols.ULTRASONIC_FAST, AcousticProtocols.toGgwaveProtocol(Protocol.ULTRASONIC, useAudibleFast = true))
    }

    @Test
    fun protocolName_returnsNonEmptyForAllIds() {
        for (id in 0..5) {
            val name = AcousticProtocols.protocolName(id)
            assertTrue("protocolName($id) should be non-empty", name.isNotBlank())
            assertFalse("protocolName($id) should not be Unknown", name.startsWith("Unknown"))
        }
    }

    @Test
    fun protocolName_unknownForInvalidId() {
        val name = AcousticProtocols.protocolName(99)
        assertTrue(name.startsWith("Unknown"))
    }

    @Test
    fun maxPayloadBytes_containsAllProtocolIds() {
        val expected = mapOf(
            AcousticProtocols.AUDIBLE_NORMAL to 140,
            AcousticProtocols.AUDIBLE_FAST to 140,
            AcousticProtocols.AUDIBLE_FASTEST to 140,
            AcousticProtocols.ULTRASONIC_NORMAL to 140,
            AcousticProtocols.ULTRASONIC_FAST to 140,
            AcousticProtocols.ULTRASONIC_FASTEST to 140
        )
        for ((id, limit) in expected) {
            assertEquals("MAX_PAYLOAD_BYTES[$id]", limit, AcousticProtocols.MAX_PAYLOAD_BYTES[id])
        }
    }

    @Test
    fun isDssProtocol_ultrasonicTrue() {
        assertTrue(AcousticProtocols.isDssProtocol(AcousticProtocols.ULTRASONIC_NORMAL))
        assertTrue(AcousticProtocols.isDssProtocol(AcousticProtocols.ULTRASONIC_FAST))
        assertTrue(AcousticProtocols.isDssProtocol(AcousticProtocols.ULTRASONIC_FASTEST))
    }

    @Test
    fun isDssProtocol_audibleFalse() {
        assertFalse(AcousticProtocols.isDssProtocol(AcousticProtocols.AUDIBLE_NORMAL))
        assertFalse(AcousticProtocols.isDssProtocol(AcousticProtocols.AUDIBLE_FAST))
        assertFalse(AcousticProtocols.isDssProtocol(AcousticProtocols.AUDIBLE_FASTEST))
    }

    @Test
    fun constants_matchGgwaveEnum() {
        assertEquals(0, AcousticProtocols.AUDIBLE_NORMAL)
        assertEquals(1, AcousticProtocols.AUDIBLE_FAST)
        assertEquals(2, AcousticProtocols.AUDIBLE_FASTEST)
        assertEquals(3, AcousticProtocols.ULTRASONIC_NORMAL)
        assertEquals(4, AcousticProtocols.ULTRASONIC_FAST)
        assertEquals(5, AcousticProtocols.ULTRASONIC_FASTEST)
    }
}
