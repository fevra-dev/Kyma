package com.sonicvault.app.data.sound

import android.media.AudioFormat
import android.media.AudioTrack
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Transmits chunked payload over audio via ggwave.
 *
 * Plays each chunk 2x with 300ms gap. Uses ULTRASONIC for 64B signature,
 * AUDIBLE_FAST for larger TX payloads.
 */
object AcousticTransmitter {

    /**
     * Transmits payload as chunks. Each chunk encoded via ggwave, played twice.
     *
     * @param payload bytes to transmit (e.g. serialized unsigned TX)
     * @param sessionId session identifier for chunk protocol
     * @param applyFingerprintRandomization when true, randomizes ultrasonic spectral envelope
     * @param onChunkEncoded optional callback for progress
     */
    suspend fun transmitChunked(
        payload: ByteArray,
        sessionId: Int,
        applyFingerprintRandomization: Boolean = false,
        onChunkEncoded: ((Int, Int) -> Unit)? = null
    ) {
        val chunks = AcousticChunker.chunk(payload, sessionId)
        for ((idx, chunk) in chunks.withIndex()) {
            val samples = GgwaveDataOverSound.encode(chunk, com.sonicvault.app.domain.model.Protocol.ULTRASONIC, applyFingerprintRandomization = applyFingerprintRandomization)
                ?: continue
            playSamples(samples)
            onChunkEncoded?.invoke(idx + 1, chunks.size)
            delay(AcousticChunker.GAP_BETWEEN_CHUNKS_MS)
            playSamples(samples)
            delay(AcousticChunker.GAP_BETWEEN_CHUNKS_MS)
        }
        SonicVaultLogger.i("[AcousticTransmitter] transmitted ${chunks.size} chunks")
    }

    /**
     * Transmits a small payload (e.g. 64B signature) in a single ggwave burst.
     *
     * @param applyFingerprintRandomization when true, randomizes ultrasonic spectral envelope
     */
    suspend fun transmitSingle(payload: ByteArray, applyFingerprintRandomization: Boolean = false) {
        val samples = GgwaveDataOverSound.encode(payload, com.sonicvault.app.domain.model.Protocol.ULTRASONIC, applyFingerprintRandomization = applyFingerprintRandomization)
            ?: return
        playSamples(samples)
    }

    private suspend fun playSamples(samples: ShortArray) = withContext(Dispatchers.IO) {
        val bufferSize = AudioTrack.getMinBufferSize(
            GgwaveDataOverSound.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(GgwaveDataOverSound.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(bufferSize, samples.size * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            track.play()
            track.write(samples, 0, samples.size)
            val durationMs = samples.size * 1000L / GgwaveDataOverSound.SAMPLE_RATE
            delay(durationMs + 200)
            track.stop()
        } finally {
            track.release()
        }
    }
}
