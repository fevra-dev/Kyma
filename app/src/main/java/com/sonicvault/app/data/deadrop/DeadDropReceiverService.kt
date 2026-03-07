package com.sonicvault.app.data.deadrop

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import com.sonicvault.app.data.receive.FrameValidator
import com.sonicvault.app.data.sound.AudioRecordSourceHelper
import com.sonicvault.app.data.sound.GgwaveDataOverSound
import com.sonicvault.app.data.codec.ReedSolomonCodec
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Result of a single listen event.
 * @property Packet Emitted when a valid Dead Drop packet (DDRP/SVDD/v2) is received.
 * @property EcdhPacket Emitted when ECDH handshake packet (34 bytes, 0x02) is received; bypasses Reed-Solomon.
 * @property FountainDroplet Emitted when fountain droplet (19 bytes, 0xFC) is received; feed to FountainDecoder.
 * @property Done Emitted when listening stops; micUnavailable=true if mic never initialized.
 */
sealed class DeadDropListenEvent {
    data class Packet(val data: ByteArray) : DeadDropListenEvent()
    data class EcdhPacket(val data: ByteArray) : DeadDropListenEvent()
    data class FountainDroplet(val data: ByteArray) : DeadDropListenEvent()
    data class Done(val micUnavailable: Boolean) : DeadDropListenEvent()
}

/**
 * Background Dead Drop receiver that listens for broadcast packets via ggwave.
 *
 * Runs a duty-cycle loop: record N seconds, decode, emit results.
 * Battery-conscious: uses AudioRecord in short bursts rather than continuous.
 *
 * Emits Packet when a valid payload is received; emits Done when listening stops.
 * Done.micUnavailable=true when all windows failed due to mic init (e.g. emulator).
 */
object DeadDropReceiverService {

    /** Duration of each listening window in seconds. */
    private const val LISTEN_WINDOW_SECONDS = 10

    /** Pause between listening windows in milliseconds. */
    private const val PAUSE_BETWEEN_WINDOWS_MS = 2000L

    /**
     * Starts listening for Dead Drop broadcasts.
     *
     * Returns a Flow that emits Packet when a valid Dead Drop packet is received,
     * and Done when listening stops (with micUnavailable if mic never worked).
     *
     * @param context Application or Activity context for AudioRecord source selection
     * @param maxWindows maximum number of listen windows before stopping (0 = unlimited)
     */
    fun listen(context: Context, maxWindows: Int = 6): Flow<DeadDropListenEvent> = flow {
        SonicVaultLogger.i("[DeadDropReceiver] starting listener, maxWindows=$maxWindows")
        var windowCount = 0
        var anyWindowRecorded = false

        while (coroutineContext.isActive && (maxWindows == 0 || windowCount < maxWindows)) {
            windowCount++
            SonicVaultLogger.d("[DeadDropReceiver] window $windowCount/$maxWindows")

            val samples = recordWindow(context)
            if (samples != null) {
                anyWindowRecorded = true
                // Decode via ggwave
                val rawPayload = GgwaveDataOverSound.decode(samples)
                if (rawPayload != null) {
                    // ECDH packets (34 bytes, no Reed-Solomon): route before other handling
                    if (rawPayload.size == 34 && rawPayload[0] == 0x02.toByte() &&
                        (rawPayload[1] == 0x01.toByte() || rawPayload[1] == 0x02.toByte())
                    ) {
                        SonicVaultLogger.i("[DeadDropReceiver] received ECDH packet: type=0x${rawPayload[1].toString(16)}")
                        emit(DeadDropListenEvent.EcdhPacket(rawPayload))
                        continue
                    }
                    // Fountain droplets (19 bytes, 0xFC): route to fountain decoder (handled by ViewModel)
                    if (rawPayload.size == 19 && rawPayload[0] == 0xFC.toByte()) {
                        SonicVaultLogger.d("[DeadDropReceiver] fountain droplet received")
                        emit(DeadDropListenEvent.FountainDroplet(rawPayload))
                        continue
                    }
                    val decoded = ReedSolomonCodec.decode(rawPayload) ?: continue
                    // Strict framing: if looks like SonicVault payload (0x01/0x02), validate then discard (no handler)
                    if (decoded.size >= 2 && (decoded[0] == 0x01.toByte() || decoded[0] == 0x02.toByte())) {
                        when (val result = FrameValidator.validate(decoded)) {
                            is FrameValidator.FrameValidationResult.Rejected -> {
                                SonicVaultLogger.d("[DeadDropReceiver] SonicVault payload rejected: ${result.reason}")
                                continue
                            }
                            is FrameValidator.FrameValidationResult.Accepted -> {
                                SonicVaultLogger.d("[DeadDropReceiver] SonicVault payload format detected; no handler, discarding")
                                continue
                            }
                        }
                    }
                    if (DeadDropEncryptor.isDeadDropPacket(decoded) || DeadDropEncryptor.isPassphrasePacket(decoded)) {
                        SonicVaultLogger.i("[DeadDropReceiver] received Dead Drop packet: ${decoded.size} bytes")
                        emit(DeadDropListenEvent.Packet(decoded))
                    }
                }
            }

            if (maxWindows == 0 || windowCount < maxWindows) {
                delay(PAUSE_BETWEEN_WINDOWS_MS)
            }
        }

        SonicVaultLogger.i("[DeadDropReceiver] listener stopped after $windowCount windows")
        emit(DeadDropListenEvent.Done(micUnavailable = !anyWindowRecorded))
    }.flowOn(Dispatchers.IO)

    /**
     * Computes RMS (root mean square) of PCM samples for debug-level signal strength logging.
     */
    private fun computeRms(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (s in samples) {
            val v = s / 32768.0
            sum += v * v
        }
        return kotlin.math.sqrt(sum / samples.size)
    }

    /**
     * Records a single listening window from the microphone.
     *
     * @param context Application or Activity context for AudioRecord source selection
     * @return PCM samples, or null if recording fails
     */
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
            SonicVaultLogger.w("[DeadDropReceiver] mic permission denied")
            return null
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }

        val totalSamples = sampleRate * LISTEN_WINDOW_SECONDS
        val allSamples = ShortArray(totalSamples)
        var collected = 0
        val startMs = System.currentTimeMillis()

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

        val elapsedMs = System.currentTimeMillis() - startMs
        val rms = computeRms(allSamples)
        SonicVaultLogger.d("[DeadDropReceiver] recordWindow samples=$collected elapsedMs=$elapsedMs rms=$rms")

        return allSamples
    }
}
