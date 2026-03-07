package com.sonicvault.app.data.stego

import com.sonicvault.app.logging.SonicVaultLogger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * LSB Matching with PRNG-keyed position scatter.
 *
 * LSB Replacement (old): sample = (sample AND 0xFFFE) OR targetBit
 *   → Detectable by Chi-Square, SPA, RS Analysis
 *
 * LSB Matching (this): if LSB matches target → leave unchanged; else ±1 to flip
 *   → Preserves statistical symmetry; Chi-Square/SPA become insignificant
 *
 * PRNG scatter: embedding positions from key-derived shuffle.
 * Without key, attacker cannot locate embedded bits.
 */
class LsbMatchingStegoCodec : StegoCodec {

    companion object {
        private const val LENGTH_HEADER_BYTES = 4
    }

    override fun embed(
        coverSamples: ShortArray,
        payload: ByteArray,
        keyBytes: ByteArray
    ): ShortArray {
        val totalPayload = framed(payload)
        val requiredBits = totalPayload.size * 8
        val maxBytes = maxCapacityBytes(coverSamples.size)

        SonicVaultLogger.d("[LsbMatching] embed ${payload.size} bytes (+$LENGTH_HEADER_BYTES header) into ${coverSamples.size} samples (capacity=$maxBytes)")
        if (totalPayload.size > maxBytes) {
            throw StegoCapacityException(totalPayload.size, maxBytes)
        }

        /* Phase 4: TPDF dither before LSB embedding — makes LSB distribution indistinguishable from natural quantization noise. */
        val ditheredSamples = applyTpdfDither(coverSamples)

        val embedPositions = generateEmbedPositions(keyBytes, ditheredSamples.size, requiredBits)
        val bits = toBitStream(totalPayload)
        val stegoSamples = ditheredSamples.copyOf()
        val rng = SecureRandom()
        var modifiedCount = 0

        for (bitIndex in 0 until requiredBits) {
            val sampleIndex = embedPositions[bitIndex]
            val targetBit = bits[bitIndex].toInt()
            val currentSample = stegoSamples[sampleIndex].toInt()
            val currentLsb = currentSample and 1

            if (currentLsb != targetBit) {
                val delta = if (rng.nextBoolean()) 1 else -1
                val newSample = (currentSample + delta).coerceIn(-32768, 32767)
                stegoSamples[sampleIndex] = newSample.toShort()
                modifiedCount++
            }
        }

        SonicVaultLogger.i("[LsbMatching] embed complete. Modified $modifiedCount/$requiredBits samples")
        return stegoSamples
    }

    override fun extract(
        stegoSamples: ShortArray,
        payloadSizeBytes: Int,
        keyBytes: ByteArray
    ): ByteArray {
        val totalBytes = payloadSizeBytes + LENGTH_HEADER_BYTES
        val requiredBits = totalBytes * 8

        SonicVaultLogger.d("[LsbMatching] extract $payloadSizeBytes bytes from ${stegoSamples.size} samples")

        // Try HMAC-based positions first (new backups)
        var embedPositions = generateEmbedPositions(keyBytes, stegoSamples.size, requiredBits)
        var bits = IntArray(requiredBits)
        for (bitIndex in 0 until requiredBits) {
            bits[bitIndex] = stegoSamples[embedPositions[bitIndex]].toInt() and 1
        }
        var allBytes = fromBitStream(bits, totalBytes)
        var embeddedLength = ByteBuffer.wrap(allBytes, 0, LENGTH_HEADER_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN).int

        // Legacy fallback: if length mismatch, retry with old Random-based positions
        if (embeddedLength != payloadSizeBytes) {
            SonicVaultLogger.d("[LsbMatching] HMAC length mismatch (header=$embeddedLength expected=$payloadSizeBytes), trying legacy positions")
            val seed = keyBytesToSeed(keyBytes)
            embedPositions = generateEmbedPositionsLegacy(seed, stegoSamples.size, requiredBits)
            for (bitIndex in 0 until requiredBits) {
                bits[bitIndex] = stegoSamples[embedPositions[bitIndex]].toInt() and 1
            }
            allBytes = fromBitStream(bits, totalBytes)
            embeddedLength = ByteBuffer.wrap(allBytes, 0, LENGTH_HEADER_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN).int
            if (embeddedLength != payloadSizeBytes) {
                SonicVaultLogger.w("[LsbMatching] length mismatch: header=$embeddedLength expected=$payloadSizeBytes")
            }
        }

        SonicVaultLogger.i("[LsbMatching] extract complete. ${allBytes.size - LENGTH_HEADER_BYTES} bytes")
        return allBytes.copyOfRange(LENGTH_HEADER_BYTES, totalBytes)
    }

    private fun framed(payload: ByteArray): ByteArray {
        return ByteBuffer.allocate(LENGTH_HEADER_BYTES + payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(payload.size)
            .put(payload)
            .array()
    }

    /** Legacy: used only for fallback when extracting old backups (Random-based positions). */
    private fun keyBytesToSeed(keyBytes: ByteArray): Long {
        require(keyBytes.size >= 16) { "keyBytes must be at least 16 bytes" }
        var seed = 0L
        for (i in 0 until 8) {
            val b = (keyBytes[i].toLong() and 0xFF) xor (keyBytes[i + 8].toLong() and 0xFF)
            seed = seed or (b shl (i * 8))
        }
        return seed
    }

    /**
     * HMAC-SHA256-driven Fisher-Yates shuffle for cryptographically sound position derivation.
     * Amortizes 8 indices per HMAC call (32 bytes / 4 bytes each).
     */
    private fun generateEmbedPositions(
        keyBytes: ByteArray,
        sampleCount: Int,
        requiredBits: Int
    ): IntArray {
        require(keyBytes.size >= 16) { "keyBytes must be at least 16 bytes" }
        val positions = IntArray(sampleCount) { it }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        var counter = 0L
        var hashBuf: ByteArray? = null
        var hashOffset = 32
        for (i in sampleCount - 1 downTo 1) {
            if (hashOffset >= 32) {
                hashBuf = mac.doFinal(ByteBuffer.allocate(8).putLong(counter++).array())
                hashOffset = 0
            }
            val j = (ByteBuffer.wrap(hashBuf!!, hashOffset, 4).int and 0x7FFFFFFF) % (i + 1)
            hashOffset += 4
            positions[i] = positions[j].also { positions[j] = positions[i] }
        }
        return positions.copyOf(requiredBits)
    }

    /**
     * TPDF dither: sum of two uniform [-0.5, 0.5] → triangular [-1, 1].
     * Applied to all samples before LSB embedding so LSB distribution is indistinguishable
     * from natural quantization noise (resists histogram-based steganalysis).
     */
    private fun applyTpdfDither(samples: ShortArray): ShortArray {
        val rng = SecureRandom()
        val out = samples.copyOf()
        for (i in out.indices) {
            val dither = (rng.nextFloat() - 0.5f) + (rng.nextFloat() - 0.5f)
            val v = (out[i].toInt() + dither).toInt().coerceIn(-32768, 32767)
            out[i] = v.toShort()
        }
        return out
    }

    /** Legacy: Random-based positions for backward compat with pre-HMAC backups. */
    private fun generateEmbedPositionsLegacy(
        seed: Long,
        sampleCount: Int,
        requiredBits: Int
    ): IntArray {
        val rng = java.util.Random(seed)
        val positions = IntArray(sampleCount) { it }
        for (i in sampleCount - 1 downTo 1) {
            val j = (rng.nextLong() and Long.MAX_VALUE % (i + 1)).toInt()
            positions[i] = positions[j].also { positions[j] = positions[i] }
        }
        return positions.copyOf(requiredBits)
    }

    private fun toBitStream(data: ByteArray): ByteArray {
        val bits = ByteArray(data.size * 8)
        for (i in data.indices) {
            val b = data[i].toInt() and 0xFF
            for (j in 7 downTo 0) {
                bits[i * 8 + (7 - j)] = ((b shr j) and 1).toByte()
            }
        }
        return bits
    }

    private fun fromBitStream(bits: IntArray, byteCount: Int): ByteArray {
        val result = ByteArray(byteCount)
        for (i in 0 until byteCount) {
            var byte = 0
            for (j in 0 until 8) {
                byte = (byte shl 1) or bits[i * 8 + j]
            }
            result[i] = byte.toByte()
        }
        return result
    }
}
