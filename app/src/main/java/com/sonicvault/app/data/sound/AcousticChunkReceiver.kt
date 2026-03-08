package com.sonicvault.app.data.sound

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
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

    /** Magic for 64-byte signature envelope: 0x53 0x49 ("SI"). Hot signer receives single-burst. */
    private val SIG_ENVELOPE_MAGIC = byteArrayOf(0x53, 0x49)

    /**
     * Records one window and attempts to decode. Returns chunk if valid chunk, or raw payload for routing.
     *
     * @param context Application or Activity context for AudioRecord source selection
     * @return Pair(chunk or null, rawPayload or null when decode succeeds)
     */
    private fun recordAndDecode(context: Context): Pair<AcousticChunker.ChunkData?, ByteArray?> {
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
            return Pair(null, null)
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return Pair(null, null)
        }
        // Disable AEC/AGC/NS — Android applies these by default; they degrade ggwave decode on Seeker
        val sessionId = recorder.audioSessionId
        AcousticEchoCanceler.create(sessionId)?.apply { enabled = false; release() }
        AutomaticGainControl.create(sessionId)?.apply { enabled = false; release() }
        NoiseSuppressor.create(sessionId)?.apply { enabled = false; release() }
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
        val payload = GgwaveDataOverSound.decode(allSamples) ?: return Pair(null, null)
        return Pair(AcousticChunker.parseChunk(payload), payload)
    }

    /**
     * Records one window and attempts to decode a chunk. Caller loops for multi-chunk.
     *
     * @param context Application or Activity context for AudioRecord source selection
     */
    fun recordAndDecodeChunk(context: Context): AcousticChunker.ChunkData? =
        recordAndDecode(context).first

    /**
     * Flow that emits reassembled payloads when a complete chunk set is received.
     * Also emits raw payloads of specified sizes (for cNFT drop, vote, presence oracle).
     *
     * @param context Application or Activity context for AudioRecord source selection
     * @param sessionIdFilter Optional session ID filter; null = accept any
     * @param rawPayloadSizes When non-null, emit decoded payloads of these sizes (e.g. setOf(8, 33, 40))
     *        when they are not chunks or signature envelopes
     */
    fun receiveFlow(
        context: Context,
        sessionIdFilter: Int? = null,
        rawPayloadSizes: Set<Int>? = null
    ): Flow<ByteArray> = flow {
        val chunksBySession = mutableMapOf<Int, MutableList<AcousticChunker.ChunkData>>()
        while (coroutineContext.isActive) {
            val (chunk, rawPayload) = recordAndDecode(context)
            if (rawPayload != null) {
                if (rawPayload.size == 66 &&
                    rawPayload.size >= SIG_ENVELOPE_MAGIC.size &&
                    rawPayload.copyOfRange(0, SIG_ENVELOPE_MAGIC.size).contentEquals(SIG_ENVELOPE_MAGIC) &&
                    (sessionIdFilter == null || sessionIdFilter == 2)
                ) {
                    val signature = rawPayload.copyOfRange(SIG_ENVELOPE_MAGIC.size, 66)
                    SonicVaultLogger.i("[AcousticChunkReceiver] received 64-byte signature envelope")
                    emit(signature)
                } else if (chunk != null && (sessionIdFilter == null || chunk.sessionId == sessionIdFilter)) {
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
                } else if (rawPayloadSizes != null && rawPayload.size in rawPayloadSizes) {
                    SonicVaultLogger.i("[AcousticChunkReceiver] raw payload ${rawPayload.size} bytes")
                    emit(rawPayload)
                }
            }
            delay(500)
        }
    }.flowOn(Dispatchers.IO)
}
