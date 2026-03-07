package com.sonicvault.app.data.stego

import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.Constants
import java.nio.ByteBuffer

/**
 * Builds hybrid metadata: [version:1][payload_len:4][checksum:32].
 * @param version Payload format version (e.g. PAYLOAD_VERSION_HYBRID).
 * @param payloadLen Length of LSB payload in bytes.
 * @param checksumRaw 32-byte raw SHA-256 of plaintext.
 */
fun buildHybridMetadata(version: Int, payloadLen: Int, checksumRaw: ByteArray): ByteArray {
    require(checksumRaw.size >= 32) { "Checksum must be at least 32 bytes" }
    return ByteBuffer.allocate(Constants.HYBRID_METADATA_BYTES).apply {
        order(java.nio.ByteOrder.BIG_ENDIAN)
        put(version.toByte())
        putInt(payloadLen)
        put(checksumRaw, 0, 32)
    }.array()
}

/**
 * Hybrid steganography: metadata in phase (robust ~40 dB SNR), payload in LSB (high capacity).
 * Survives upload, compression, format conversion better than LSB alone.
 *
 * Metadata format (phase): [version:1][payload_len:4][checksum:32] = 37 bytes.
 * Payload (LSB): encrypted seed. Version 5 = legacy LSB replacement; 7 = LSB Matching + PRNG scatter.
 */
interface HybridSteganography {
    /**
     * Embeds metadata in phase (robust), payload in LSB (high capacity).
     * When [keyBytesForLsb] is non-null, uses LSB Matching (version 7). Otherwise legacy (version 5).
     */
    fun embed(
        coverSamples: ShortArray,
        metadata: ByteArray,
        payload: ByteArray,
        keyBytesForLsb: ByteArray? = null
    ): ShortArray

    /**
     * Extracts metadata from phase, payload from LSB.
     * When metadata version is 7, [keyBytesForLsb] must be provided. For version 5, ignored.
     * @return Pair(metadata, payload) or null if phase extraction fails.
     */
    fun extract(stegoSamples: ShortArray, keyBytesForLsb: ByteArray? = null): Pair<ByteArray, ByteArray>?
}

/**
 * Combines [PhaseSteganography] (metadata) and [LSBSteganography] or [LsbMatchingStegoCodec] (payload).
 * Phase first for robustness; LSB for capacity.
 */
class HybridSteganographyImpl(
    private val lsb: LSBSteganography,
    private val lsbMatching: LsbMatchingStegoCodec,
    private val phase: PhaseSteganography
) : HybridSteganography {

    private val phaseBlockSize = 512
    private val phaseBinIndices = intArrayOf(1, 2)
    /** Single-bin fallback for backups created before multi-bin phase was introduced. */
    private val legacyPhaseBinIndices = intArrayOf(1)

    override fun embed(
        coverSamples: ShortArray,
        metadata: ByteArray,
        payload: ByteArray,
        keyBytesForLsb: ByteArray?
    ): ShortArray {
        SonicVaultLogger.i("[HybridStego] embed metadata=${metadata.size} payload=${payload.size} lsbMatching=${keyBytesForLsb != null}")
        require(metadata.size == Constants.HYBRID_METADATA_BYTES) {
            "Metadata must be ${Constants.HYBRID_METADATA_BYTES} bytes, got ${metadata.size}"
        }
        val phaseSamples = phase.embed(coverSamples, metadata, phaseBlockSize, phaseBinIndices)
        return if (keyBytesForLsb != null) {
            lsbMatching.embed(phaseSamples, payload, keyBytesForLsb)
        } else {
            lsb.embed(phaseSamples, payload, Constants.DEFAULT_NUM_LSB)
        }
    }

    override fun extract(stegoSamples: ShortArray, keyBytesForLsb: ByteArray?): Pair<ByteArray, ByteArray>? {
        // Try multi-bin first (new backups), fall back to single-bin (pre-multi-bin backups)
        return tryExtractWithBins(stegoSamples, keyBytesForLsb, phaseBinIndices)
            ?: tryExtractWithBins(stegoSamples, keyBytesForLsb, legacyPhaseBinIndices)
    }

    /**
     * Attempts phase metadata extraction using the given bin indices, then extracts
     * the LSB payload. Returns null if phase metadata is invalid.
     */
    private fun tryExtractWithBins(
        stegoSamples: ShortArray,
        keyBytesForLsb: ByteArray?,
        binIndices: IntArray
    ): Pair<ByteArray, ByteArray>? {
        return try {
            val metadata = phase.extract(stegoSamples, Constants.HYBRID_METADATA_BYTES, phaseBlockSize, binIndices)
            val payloadLen = ByteBuffer.wrap(metadata, 1, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
            if (payloadLen <= 0 || payloadLen > 10_000) {
                SonicVaultLogger.d("[HybridStego] extract bins=${binIndices.toList()} invalid payload_len=$payloadLen")
                return null
            }
            val version = metadata[0].toInt() and 0xFF
            val payload = if (version == Constants.PAYLOAD_VERSION_HYBRID_LSB_MATCHING && keyBytesForLsb != null) {
                lsbMatching.extract(stegoSamples, payloadLen, keyBytesForLsb)
            } else {
                lsb.extract(stegoSamples, payloadLen, Constants.DEFAULT_NUM_LSB)
            }
            SonicVaultLogger.i("[HybridStego] extract metadata=${metadata.size} payload=${payload.size} version=$version bins=${binIndices.toList()}")
            Pair(metadata, payload)
        } catch (e: Exception) {
            SonicVaultLogger.d("[HybridStego] extract bins=${binIndices.toList()} failed: ${e.message}")
            null
        }
    }
}
