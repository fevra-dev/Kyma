package com.sonicvault.app.data.stego

import android.graphics.Bitmap
import android.graphics.Color
import com.sonicvault.app.logging.SonicVaultLogger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generates spectrogram images from PCM audio for the Matryoshka pipeline.
 *
 * Computes a Short-Time Fourier Transform (STFT) to produce a frequency-time
 * representation, then renders as a bitmap for image LSB embedding.
 *
 * The resulting spectrogram image is a lossless PNG that preserves all the
 * frequency information needed for Griffin-Lim reconstruction.
 *
 * FFT window: Hann, size 1024, hop 512 (50% overlap).
 */
object SpectrogramGenerator {

    private const val FFT_SIZE = 1024
    private const val HOP_SIZE = 512
    private const val FREQ_BINS = FFT_SIZE / 2

    /**
     * Generates a spectrogram bitmap from PCM audio.
     *
     * @param samples 16-bit PCM mono samples
     * @param sampleRate sample rate in Hz
     * @return spectrogram as Bitmap (width = time frames, height = frequency bins)
     */
    fun generate(samples: ShortArray, sampleRate: Int = 44100): Bitmap {
        SonicVaultLogger.i("[SpectrogramGen] generating from ${samples.size} samples @ ${sampleRate}Hz")

        val numFrames = (samples.size - FFT_SIZE) / HOP_SIZE + 1
        if (numFrames <= 0) {
            // Return minimal bitmap for very short audio
            return Bitmap.createBitmap(1, FREQ_BINS, Bitmap.Config.ARGB_8888)
        }

        // Compute STFT magnitudes
        val magnitudes = Array(numFrames) { FloatArray(FREQ_BINS) }
        val hannWindow = FloatArray(FFT_SIZE) { 0.5f * (1 - cos(2.0 * PI * it / (FFT_SIZE - 1))).toFloat() }

        for (frame in 0 until numFrames) {
            val offset = frame * HOP_SIZE
            val real = FloatArray(FFT_SIZE)
            val imag = FloatArray(FFT_SIZE)

            // Apply window
            for (i in 0 until FFT_SIZE) {
                val sampleIdx = offset + i
                if (sampleIdx < samples.size) {
                    real[i] = samples[sampleIdx].toFloat() / Short.MAX_VALUE * hannWindow[i]
                }
            }

            // In-place FFT (Cooley-Tukey radix-2)
            fft(real, imag)

            // Magnitude spectrum (first half only — symmetric)
            for (bin in 0 until FREQ_BINS) {
                magnitudes[frame][bin] = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
            }
        }

        // Find global max for normalization
        var maxMag = 0f
        for (frame in magnitudes) {
            for (mag in frame) {
                if (mag > maxMag) maxMag = mag
            }
        }
        if (maxMag == 0f) maxMag = 1f

        // Render to bitmap: x = time, y = frequency (y=0 = highest freq)
        val bitmap = Bitmap.createBitmap(numFrames, FREQ_BINS, Bitmap.Config.ARGB_8888)
        for (x in 0 until numFrames) {
            for (y in 0 until FREQ_BINS) {
                // Map magnitude to grayscale via log scale
                val mag = magnitudes[x][FREQ_BINS - 1 - y] / maxMag
                val logMag = (20f * ln(mag.coerceAtLeast(1e-10f) + 1f)).coerceIn(0f, 255f)
                val intensity = logMag.toInt()
                bitmap.setPixel(x, y, Color.argb(255, intensity, intensity, intensity))
            }
        }

        SonicVaultLogger.i("[SpectrogramGen] generated ${numFrames}x${FREQ_BINS} spectrogram")
        return bitmap
    }

    /**
     * In-place Cooley-Tukey radix-2 FFT.
     * Arrays must have power-of-2 length.
     */
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        if (n <= 1) return

        // Bit reversal permutation
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var m = n / 2
            while (m >= 1 && j >= m) {
                j -= m
                m /= 2
            }
            j += m
        }

        // FFT butterfly
        var step = 2
        while (step <= n) {
            val halfStep = step / 2
            val angleStep = -2.0 * PI / step
            for (group in 0 until n step step) {
                for (pair in 0 until halfStep) {
                    val angle = angleStep * pair
                    val wr = cos(angle).toFloat()
                    val wi = sin(angle).toFloat()
                    val even = group + pair
                    val odd = group + pair + halfStep
                    val tR = wr * real[odd] - wi * imag[odd]
                    val tI = wr * imag[odd] + wi * real[odd]
                    real[odd] = real[even] - tR
                    imag[odd] = imag[even] - tI
                    real[even] = real[even] + tR
                    imag[even] = imag[even] + tI
                }
            }
            step *= 2
        }
    }
}
