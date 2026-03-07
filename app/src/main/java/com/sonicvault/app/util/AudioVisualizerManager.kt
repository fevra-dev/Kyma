package com.sonicvault.app.util

import android.media.audiofx.Visualizer
import com.sonicvault.app.logging.SonicVaultLogger

/**
 * Wraps android.media.audiofx.Visualizer for real-time FFT and waveform capture.
 * Zero external dependencies — uses Android SDK native audio API.
 *
 * Attach to an AudioTrack session ID during playback to capture speaker output,
 * or use session 0 (global output) for system-wide capture (requires RECORD_AUDIO permission).
 *
 * Rams: functional (no decorative overhead), thorough (handles lifecycle correctly).
 *
 * Usage:
 *   val manager = AudioVisualizerManager(audioSessionId)
 *   manager.start(
 *       onWaveform = { data -> /* normalized 0..1 amplitudes */ },
 *       onFft = { data -> /* magnitude values per frequency bin */ }
 *   )
 *   // ... during playback ...
 *   manager.release()
 */
class AudioVisualizerManager(
    private val audioSessionId: Int = 0
) {
    private var visualizer: Visualizer? = null

    /** Whether the visualizer is currently capturing. */
    val isActive: Boolean get() = visualizer?.enabled == true

    /**
     * Starts capturing real-time audio data.
     * @param captureRate Captures per second (default 20Hz; max depends on device).
     * @param onWaveform Called with normalized amplitude array (0f..1f), 128 samples per callback.
     * @param onFft Called with magnitude values per frequency bin (128 bins).
     */
    fun start(
        captureRate: Int = 20,
        onWaveform: ((FloatArray) -> Unit)? = null,
        onFft: ((FloatArray) -> Unit)? = null
    ) {
        try {
            release() // Clean up any previous instance

            val viz = Visualizer(audioSessionId).apply {
                /* Use 256 capture size for good resolution at low CPU cost */
                captureSize = Visualizer.getCaptureSizeRange()[0].coerceAtLeast(256)
            }

            /* Convert capture rate from Hz to milliHz (Visualizer API uses milliHz) */
            val rateMilliHz = (captureRate * 1000).coerceAtMost(Visualizer.getMaxCaptureRate())

            viz.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (waveform == null || onWaveform == null) return
                        /* Normalize unsigned byte (0..255) to float (0..1) */
                        val normalized = FloatArray(waveform.size) { i ->
                            val unsigned = waveform[i].toInt() and 0xFF
                            kotlin.math.abs(unsigned - 128) / 128f
                        }
                        onWaveform(normalized)
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft == null || onFft == null) return
                        /* Convert FFT byte pairs (real, imaginary) to magnitudes.
                         * First pair is DC + Nyquist; rest are complex pairs. */
                        val magnitudes = FloatArray(fft.size / 2)
                        magnitudes[0] = kotlin.math.abs(fft[0].toFloat()) // DC
                        for (i in 1 until magnitudes.size) {
                            val real = fft[2 * i].toFloat()
                            val imag = fft[2 * i + 1].toFloat()
                            magnitudes[i] = kotlin.math.sqrt(real * real + imag * imag)
                        }
                        /* Normalize to 0..1 range */
                        val max = magnitudes.maxOrNull()?.coerceAtLeast(1f) ?: 1f
                        for (i in magnitudes.indices) {
                            magnitudes[i] = magnitudes[i] / max
                        }
                        onFft(magnitudes)
                    }
                },
                rateMilliHz,
                /* waveform */ onWaveform != null,
                /* fft */ onFft != null
            )

            viz.enabled = true
            visualizer = viz
            SonicVaultLogger.i("[AudioVisualizerManager] Started: session=$audioSessionId, rate=$captureRate Hz, captureSize=${viz.captureSize}")
        } catch (e: Exception) {
            SonicVaultLogger.e("[AudioVisualizerManager] Failed to start: ${e.message}")
            release()
        }
    }

    /**
     * Stops capturing and releases the Visualizer instance.
     * Must be called when playback ends or the screen is disposed.
     */
    fun release() {
        try {
            visualizer?.let { viz ->
                viz.enabled = false
                viz.release()
                SonicVaultLogger.d("[AudioVisualizerManager] Released")
            }
        } catch (e: Exception) {
            SonicVaultLogger.w("[AudioVisualizerManager] Release error: ${e.message}")
        }
        visualizer = null
    }
}
