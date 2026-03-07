package com.sonicvault.app.data.receive

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import com.sonicvault.app.data.codec.ReedSolomonCodec
import com.sonicvault.app.data.deadrop.DeadDropEncryptor
import com.sonicvault.app.data.deadrop.PayloadV2Parser
import com.sonicvault.app.data.deadrop.PayloadV2Spec
import com.sonicvault.app.data.solana.SolanaPayUri
import com.sonicvault.app.data.sound.AcousticChunker
import com.sonicvault.app.data.sound.AudioRecordSourceHelper
import com.sonicvault.app.data.sound.GgwaveDataOverSound
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/** UTF-8 "solana:" prefix for Solana Pay URI detection. */
private val SOLANA_PAY_PREFIX = "solana:".toByteArray(Charsets.UTF_8)

/**
 * Result of a single unified listen event.
 * Routes all acoustic packet types to the appropriate handler.
 */
sealed class UnifiedReceiveEvent {
    /** Dead Drop packet (DDRP/SVDD or Payload v2 raw). */
    data class DeadDropPacket(val data: ByteArray) : UnifiedReceiveEvent()
    /** ECDH handshake packet (34 bytes, 0x02 prefix). */
    data class EcdhPacket(val data: ByteArray) : UnifiedReceiveEvent()
    /** Fountain droplet (19 bytes, 0xFC); feed to FountainDecoder. */
    data class FountainDroplet(val data: ByteArray) : UnifiedReceiveEvent()
    /** Solana Pay URI; show payment approval sheet. */
    data class SolanaPayRequest(val uri: SolanaPayUri) : UnifiedReceiveEvent()
    /** Reassembled chunk (session 1 = unsigned TX); show cold sign UI. */
    data class ColdSignTx(val txBytes: ByteArray) : UnifiedReceiveEvent()
    /** Listening stopped; micUnavailable=true if mic never initialized. */
    data class Done(val micUnavailable: Boolean) : UnifiedReceiveEvent()
}

/**
 * Unified acoustic receiver: single listen loop, auto-routes by packet type.
 *
 * Replaces separate DeadDropReceiverService, AcousticPaymentReceiver, and
 * AcousticChunkReceiver for the receive tab. All use GgwaveDataOverSound.decode;
 * routing happens after decode based on first bytes.
 *
 * Packet order: Chunk (0xAC5A) → ECDH → Fountain → Solana Pay → Payload v2 → RS Dead Drop.
 */
object UnifiedAcousticReceiver {

    private const val LISTEN_WINDOW_SECONDS = 10
    private const val PAUSE_BETWEEN_WINDOWS_MS = 2000L

    /**
     * Starts listening for all acoustic packet types.
     *
     * @param context Application or Activity context for AudioRecord source selection
     * @param maxWindows Maximum listen windows before stopping (0 = unlimited)
     */
    fun listen(context: Context, maxWindows: Int = 6): Flow<UnifiedReceiveEvent> = flow {
        SonicVaultLogger.i("[UnifiedReceiver] starting, maxWindows=$maxWindows")
        var windowCount = 0
        var anyWindowRecorded = false
        val chunksBySession = mutableMapOf<Int, MutableList<AcousticChunker.ChunkData>>()

        while (coroutineContext.isActive && (maxWindows == 0 || windowCount < maxWindows)) {
            windowCount++
            SonicVaultLogger.d("[UnifiedReceiver] window $windowCount/$maxWindows")

            val samples = recordWindow(context)
            if (samples != null) {
                anyWindowRecorded = true
                val rawPayload = GgwaveDataOverSound.decode(samples)
                if (rawPayload != null) {
                    for (event in routePayload(rawPayload, chunksBySession)) {
                        emit(event)
                    }
                }
            }

            if (maxWindows == 0 || windowCount < maxWindows) {
                delay(PAUSE_BETWEEN_WINDOWS_MS)
            }
        }

        SonicVaultLogger.i("[UnifiedReceiver] stopped after $windowCount windows")
        emit(UnifiedReceiveEvent.Done(micUnavailable = !anyWindowRecorded))
    }.flowOn(Dispatchers.IO)

    /**
     * Routes raw payload by first bytes; returns events to emit.
     * Order: Chunk → ECDH → Fountain → Solana Pay → Payload v2 → RS Dead Drop.
     */
    private fun routePayload(
        rawPayload: ByteArray,
        chunksBySession: MutableMap<Int, MutableList<AcousticChunker.ChunkData>>
    ): List<UnifiedReceiveEvent> {
        // 1. Chunk (Sonic Safe TX; session 1 = unsigned, cold signer receives)
        if (rawPayload.size >= 2 && rawPayload[0] == 0xAC.toByte() && rawPayload[1] == 0x5A.toByte()) {
            val chunk = AcousticChunker.parseChunk(rawPayload)
            if (chunk != null) {
                val list = chunksBySession.getOrPut(chunk.sessionId) { mutableListOf() }
                if (list.none { it.seq == chunk.seq }) list.add(chunk)
                if (list.size == chunk.total) {
                    val reassembled = AcousticChunker.reassemble(list.sortedBy { it.seq })
                    if (reassembled != null && chunk.sessionId == 1) {
                        SonicVaultLogger.i("[UnifiedReceiver] reassembled cold-sign TX ${reassembled.size} bytes")
                        chunksBySession.remove(chunk.sessionId)
                        return listOf(UnifiedReceiveEvent.ColdSignTx(reassembled))
                    }
                    chunksBySession.remove(chunk.sessionId)
                }
            }
            return emptyList()
        }

        // 2. ECDH (34 bytes, 0x02 prefix; second byte 0x01 or 0x02)
        if (rawPayload.size == 34 && rawPayload[0] == 0x02.toByte() &&
            (rawPayload[1] == 0x01.toByte() || rawPayload[1] == 0x02.toByte())
        ) {
            SonicVaultLogger.i("[UnifiedReceiver] ECDH packet")
            return listOf(UnifiedReceiveEvent.EcdhPacket(rawPayload))
        }

        // 3. Fountain (19 bytes, 0xFC)
        if (rawPayload.size == 19 && rawPayload[0] == 0xFC.toByte()) {
            SonicVaultLogger.d("[UnifiedReceiver] fountain droplet")
            return listOf(UnifiedReceiveEvent.FountainDroplet(rawPayload))
        }

        // 4. Solana Pay ("solana:" prefix)
        if (rawPayload.size >= SOLANA_PAY_PREFIX.size &&
            rawPayload.copyOfRange(0, SOLANA_PAY_PREFIX.size).contentEquals(SOLANA_PAY_PREFIX)
        ) {
            val uri = SolanaPayUri.decode(rawPayload)
            if (uri != null) {
                SonicVaultLogger.i("[UnifiedReceiver] Solana Pay request: ${uri.recipient.take(8)}...")
                return listOf(UnifiedReceiveEvent.SolanaPayRequest(uri))
            }
            return emptyList()
        }

        // 5. Payload v2 (raw, ECDH-secured; not RS-encoded)
        if (rawPayload.size >= PayloadV2Spec.MIN_PACKET_BYTES && PayloadV2Parser.isV2Packet(rawPayload)) {
            SonicVaultLogger.i("[UnifiedReceiver] Payload v2 packet ${rawPayload.size} bytes")
            return listOf(UnifiedReceiveEvent.DeadDropPacket(rawPayload))
        }

        // 6. Dead Drop (RS-encoded: DDRP/SVDD)
        val decoded = ReedSolomonCodec.decode(rawPayload) ?: return emptyList()
        if (decoded.size >= 2 && (decoded[0] == 0x01.toByte() || decoded[0] == 0x02.toByte())) {
            when (val result = FrameValidator.validate(decoded)) {
                is FrameValidator.FrameValidationResult.Rejected -> {
                    SonicVaultLogger.d("[UnifiedReceiver] SonicVault payload rejected: ${result.reason}")
                    return emptyList()
                }
                is FrameValidator.FrameValidationResult.Accepted -> {
                    SonicVaultLogger.d("[UnifiedReceiver] SonicVault payload format; no handler, discarding")
                    return emptyList()
                }
            }
        }
        if (DeadDropEncryptor.isDeadDropPacket(decoded) || DeadDropEncryptor.isPassphrasePacket(decoded)) {
            SonicVaultLogger.i("[UnifiedReceiver] Dead Drop packet ${decoded.size} bytes")
            return listOf(UnifiedReceiveEvent.DeadDropPacket(decoded))
        }
        return emptyList()
    }

    private fun computeRms(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (s in samples) {
            val v = s / 32768.0
            sum += v * v
        }
        return kotlin.math.sqrt(sum / samples.size)
    }

    private fun recordWindow(context: Context): ShortArray? {
        val sampleRate = GgwaveDataOverSound.SAMPLE_RATE
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val audioSource = AudioRecordSourceHelper.getAudioSourceForUltrasonic(context)
        val recorder = try {
            AudioRecord(
                audioSource,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
        } catch (e: SecurityException) {
            SonicVaultLogger.w("[UnifiedReceiver] mic permission denied")
            return null
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }

        val totalSamples = sampleRate * LISTEN_WINDOW_SECONDS
        val allSamples = ShortArray(totalSamples)
        var collected = 0

        try {
            recorder.startRecording()
            while (collected < totalSamples) {
                val readBuf = ShortArray(bufferSize)
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

        val rms = computeRms(allSamples)
        SonicVaultLogger.d("[UnifiedReceiver] recordWindow samples=$collected rms=$rms")
        return allSamples
    }
}
