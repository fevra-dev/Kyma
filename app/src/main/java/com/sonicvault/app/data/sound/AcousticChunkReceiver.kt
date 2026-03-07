package com.sonicvault.app.data.sound

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.flowOn

/**
 * Receives chunked payloads from acoustic transmission.
 *
 * Records audio → decode via ggwave → parse chunk → collect by SESSION_ID.
 * Emits reassembled payload when all chunks received.
 */
object AcousticChunkReceiver {

    private const val LISTEN_WINDOW_SECONDS = 12
    private const val SAMPLE_RATE = GgwaveDataOverSound.SAMPLE_RATE

    /**
     * Records one window and attempts to decode a chunk. Caller loops for multi-chunk.
     *
     * @param context Application or Activity context for AudioRecord source selection (UNPROCESSED/VOICE_RECOGNITION)
     */
    fun recordAndDecodeChunk(context: Context): AcousticChunker.ChunkData? {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val audioSource = AudioRecordSourceHelper.getAudioSourceForUltrasonic(context)
        val recorder = try {
            AudioRecord(
                audioSource,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
        } catch (e: SecurityException) {
            SonicVaultLogger.w("[AcousticChunkReceiver] mic permission denied")
            return null
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }
        val totalSamples = SAMPLE_RATE * LISTEN_WINDOW_SECONDS
        val allSamples = ShortArray(totalSamples)
        val readBuf = ShortArray(bufferSize)
        try {
            recorder.startRecording()
            var collected = 0
            while (collected < totalSamples) {
                val read = recorder.read(readBuf, 0, readBuf.size)
                if (read > 0) {
                    val toCopy = minOf(read, totalSamples - collected)
                    readBuf.copyInto(allSamples, collected, 0, toCopy)
                    collected += toCopy
                }
            }
            recorder.stop()
        } finally {
            recorder.release()
        }
        val payload = GgwaveDataOverSound.decode(allSamples) ?: return null
        return AcousticChunker.parseChunk(payload)
    }

    /**
     * Flow that emits reassembled payloads when a complete chunk set is received.
     * Loops record/decode until all chunks for a session are collected.
     *
     * @param context Application or Activity context for AudioRecord source selection
     * @param sessionIdFilter Optional session ID filter; null = accept any
     */
    fun receiveFlow(context: Context, sessionIdFilter: Int? = null): Flow<ByteArray> = flow {
        val chunksBySession = mutableMapOf<Int, MutableList<AcousticChunker.ChunkData>>()
        while (coroutineContext.isActive) {
            val chunk = recordAndDecodeChunk(context)
            if (chunk != null && (sessionIdFilter == null || chunk.sessionId == sessionIdFilter)) {
                val list = chunksBySession.getOrPut(chunk.sessionId) { mutableListOf() }
                if (list.none { it.seq == chunk.seq }) list.add(chunk)
                if (list.size == chunk.total) {
                    val reassembled = AcousticChunker.reassemble(list.sortedBy { it.seq })
                    if (reassembled != null) {
                        SonicVaultLogger.i("[AcousticChunkReceiver] reassembled ${reassembled.size} bytes")
                        emit(reassembled)
                        chunksBySession.remove(chunk.sessionId)
                    }
                }
            }
            delay(500)
        }
    }.flowOn(Dispatchers.IO)
}
