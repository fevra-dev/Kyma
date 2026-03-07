package com.sonicvault.app.data.stego

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Minimal in-place radix-2 FFT for complex signals (power-of-2 length).
 * Used by phase coding steganography. Real and imaginary parts in separate arrays.
 */
object FftHelper {

    /**
     * In-place FFT. [real] and [imag] must have same length, power of 2.
     * On return, real/imag hold the frequency-domain values.
     */
    fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        require(n == imag.size && n > 0 && (n and (n - 1)) == 0) { "Length must be power of 2" }
        bitReversePermute(real, imag)
        var len = 2
        while (len <= n) {
            val angle = -2.0 * Math.PI / len
            val wlenReal = cos(angle)
            val wlenImag = sin(angle)
            var i = 0
            while (i < n) {
                var wReal = 1.0
                var wImag = 0.0
                for (j in 0 until len / 2) {
                    val u = i + j
                    val t = i + j + len / 2
                    val tReal = real[t] * wReal - imag[t] * wImag
                    val tImag = real[t] * wImag + imag[t] * wReal
                    real[t] = real[u] - tReal
                    imag[t] = imag[u] - tImag
                    real[u] += tReal
                    imag[u] += tImag
                    val nextWReal = wReal * wlenReal - wImag * wlenImag
                    val nextWImag = wReal * wlenImag + wImag * wlenReal
                    wReal = nextWReal
                    wImag = nextWImag
                }
                i += len
            }
            len *= 2
        }
    }

    /**
     * In-place inverse FFT. Same layout as [fft]; after call, real/imag hold time-domain signal.
     */
    fun ifft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        for (i in imag.indices) imag[i] = -imag[i]
        fft(real, imag)
        for (i in 0 until n) {
            real.set(i, real.get(i) / n)
            imag.set(i, -imag.get(i) / n)
        }
    }

    private fun bitReversePermute(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                real[i] = real[j].also { real[j] = real[i] }
                imag[i] = imag[j].also { imag[j] = imag[i] }
            }
            var k = n / 2
            while (k <= j) {
                j -= k
                k /= 2
            }
            j += k
        }
    }

    /** Magnitude at index [i]. */
    fun magnitude(real: DoubleArray, imag: DoubleArray, i: Int): Double =
        sqrt(real[i] * real[i] + imag[i] * imag[i])

    /** Phase (angle) at index [i] in [-π, π]. */
    fun phase(real: DoubleArray, imag: DoubleArray, i: Int): Double =
        kotlin.math.atan2(imag[i], real[i])

    /** Set phase at index [i] to [phaseRad] while keeping magnitude. */
    fun setPhase(real: DoubleArray, imag: DoubleArray, i: Int, phaseRad: Double) {
        val mag = magnitude(real, imag, i)
        real[i] = mag * cos(phaseRad)
        imag[i] = mag * sin(phaseRad)
    }
}
