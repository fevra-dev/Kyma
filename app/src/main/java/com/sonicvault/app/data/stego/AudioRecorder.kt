package com.sonicvault.app.data.stego

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import com.sonicvault.app.data.sound.AudioRecordSourceHelper
import com.sonicvault.app.data.sound.GgwaveDataOverSound
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records audio from the microphone to a WAV file (PCM 16-bit mono).
 * Used as "in-app recording" cover source for backup and for sound receive.
 *
 * @param sampleRate Use 48 kHz for ggwave sound receive; 44.1 kHz for LSB cover.
 */
interface AudioRecorder {
    /**
     * Records for [durationSeconds] and writes to a temp WAV file.
     * @param durationSeconds Record duration in seconds.
     * @param sampleRate Sample rate (default 44100; use 48000 for ggwave sound receive).
     * @param onAmplitude Optional callback invoked per buffer read with RMS amplitude 0f..1f. Called from IO thread.
     * @return Uri of the recorded WAV file, or failure via exception.
     */
    suspend fun recordToWav(
        durationSeconds: Int,
        sampleRate: Int = Constants.DEFAULT_SAMPLE_RATE,
        onAmplitude: ((Float) -> Unit)? = null
    ): Result<Uri>

    /**
     * Records until [stopRequested] returns true, with minimum [minDurationSeconds] (e.g. 5s for ggwave).
     * User-controlled duration: start recording, then stop when ready.
     * @param minDurationSeconds Minimum record time (ggwave needs ~5s).
     * @param stopRequested Called each buffer; when true, stop after current buffer (if past min).
     * @param onAmplitude Optional callback for waveform visualization.
     * @param onElapsedSeconds Called when each second elapses.
     * @return Uri of the recorded WAV file.
     */
    suspend fun recordToWavUntilStopped(
        minDurationSeconds: Int,
        stopRequested: () -> Boolean,
        sampleRate: Int = Constants.DEFAULT_SAMPLE_RATE,
        onAmplitude: ((Float) -> Unit)? = null,
        onElapsedSeconds: (Int) -> Unit = {}
    ): Result<Uri>
}

class AudioRecorderImpl(private val context: Context) : AudioRecorder {

    override suspend fun recordToWav(
        durationSeconds: Int,
        sampleRate: Int,
        onAmplitude: ((Float) -> Unit)?
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSizeBytes = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            .coerceAtLeast(sampleRate * 2 * durationSeconds) // at least 2 bytes per sample * duration

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSizeBytes
            )
        } catch (e: SecurityException) {
            SonicVaultLogger.e("[AudioRecorder] RECORD_AUDIO permission required", e)
            return@withContext Result.failure(e)
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            SonicVaultLogger.e("[AudioRecorder] AudioRecord not initialized")
            recorder.release()
            return@withContext Result.failure(IllegalStateException("Microphone access denied or unavailable."))
        }

        val totalSamples = sampleRate * durationSeconds
        val samples = ShortArray(totalSamples)
        try {
            recorder.startRecording()
            var recorded = 0
            while (recorded < totalSamples) {
                val toRead = minOf(bufferSizeBytes / 2, totalSamples - recorded)
                val read = recorder.read(samples, recorded, toRead)
                if (read <= 0) break
                /* Emit RMS amplitude for waveform visualization (0f..1f). */
                onAmplitude?.let { cb ->
                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val s = samples[recorded + i] / 32768f
                        sumSq += s * s
                    }
                    val rms = kotlin.math.sqrt(sumSq / read).toFloat()
                    cb(rms)
                }
                recorded += read
            }
            recorder.stop()
        } catch (e: Exception) {
            SonicVaultLogger.e("[AudioRecorder] record failed", e)
            recorder.release()
            return@withContext Result.failure(e)
        }
        recorder.release()

        val file = File(context.cacheDir, "sonicvault_recorded_${System.currentTimeMillis()}.wav")
        try {
            FileOutputStream(file).use { out ->
                writeWavHeader(out, samples.size, sampleRate)
                val byteBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                samples.forEach { byteBuffer.putShort(it) }
                out.write(byteBuffer.array())
            }
        } catch (e: Exception) {
            SonicVaultLogger.e("[AudioRecorder] write WAV failed", e)
            file.delete()
            return@withContext Result.failure(e)
        }
        SonicVaultLogger.d("[AudioRecorder] recorded file=${file.absolutePath} samples=${samples.size}")
        Result.success(Uri.fromFile(file))
    }

    override suspend fun recordToWavUntilStopped(
        minDurationSeconds: Int,
        stopRequested: () -> Boolean,
        sampleRate: Int,
        onAmplitude: ((Float) -> Unit)?,
        onElapsedSeconds: (Int) -> Unit
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSizeBytes = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            .coerceAtLeast(sampleRate * 2) // 1 second buffer minimum

        // Use UNPROCESSED/VOICE_RECOGNITION for ultrasonic (48 kHz ggwave); MIC for audible
        val audioSource = if (sampleRate == GgwaveDataOverSound.SAMPLE_RATE) {
            AudioRecordSourceHelper.getAudioSourceForUltrasonic(context)
        } else {
            MediaRecorder.AudioSource.MIC
        }
        val recorder = try {
            AudioRecord(
                audioSource,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSizeBytes * 2
            )
        } catch (e: SecurityException) {
            SonicVaultLogger.e("[AudioRecorder] RECORD_AUDIO permission required", e)
            return@withContext Result.failure(e)
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            SonicVaultLogger.e("[AudioRecorder] AudioRecord not initialized")
            recorder.release()
            return@withContext Result.failure(IllegalStateException("Microphone access denied or unavailable."))
        }

        val samples = mutableListOf<Short>()
        val minSamples = sampleRate * minDurationSeconds
        var lastElapsedSec = -1
        try {
            recorder.startRecording()
            val readBuf = ShortArray(bufferSizeBytes / 2)
            while (true) {
                val read = recorder.read(readBuf, 0, readBuf.size)
                if (read <= 0) break
                for (i in 0 until read) samples.add(readBuf[i])
                onAmplitude?.let { cb ->
                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val s = readBuf[i] / 32768f
                        sumSq += s * s
                    }
                    val rms = kotlin.math.sqrt(sumSq / read).toFloat()
                    cb(rms)
                }
                val elapsedSec = samples.size / sampleRate
                if (elapsedSec != lastElapsedSec) {
                    lastElapsedSec = elapsedSec
                    onElapsedSeconds(elapsedSec)
                }
                if (samples.size >= minSamples && stopRequested()) break
            }
            recorder.stop()
        } catch (e: Exception) {
            SonicVaultLogger.e("[AudioRecorder] record failed", e)
            recorder.release()
            return@withContext Result.failure(e)
        }
        recorder.release()

        if (samples.size < minSamples) {
            return@withContext Result.failure(IllegalStateException("Recording too short. Need at least ${minDurationSeconds}s."))
        }

        val sampleArray = samples.toShortArray()
        val file = File(context.cacheDir, "sonicvault_recorded_${System.currentTimeMillis()}.wav")
        try {
            FileOutputStream(file).use { out ->
                writeWavHeader(out, sampleArray.size, sampleRate)
                val byteBuffer = ByteBuffer.allocate(sampleArray.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                sampleArray.forEach { byteBuffer.putShort(it) }
                out.write(byteBuffer.array())
            }
        } catch (e: Exception) {
            SonicVaultLogger.e("[AudioRecorder] write WAV failed", e)
            file.delete()
            return@withContext Result.failure(e)
        }
        SonicVaultLogger.d("[AudioRecorder] recorded file=${file.absolutePath} samples=${sampleArray.size}")
        Result.success(Uri.fromFile(file))
    }

    private fun writeWavHeader(out: FileOutputStream, numSamples: Int, sampleRate: Int) {
        val dataSize = numSamples * 2
        val buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        out.write("RIFF".toByteArray())
        out.write(intToBytes(36 + dataSize))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(intToBytes(16))
        out.write(shortToBytes(1))
        out.write(shortToBytes(1))
        out.write(intToBytes(sampleRate))
        out.write(intToBytes(sampleRate * 2))
        out.write(shortToBytes(2))
        out.write(shortToBytes(16))
        out.write("data".toByteArray())
        out.write(intToBytes(dataSize))
    }

    private fun intToBytes(v: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).apply { putInt(v) }.array()
    private fun shortToBytes(v: Int): ByteArray = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).apply { putShort(v.toShort()) }.array()
}
