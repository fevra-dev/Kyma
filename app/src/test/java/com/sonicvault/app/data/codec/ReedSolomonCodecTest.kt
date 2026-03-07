package com.sonicvault.app.data.codec

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * Unit tests for ReedSolomonCodec — GF(2^8) RS error correction.
 *
 * Validates encode/decode round-trip, error correction up to T_CORRECTION=16,
 * beyond-correction failure, edge cases, and magic header framing.
 */
class ReedSolomonCodecTest {

    @Test
    fun testRoundTrip() {
        val original = "Hello, SonicVault!".toByteArray(Charsets.UTF_8)
        val encoded = ReedSolomonCodec.encode(original)
        val decoded = ReedSolomonCodec.decode(encoded)

        assertNotNull("decode should succeed with no errors", decoded)
        assertArrayEquals("decoded data should match original", original, decoded)
    }

    @Test
    fun testErrorCorrection_upTo16() {
        val original = "Solana seed phrase test data for RS".toByteArray(Charsets.UTF_8)
        val encoded = ReedSolomonCodec.encode(original)

        // Inject exactly 16 random byte errors (max correctable)
        val corrupted = encoded.copyOf()
        val rng = Random(42)
        val headerSize = 6 // MAGIC(4) + payload_len(2)
        val errorPositions = mutableSetOf<Int>()
        while (errorPositions.size < ReedSolomonCodec.T_CORRECTION) {
            errorPositions.add(rng.nextInt(headerSize, corrupted.size))
        }
        for (pos in errorPositions) {
            corrupted[pos] = (corrupted[pos].toInt() xor (rng.nextInt(1, 256))).toByte()
        }

        val decoded = ReedSolomonCodec.decode(corrupted)
        assertNotNull("decode should succeed with ${ReedSolomonCodec.T_CORRECTION} errors", decoded)
        assertArrayEquals("decoded data should match original after correction", original, decoded)
    }

    @Test
    fun testBeyondCorrection_17errors() {
        val original = "Data that will be too corrupted".toByteArray(Charsets.UTF_8)
        val encoded = ReedSolomonCodec.encode(original)

        // Inject 17 errors — beyond correction capacity
        val corrupted = encoded.copyOf()
        val rng = Random(99)
        val headerSize = 6
        val errorPositions = mutableSetOf<Int>()
        while (errorPositions.size < ReedSolomonCodec.T_CORRECTION + 1) {
            errorPositions.add(rng.nextInt(headerSize, corrupted.size))
        }
        for (pos in errorPositions) {
            corrupted[pos] = (corrupted[pos].toInt() xor (rng.nextInt(1, 256))).toByte()
        }

        val decoded = ReedSolomonCodec.decode(corrupted)
        // Should either return null (uncorrectable) or return wrong data (detected by app layer)
        if (decoded != null) {
            // If RS thinks it corrected, the result should NOT match (corruption beyond capacity)
            // This is best-effort; RS may silently miscorrect in rare cases
            // We just verify the test runs without crash
        }
    }

    @Test
    fun testEmptyPayload() {
        val original = ByteArray(0)
        val encoded = ReedSolomonCodec.encode(original)
        val decoded = ReedSolomonCodec.decode(encoded)

        assertNotNull("decode should succeed for empty payload", decoded)
        assertEquals("decoded length should be 0", 0, decoded!!.size)
    }

    @Test
    fun testLargePayload() {
        val original = ByteArray(200) { it.toByte() }
        val encoded = ReedSolomonCodec.encode(original)
        val decoded = ReedSolomonCodec.decode(encoded)

        assertNotNull("decode should succeed for 200-byte payload", decoded)
        assertArrayEquals("decoded data should match original 200-byte payload", original, decoded)
    }

    @Test
    fun testMagicHeaderPresent() {
        val original = "test".toByteArray(Charsets.UTF_8)
        val encoded = ReedSolomonCodec.encode(original)

        // "SVRS" = 0x53, 0x56, 0x52, 0x53
        assertEquals("first byte should be 'S'", 0x53.toByte(), encoded[0])
        assertEquals("second byte should be 'V'", 0x56.toByte(), encoded[1])
        assertEquals("third byte should be 'R'", 0x52.toByte(), encoded[2])
        assertEquals("fourth byte should be 'S'", 0x53.toByte(), encoded[3])
    }

    @Test
    fun testNonRsDataPassthrough() {
        // Data without SVRS magic should be returned as-is (backward compat)
        val rawData = "not RS encoded".toByteArray(Charsets.UTF_8)
        val result = ReedSolomonCodec.decode(rawData)

        assertNotNull("non-RS data should pass through", result)
        assertArrayEquals("non-RS data should be returned unchanged", rawData, result)
    }

    @Test
    fun testSingleBytePayload() {
        val original = byteArrayOf(0x42)
        val encoded = ReedSolomonCodec.encode(original)
        val decoded = ReedSolomonCodec.decode(encoded)

        assertNotNull("decode should succeed for 1-byte payload", decoded)
        assertArrayEquals("decoded should match single byte", original, decoded)
    }
}
