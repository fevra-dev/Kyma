package com.sonicvault.app.data.sound

import com.sonicvault.app.data.stego.FftHelper
import com.sonicvault.app.logging.SonicVaultLogger
import java.security.SecureRandom
import kotlin.math.pow

/**
 * Randomizes the spectral envelope of ultrasonic transmissions (17–22 kHz)
 * to reduce acoustic device fingerprinting.
 *
 * THREAT: MEMS speakers have a unique frequency response above 16 kHz visible
 * in spectrograms. A passive observer could correlate sessions.
 *
 * MITIGATION: Apply per-session random gain curve (±3 dB) in 17–22 kHz band.
 * ggwave tolerates this; decode performance is unchanged.
 *
 * Uses [FftHelper] for FFT/IFFT. At 48 kHz, 1024-point: bins 363–469 ≈ 17–22 kHz.
 */
class FingerprintRandomizer(
    private val sampleRateHz: Int = GgwaveDataOverSound.SAMPLE_RATE,
    private val frameSize: Int = 1024
) {

    companion object {
        private const val FINGERPRINT_FREQ_LOW_HZ = 17_000f
        private const val FINGERPRINT_FREQ_HIGH_HZ = 22_000f
        private const val GAIN_DB_VARIATION = 3.0f
        private const val CONTROL_POINTS = 6
    }

    /**
     * Apply per-session random spectral gain to [waveform] in the 17–22 kHz band.
     *
     * @param waveform 16-bit PCM ShortArray (ggwave-generated waveform)
     * @param sessionId 4-byte hex for logging (non-sensitive)
     * @return Modified waveform with randomized ultrasonic spectral envelope
     */
    fun randomize(waveform: ShortArray, sessionId: String = newSessionId()): ShortArray {
        val gainVector = generateSmoothGainVector()
        SonicVaultLogger.d("[FingerprintRandomizer] applying gain (session $sessionId)")

        val result = waveform.copyOf()
        val frames = waveform.size / frameSize
        val binLow = (FINGERPRINT_FREQ_LOW_HZ / sampleRateHz * frameSize).toInt()
        val binHigh = (FINGERPRINT_FREQ_HIGH_HZ / sampleRateHz * frameSize).toInt()
            .coerceAtMost(frameSize / 2 - 1)

        for (frameIdx in 0 until frames) {
            val offset = frameIdx * frameSize
            val real = DoubleArray(frameSize) { i -> result[offset + i].toDouble() / 32768.0 }
            val imag = DoubleArray(frameSize) { 0.0 }

            FftHelper.fft(real, imag)
            applySpectralGain(real, imag, gainVector, binLow, binHigh)
            FftHelper.ifft(real, imag)

            for (i in 0 until frameSize) {
                val sample = (real[i] * 32768.0).toInt().coerceIn(-32768, 32767)
                result[offset + i] = sample.toShort()
            }
        }

        SonicVaultLogger.d("[FingerprintRandomizer] done: $frames frames, bins $binLow–$binHigh")
        return result
    }

    /**
     * Apply [gainVector] to bins binLow..binHigh and their Hermitian conjugates.
     * Preserves real-output property of IFFT.
     */
    private fun applySpectralGain(
        real: DoubleArray,
        imag: DoubleArray,
        gainVector: List<Float>,
        binLow: Int,
        binHigh: Int
    ) {
        val n = frameSize
        for (i in gainVector.indices) {
            val bin = binLow + i
            val gain = gainVector[i].toDouble()
            real[bin] *= gain
            imag[bin] *= gain
            val conjBin = n - bin
            if (conjBin != bin) {
                real[conjBin] *= gain
                imag[conjBin] *= gain
            }
        }
    }

    private fun generateSmoothGainVector(): List<Float> {
        val rng = SecureRandom()
        val binLow = (FINGERPRINT_FREQ_LOW_HZ / sampleRateHz * frameSize).toInt()
        val binHigh = (FINGERPRINT_FREQ_HIGH_HZ / sampleRateHz * frameSize).toInt()
        val numBins = binHigh - binLow + 1

        val ctrlDb = FloatArray(CONTROL_POINTS) {
            (rng.nextFloat() * 2f - 1f) * GAIN_DB_VARIATION
        }

        return (0 until numBins).map { i ->
            val t = i.toFloat() / numBins * (CONTROL_POINTS - 1)
            val ctrlIdx = t.toInt().coerceAtMost(CONTROL_POINTS - 2)
            val frac = t - ctrlIdx
            val db = ctrlDb[ctrlIdx] * (1f - frac) + ctrlDb[ctrlIdx + 1] * frac
            10f.pow(db / 20f)
        }
    }

    private fun newSessionId(): String {
        val bytes = ByteArray(4).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
