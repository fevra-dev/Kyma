package com.sonicvault.app.data.entropy

import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.wipe
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Derives cryptographic entropy from ambient audio recordings.
 *
 * Pipeline: Record 30s -> extract 5 feature vectors -> SHA-512(concat) ->
 * HMAC-SHA256 with SecureRandom key -> 128/256 bits of entropy -> BIP39 mnemonic.
 *
 * Feature vectors ensure diverse entropy sources:
 * 1. Raw samples (time-domain signal values)
 * 2. Spectral energy (frequency-domain via simple DFT approximation)
 * 3. Delta features (sample-to-sample differences capture dynamics)
 * 4. Statistical moments (mean, variance, skewness, kurtosis)
 * 5. Zero-crossing rate (captures texture/noise characteristics)
 *
 * SecureRandom mixing ensures output is at least as strong as the system CSPRNG,
 * even if audio is silent or predictable.
 */
object AudioEntropyExtractor {

    /**
     * Extracts entropy from PCM audio samples and returns raw entropy bytes.
     *
     * @param samples 16-bit PCM samples (mono, any sample rate)
     * @param entropyBits desired entropy length: 128 (12 words) or 256 (24 words)
     * @return entropy bytes (16 or 32 bytes), or null if samples too short
     */
    fun extractEntropy(samples: ShortArray, entropyBits: Int = 128): ByteArray? {
        SonicVaultLogger.i("[AudioEntropy] extracting from ${samples.size} samples, target=${entropyBits} bits")

        if (samples.size < 4410) { // ~0.1s @ 44.1kHz minimum
            SonicVaultLogger.w("[AudioEntropy] samples too short: ${samples.size}")
            return null
        }

        var rawFeatures: ByteArray? = null
        var spectralFeatures: ByteArray? = null
        var deltaFeatures: ByteArray? = null
        var statsFeatures: ByteArray? = null
        var zcrFeatures: ByteArray? = null

        try {
            // 1. Raw sample hash
            rawFeatures = hashRawSamples(samples)

            // 2. Spectral energy (simple band-energy approximation)
            spectralFeatures = hashSpectralEnergy(samples)

            // 3. Delta features (sample-to-sample differences)
            deltaFeatures = hashDeltaFeatures(samples)

            // 4. Statistical moments
            statsFeatures = hashStatisticalMoments(samples)

            // 5. Zero-crossing rate
            zcrFeatures = hashZeroCrossingRate(samples)

            // Combine all features via SHA-512
            val combined = ByteArray(rawFeatures.size + spectralFeatures.size +
                    deltaFeatures.size + statsFeatures.size + zcrFeatures.size)
            var offset = 0
            for (f in listOf(rawFeatures, spectralFeatures, deltaFeatures, statsFeatures, zcrFeatures)) {
                f.copyInto(combined, offset)
                offset += f.size
            }

            val sha512 = MessageDigest.getInstance("SHA-512").digest(combined)

            // Mix with SecureRandom via HMAC-SHA256 (defense-in-depth)
            val csrngKey = ByteArray(32)
            SecureRandom().nextBytes(csrngKey)

            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(csrngKey, "HmacSHA256"))
            val mixed = mac.doFinal(sha512)

            csrngKey.wipe()
            combined.wipe()

            val entropyBytes = entropyBits / 8
            val result = mixed.copyOfRange(0, entropyBytes)

            SonicVaultLogger.i("[AudioEntropy] extracted ${result.size} bytes of entropy")
            return result

        } finally {
            rawFeatures?.wipe()
            spectralFeatures?.wipe()
            deltaFeatures?.wipe()
            statsFeatures?.wipe()
            zcrFeatures?.wipe()
        }
    }

    /**
     * Estimates entropy quality of PCM samples on a 0-100 scale.
     *
     * Measures signal variance, zero-crossing rate, and spectral flatness.
     * Higher = more diverse audio = better entropy source.
     */
    fun estimateQuality(samples: ShortArray): Int {
        if (samples.size < 100) return 0

        // Variance (silence detection)
        val mean = samples.map { it.toLong() }.average()
        val variance = samples.map { (it - mean) * (it - mean) }.average()
        val varianceScore = (variance / 1_000_000.0).coerceAtMost(1.0) * 40

        // Zero-crossing rate (texture diversity)
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i] >= 0) != (samples[i - 1] >= 0)) crossings++
        }
        val zcr = crossings.toDouble() / samples.size
        val zcrScore = (zcr * 200).coerceAtMost(1.0) * 30

        // Dynamic range
        val max = samples.maxOrNull()?.toInt() ?: 0
        val min = samples.minOrNull()?.toInt() ?: 0
        val dynamicRange = (max - min).toDouble() / 65536.0
        val dynamicScore = dynamicRange * 30

        return (varianceScore + zcrScore + dynamicScore).toInt().coerceIn(0, 100)
    }

    /** SHA-256 of raw PCM bytes. */
    private fun hashRawSamples(samples: ShortArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = java.nio.ByteBuffer.allocate(samples.size * 2)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (s in samples) buf.putShort(s)
        return md.digest(buf.array())
    }

    /** Band energy approximation: sum |sample|^2 in 8 frequency-proxy windows. */
    private fun hashSpectralEnergy(samples: ShortArray): ByteArray {
        val bands = 8
        val bandSize = samples.size / bands
        if (bandSize < 1) return ByteArray(32)
        val md = MessageDigest.getInstance("SHA-256")
        val buf = java.nio.ByteBuffer.allocate(bands * 8)
            .order(java.nio.ByteOrder.BIG_ENDIAN)
        for (b in 0 until bands) {
            var energy = 0.0
            for (i in b * bandSize until ((b + 1) * bandSize).coerceAtMost(samples.size)) {
                energy += samples[i].toDouble() * samples[i].toDouble()
            }
            buf.putDouble(energy)
        }
        return md.digest(buf.array())
    }

    /** SHA-256 of sample-to-sample deltas. */
    private fun hashDeltaFeatures(samples: ShortArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = java.nio.ByteBuffer.allocate((samples.size - 1) * 2)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in 1 until samples.size) {
            buf.putShort((samples[i] - samples[i - 1]).toShort())
        }
        return md.digest(buf.array())
    }

    /** Hash of statistical moments: mean, variance, skewness, kurtosis. Single-pass to avoid GC pressure. */
    private fun hashStatisticalMoments(samples: ShortArray): ByteArray {
        var sum = 0.0
        for (s in samples) sum += s.toDouble()
        val mean = sum / samples.size

        var sumSq = 0.0
        var sumCube = 0.0
        var sumQuad = 0.0
        for (s in samples) {
            val d = s.toDouble() - mean
            val d2 = d * d
            sumSq += d2
            sumCube += d2 * d
            sumQuad += d2 * d2
        }
        val variance = sumSq / samples.size
        val stdDev = sqrt(variance).coerceAtLeast(1e-10)
        val skewness = sumCube / samples.size / (stdDev * stdDev * stdDev)
        val kurtosis = sumQuad / samples.size / (stdDev * stdDev * stdDev * stdDev)

        val buf = java.nio.ByteBuffer.allocate(32).order(java.nio.ByteOrder.BIG_ENDIAN)
        buf.putDouble(mean)
        buf.putDouble(variance)
        buf.putDouble(skewness)
        buf.putDouble(kurtosis)

        return MessageDigest.getInstance("SHA-256").digest(buf.array())
    }

    /** Hash of windowed zero-crossing rates. */
    private fun hashZeroCrossingRate(samples: ShortArray): ByteArray {
        val windowSize = 1024
        val windows = samples.size / windowSize
        if (windows < 1) return ByteArray(32)
        val md = MessageDigest.getInstance("SHA-256")
        val buf = java.nio.ByteBuffer.allocate(windows * 8)
            .order(java.nio.ByteOrder.BIG_ENDIAN)
        for (w in 0 until windows) {
            var crossings = 0
            val start = w * windowSize
            for (i in start + 1 until start + windowSize) {
                if ((samples[i] >= 0) != (samples[i - 1] >= 0)) crossings++
            }
            buf.putDouble(crossings.toDouble() / windowSize)
        }
        return md.digest(buf.array())
    }
}
