package com.sonicvault.app.data.stego

import android.graphics.Bitmap
import android.graphics.Color
import com.sonicvault.app.logging.SonicVaultLogger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Griffin-Lim phase reconstruction.
 *
 * Reconstructs audio from magnitude-only spectrogram by iteratively
 * estimating phase via the Griffin-Lim algorithm.
 *
 * This allows the Matryoshka pipeline to:
 * 1. Convert audio -> spectrogram image
 * 2. Embed steganographic payload in the image
 * 3. Reconstruct audio from the (slightly modified) spectrogram
 *
 * The reconstructed audio is perceptually similar to the original,
 * with the steganographic payload hidden in the image layer invisible
 * to any audio analysis.
 *
 * Default iterations: 32 (good balance of quality vs speed).
 */
object GriffinLim {

    private const val FFT_SIZE = 1024
    private const val HOP_SIZE = 512
    private const val FREQ_BINS = FFT_SIZE / 2
    private const val DEFAULT_ITERATIONS = 32

    /**
     * Reconstructs PCM audio from a spectrogram bitmap.
     *
     * @param spectrogram the spectrogram image (x = time, y = frequency, y=0 = high freq)
     * @param iterations number of Griffin-Lim iterations
     * @return reconstructed 16-bit PCM mono audio
     */
    fun reconstruct(spectrogram: Bitmap, iterations: Int = DEFAULT_ITERATIONS): ShortArray {
        val numFrames = spectrogram.width
        val freqBins = spectrogram.height.coerceAtMost(FREQ_BINS)

        SonicVaultLogger.i("[GriffinLim] reconstructing ${numFrames}x${freqBins} spectrogram, $iterations iterations")

        // Extract magnitude from bitmap
        val magnitudes = Array(numFrames) { x ->
            FloatArray(FREQ_BINS) { y ->
                if (y < freqBins) {
                    val pixel = spectrogram.getPixel(x, y)
                    val intensity = Color.red(pixel).toFloat()
                    // Reverse the log-scale applied during spectrogram generation
                    (exp(intensity / 20f) - 1f).coerceAtLeast(0f)
                } else 0f
            }
        }

        // Flip frequency axis (bitmap y=0 is highest freq)
        for (frame in magnitudes) {
            frame.reverse()
        }

        val outputLen = (numFrames - 1) * HOP_SIZE + FFT_SIZE
        var audio = FloatArray(outputLen)
        val hannWindow = FloatArray(FFT_SIZE) { 0.5f * (1 - cos(2.0 * PI * it / (FFT_SIZE - 1))).toFloat() }

        // Initialize with random phase
        val phases = Array(numFrames) { FloatArray(FREQ_BINS) { (Math.random() * 2 * PI).toFloat() } }

        for (iter in 0 until iterations) {
            audio = FloatArray(outputLen)
            val windowSum = FloatArray(outputLen)

            for (frame in 0 until numFrames) {
                val offset = frame * HOP_SIZE
                val real = FloatArray(FFT_SIZE)
                val imag = FloatArray(FFT_SIZE)

                // Build complex spectrum from magnitude + estimated phase
                for (bin in 0 until FREQ_BINS) {
                    real[bin] = magnitudes[frame][bin] * cos(phases[frame][bin].toDouble()).toFloat()
                    imag[bin] = magnitudes[frame][bin] * sin(phases[frame][bin].toDouble()).toFloat()
                }
                // Mirror spectrum for real-valued signal
                for (bin in 1 until FREQ_BINS) {
                    real[FFT_SIZE - bin] = real[bin]
                    imag[FFT_SIZE - bin] = -imag[bin]
                }

                // Inverse FFT
                ifft(real, imag)

                // Overlap-add with window
                for (i in 0 until FFT_SIZE) {
                    if (offset + i < outputLen) {
                        audio[offset + i] += real[i] * hannWindow[i]
                        windowSum[offset + i] += hannWindow[i] * hannWindow[i]
                    }
                }
            }

            // Normalize by window sum
            for (i in audio.indices) {
                if (windowSum[i] > 1e-8f) {
                    audio[i] /= windowSum[i]
                }
            }

            // Re-analyze to get new phases (STFT)
            if (iter < iterations - 1) {
                for (frame in 0 until numFrames) {
                    val offset = frame * HOP_SIZE
                    val real = FloatArray(FFT_SIZE)
                    val imag = FloatArray(FFT_SIZE)

                    for (i in 0 until FFT_SIZE) {
                        if (offset + i < audio.size) {
                            real[i] = audio[offset + i] * hannWindow[i]
                        }
                    }

                    fft(real, imag)

                    // Update phase estimates while keeping original magnitudes
                    for (bin in 0 until FREQ_BINS) {
                        phases[frame][bin] = kotlin.math.atan2(imag[bin], real[bin])
                    }
                }
            }
        }

        // Convert to 16-bit PCM
        var maxAbs = 0f
        for (s in audio) {
            val abs = kotlin.math.abs(s)
            if (abs > maxAbs) maxAbs = abs
        }
        if (maxAbs < 1e-8f) maxAbs = 1f

        val result = ShortArray(audio.size)
        for (i in audio.indices) {
            result[i] = (audio[i] / maxAbs * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }

        SonicVaultLogger.i("[GriffinLim] reconstructed ${result.size} samples")
        return result
    }

    /** Forward FFT (Cooley-Tukey radix-2). */
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        if (n <= 1) return

        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
            var m = n / 2
            while (m >= 1 && j >= m) { j -= m; m /= 2 }
            j += m
        }

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
                    real[even] += tR
                    imag[even] += tI
                }
            }
            step *= 2
        }
    }

    /** Inverse FFT: conjugate input, forward FFT, conjugate output, scale by 1/N. */
    private fun ifft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        for (i in imag.indices) imag[i] = -imag[i]
        fft(real, imag)
        for (i in real.indices) {
            real[i] = real[i] / n
            imag[i] = -imag[i] / n
        }
    }
}
