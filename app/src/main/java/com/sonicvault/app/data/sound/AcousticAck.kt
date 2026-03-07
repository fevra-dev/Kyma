package com.sonicvault.app.data.sound

import android.media.AudioFormat
import android.media.AudioTrack
import com.sonicvault.app.logging.SonicVaultLogger
import kotlin.math.PI
import kotlin.math.sin

/**
 * Plays a short acknowledgment tone after successful transaction broadcast.
 *
 * Plan: 200ms 440Hz tone via AudioTrack. Rams: honest feedback, minimal.
 */
object AcousticAck {

    private const val SAMPLE_RATE = 48000
    private const val FREQUENCY_HZ = 440
    private const val DURATION_MS = 200

    /**
     * Plays a 200ms 440Hz sine tone. Non-blocking; runs on caller's thread.
     * Call from background (e.g. Dispatchers.IO) to avoid blocking UI.
     */
    fun play() {
        val numSamples = (SAMPLE_RATE * DURATION_MS / 1000.0).toInt()
        val samples = ShortArray(numSamples)
        val angularFreq = 2.0 * PI * FREQUENCY_HZ / SAMPLE_RATE

        for (i in 0 until numSamples) {
            val sample = sin(angularFreq * i) * Short.MAX_VALUE * 0.3
            samples[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(bufferSize, samples.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep(DURATION_MS.toLong())
            track.stop()
            SonicVaultLogger.d("[AcousticAck] played 200ms 440Hz tone")
        } catch (e: Exception) {
            SonicVaultLogger.w("[AcousticAck] play failed", e)
        } finally {
            track.release()
        }
    }
}
