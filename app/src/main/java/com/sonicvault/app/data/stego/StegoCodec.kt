package com.sonicvault.app.data.stego

/**
 * Interface for keyed steganography (LSB Matching + PRNG scatter).
 * Defeats Chi-Square, SPA, and RS steganalysis by using ±1 matching
 * and key-derived position scatter.
 *
 * @see LsbMatchingStegoCodec
 */
interface StegoCodec {

    /**
     * Embed [payload] into [coverSamples] using key-derived PRNG positions.
     * Uses first 16 bytes of [keyBytes] to seed position scatter.
     */
    fun embed(
        coverSamples: ShortArray,
        payload: ByteArray,
        keyBytes: ByteArray
    ): ShortArray

    /**
     * Extract [payloadSizeBytes] from [stegoSamples] using same [keyBytes].
     * Payload is prefixed with 4-byte little-endian length header.
     */
    fun extract(
        stegoSamples: ShortArray,
        payloadSizeBytes: Int,
        keyBytes: ByteArray
    ): ByteArray

    /** Max bytes embeddable at 50% capacity (1 bit/sample). */
    fun maxCapacityBytes(sampleCount: Int): Int = sampleCount / 16
}

/** Thrown when payload exceeds cover capacity. */
class StegoCapacityException(
    val requiredBytes: Int,
    val availableBytes: Int
) : Exception(
    "Stego capacity exceeded: need $requiredBytes bytes, have $availableBytes bytes."
)
