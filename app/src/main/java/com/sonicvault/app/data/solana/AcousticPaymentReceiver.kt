package com.sonicvault.app.data.solana

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import com.sonicvault.app.data.sound.AudioRecordSourceHelper
import com.sonicvault.app.data.sound.GgwaveDataOverSound
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Listens for acoustic Solana Pay URIs via ggwave.
 *
 * Terminal broadcasts raw URI (no DeadDrop/RS). Uses AUDIBLE_FAST protocol for reliability.
 * Pattern from DeadDropReceiverService: record window → decode → parse.
 *
 * Emits SolanaPayUri when a valid payment request is received.
 */
object AcousticPaymentReceiver {

    /** Duration of each listening window in seconds. */
    private const val LISTEN_WINDOW_SECONDS = 12

    /** Pause between windows when looping. */
    private const val PAUSE_BETWEEN_WINDOWS_MS = 1500L

    /**
     * Flow that emits SolanaPayUri when a valid payment request is received.
     *
     * @param maxWindows max listen windows (0 = unlimited)
     */
    fun receiveFlow(context: Context, maxWindows: Int = 0): Flow<SolanaPayUri> = callbackFlow {
        SonicVaultLogger.i("[AcousticPaymentReceiver] starting, maxWindows=$maxWindows")
        var windowCount = 0

        while (coroutineContext.isActive && (maxWindows == 0 || windowCount < maxWindows)) {
            windowCount++
            SonicVaultLogger.d("[AcousticPaymentReceiver] window $windowCount")

            val samples = recordWindow(context)
            if (samples != null) {
                val payload = GgwaveDataOverSound.decode(samples)
                if (payload != null) {
                    val uri = SolanaPayUri.decode(payload)
                    if (uri != null) {
                        SonicVaultLogger.i("[AcousticPaymentReceiver] received URI: ${uri.recipient.take(8)}...")
                        trySend(uri)
                    }
                }
            }

            if (maxWindows == 0 || windowCount < maxWindows) {
                delay(PAUSE_BETWEEN_WINDOWS_MS)
            }
        }

        SonicVaultLogger.i("[AcousticPaymentReceiver] stopped after $windowCount windows")
        close()
    }.flowOn(Dispatchers.IO)

    /**
     * Records a single listening window from the microphone.
     *
     * @param context Application or Activity context for AudioRecord source selection
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
            SonicVaultLogger.w("[AcousticPaymentReceiver] mic permission denied")
            return null
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }

        val totalSamples = sampleRate * LISTEN_WINDOW_SECONDS
        val allSamples = ShortArray(totalSamples)
        var collected = 0
        val readBuf = ShortArray(bufferSize)

        try {
            recorder.startRecording()
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

        return allSamples
    }
}
