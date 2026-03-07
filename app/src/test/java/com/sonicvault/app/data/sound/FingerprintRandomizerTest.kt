package com.sonicvault.app.data.sound

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for FingerprintRandomizer.
 *
 * Verifies length preservation and no-throw behavior.
 * Full ggwave encode→randomize→decode round-trip requires native lib; use instrumentation test.
 */
class FingerprintRandomizerTest {

    @Test
    fun randomize_preservesLength() {
        val samples = ShortArray(48_000) { (it % 1000).toShort() }
        val randomizer = FingerprintRandomizer()
        val result = randomizer.randomize(samples)
        assertEquals(samples.size, result.size)
    }

    @Test
    fun randomize_handlesShortInput() {
        val samples = ShortArray(1024) { 0 }
        val randomizer = FingerprintRandomizer()
        val result = randomizer.randomize(samples)
        assertEquals(1024, result.size)
    }

    @Test
    fun randomize_doesNotThrow() {
        val samples = ShortArray(48_000) { (kotlin.math.sin(it * 0.01) * 10000).toInt().toShort() }
        val randomizer = FingerprintRandomizer()
        val result = randomizer.randomize(samples)
        assertEquals(samples.size, result.size)
    }

    @Test
    fun randomize_modifiesOutput() {
        val samples = ShortArray(2048) { (kotlin.math.sin(it * 0.05) * 15000).toInt().toShort() }
        val randomizer = FingerprintRandomizer()
        val result = randomizer.randomize(samples)
        assertFalse("randomize should modify spectral envelope", result.contentEquals(samples))
    }

    @Test
    fun randomize_silentInputRemainsShortArray() {
        val samples = ShortArray(2048) { 0 }
        val randomizer = FingerprintRandomizer()
        val result = randomizer.randomize(samples)
        assertArrayEquals("silent input should produce unchanged output (no spectral content)", samples, result)
    }
}
