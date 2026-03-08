package com.sonicvault.app.data.stego

import android.content.Context
import android.net.Uri
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes audio from URI to PCM (WavContent).
 *
 * ## Supported input formats (cover audio & recovery)
 *
 * | Format | MIME | Notes |
 * |--------|------|-------|
 * | WAV    | audio/wav, audio/x-wav | Native PCM — lossless, best for stego |
 * | FLAC   | audio/flac             | Lossless — preserves LSB stego data |
 * | MP3    | audio/mpeg             | Lossy — fine as cover input, stego destroyed if re-encoded |
 * | OGG    | audio/ogg              | Vorbis or Opus inside OGG container |
 * | M4A    | audio/mp4, audio/aac   | AAC codec — common phone recording format |
 * | Opus   | audio/opus             | Low-latency codec; cuts above ~20 kHz |
 * | AMR    | audio/amr, audio/amr-wb | Voice recordings |
 * | AIFF   | audio/aiff             | Apple lossless — via MediaCodec fallback |
 *
 * ## Important: Lossless vs Lossy for steganography
 *
 * LSB steganography embeds data in the least significant bits of PCM samples.
 * **Only lossless formats (WAV, FLAC) preserve stego data after encoding.**
 * Lossy codecs (MP3, AAC, Opus, Vorbis) destroy LSB bits during compression.
 * Therefore:
 * - **Cover audio INPUT**: Any format works (decoded to PCM first)
 * - **Stego backup OUTPUT**: Must be WAV or FLAC only
 *
 * ## Opus and ultrasonic frequencies
 *
 * Opus typically cuts frequencies above ~20 kHz even in fullband mode.
 * Ultrasonic steganography (15–19.5 kHz) and ggwave ultrasonic transmission
 * would be destroyed by Opus encoding. Use WAV or FLAC for any ultrasonic data.
 */
interface AudioDecoder {
    suspend fun decodeToPcm(uri: Uri): WavContent
}

/**
 * Composite decoder: WAV via [WavAudioHandler], everything else via MediaExtractor + MediaCodec.
 * Output is normalized to mono 16-bit; sample rate preserved or resampled to 44.1 kHz if needed.
 */
class AudioDecoderImpl(
    private val context: Context,
    private val wavHandler: WavAudioHandler
) : AudioDecoder {

    override suspend fun decodeToPcm(uri: Uri): WavContent = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: ""
        val path = uri.toString().lowercase()
        SonicVaultLogger.d("[AudioDecoder] decodeToPcm uri=$uri mime=$mime")

        /* Try WAV first for all audio — handles mislabeled files (e.g. WAV with wrong MIME) and ensures
           correct RIFF chunk parsing. MediaCodec path used only when file is not valid WAV. */
        try {
            val wav = wavHandler.readWav(uri)
            SonicVaultLogger.i("[AudioDecoder] format=WAV (native PCM) samples=${wav.samples.size} rate=${wav.sampleRate}")
            return@withContext wav
        } catch (e: IllegalArgumentException) {
            SonicVaultLogger.d("[AudioDecoder] not WAV: ${e.message}")
        }

        when {
            /* MP3 — very common, MediaCodec decodes to PCM */
            isMp3(mime, path) -> {
                SonicVaultLogger.i("[AudioDecoder] format=MP3 (MediaCodec)")
                decodeWithMediaCodec(uri)
            }
            /* FLAC — lossless, preserves stego data */
            isFlac(mime, path) -> {
                SonicVaultLogger.i("[AudioDecoder] format=FLAC (MediaCodec lossless)")
                decodeWithMediaCodec(uri)
            }
            /* OGG — Vorbis or Opus container */
            isOgg(mime, path) -> {
                SonicVaultLogger.i("[AudioDecoder] format=OGG (MediaCodec)")
                decodeWithMediaCodec(uri)
            }
            /* M4A / AAC — common phone recording format */
            isM4a(mime, path) -> {
                SonicVaultLogger.i("[AudioDecoder] format=M4A/AAC (MediaCodec)")
                decodeWithMediaCodec(uri)
            }
            /* Opus — low-latency codec (note: cuts above ~20 kHz) */
            isOpus(mime, path) -> {
                SonicVaultLogger.i("[AudioDecoder] format=Opus (MediaCodec; note: ultrasonic data lost)")
                decodeWithMediaCodec(uri)
            }
            /* AMR — voice recordings */
            isAmr(mime, path) -> {
                SonicVaultLogger.i("[AudioDecoder] format=AMR (MediaCodec)")
                decodeWithMediaCodec(uri)
            }
            /* Unknown — MediaCodec as generic fallback (WAV already tried above) */
            else -> {
                SonicVaultLogger.d("[AudioDecoder] unknown format mime=$mime path=$path — trying MediaCodec")
                decodeWithMediaCodec(uri)
            }
        }
    }

    /* ── Format detection helpers ── */

    private fun isWav(mime: String, path: String): Boolean =
        mime.contains("wav", ignoreCase = true) || path.endsWith(".wav")

    private fun isMp3(mime: String, path: String): Boolean =
        mime.contains("mpeg", ignoreCase = true) || mime.contains("mp3", ignoreCase = true) || path.endsWith(".mp3")

    private fun isFlac(mime: String, path: String): Boolean =
        mime.contains("flac", ignoreCase = true) || path.endsWith(".flac")

    private fun isOgg(mime: String, path: String): Boolean =
        mime.contains("ogg", ignoreCase = true) || mime.contains("vorbis", ignoreCase = true) ||
                path.endsWith(".ogg") || path.endsWith(".oga")

    private fun isM4a(mime: String, path: String): Boolean =
        mime.contains("mp4", ignoreCase = true) || mime.contains("aac", ignoreCase = true) ||
                mime.contains("m4a", ignoreCase = true) || path.endsWith(".m4a") ||
                path.endsWith(".aac") || path.endsWith(".mp4")

    private fun isOpus(mime: String, path: String): Boolean =
        mime.contains("opus", ignoreCase = true) || path.endsWith(".opus")

    private fun isAmr(mime: String, path: String): Boolean =
        mime.contains("amr", ignoreCase = true) || path.endsWith(".amr") || path.endsWith(".3gp")

    /* ── MediaCodec decoder ── */

    /**
     * Generic MediaCodec decoder: works for MP3, FLAC, OGG, M4A, Opus, AMR, and
     * any format Android's MediaExtractor + MediaCodec pipeline supports.
     * Output: mono 16-bit PCM at 44.1 kHz (resampled if source differs).
     */
    private fun decodeWithMediaCodec(uri: Uri): WavContent {
        val extractor = MediaExtractor().apply {
            try {
                setDataSource(context, uri, null)
            } catch (e: Exception) {
                SonicVaultLogger.e("[AudioDecoder] setDataSource failed for uri=$uri", e)
                throw IllegalArgumentException("Could not open audio file. Ensure the file is a supported format (WAV, FLAC, MP3, OGG, M4A).", e)
            }
        }
        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            val format = extractor.getTrackFormat(i)
            format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IllegalArgumentException("No audio track found in file. Try a different audio file.")
        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/mpeg"
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        SonicVaultLogger.d("[AudioDecoder] MediaCodec: mime=$mime rate=$sampleRate ch=$channelCount")

        val codec = try {
            MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
        } catch (e: Exception) {
            extractor.release()
            if (mime.contains("flac", ignoreCase = true)) {
                throw IllegalArgumentException("FLAC decoding requires Android 13+. Use a WAV backup instead.", e)
            }
            throw IllegalArgumentException("Unsupported audio format: $mime. Try WAV or MP3.", e)
        }
        val bufferInfo = MediaCodec.BufferInfo()
        val outSamples = mutableListOf<Short>()
        var inputDone = false
        var outputDone = false
        val inputBufferTimeout = 5000L
        val outputBufferTimeout = 5000L

        while (!outputDone) {
            if (!inputDone) {
                val inputBufferIndex = codec.dequeueInputBuffer(inputBufferTimeout)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: continue
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }
            }

            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, outputBufferTimeout)
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> { /* no output yet */ }
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    codec.outputFormat
                }
                outputBufferIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex) ?: continue
                    if (bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        val bytes = ByteArray(bufferInfo.size)
                        outputBuffer.get(bytes)
                        outputBuffer.clear()
                        /* PCM 16-bit little-endian (typical MediaCodec output) */
                        val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
                            .asShortBuffer()
                        val arr = ShortArray(shorts.remaining()) { shorts.get() }
                        /* Stereo → mono downmix */
                        if (channelCount == 2) {
                            for (i in arr.indices step 2) {
                                val l = arr[i].toInt()
                                val r = if (i + 1 < arr.size) arr[i + 1].toInt() else 0
                                outSamples.add(((l + r) / 2).toShort())
                            }
                        } else {
                            outSamples.addAll(arr.toList())
                        }
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }
        }
        codec.stop()
        codec.release()
        extractor.release()

        val sampleArray = outSamples.toShortArray()
        /* Resample to 44.1 kHz if source has a different rate */
        val outSamplesArray = if (sampleRate != Constants.DEFAULT_SAMPLE_RATE && sampleRate in 8000..48000) {
            resampleTo(sampleArray, sampleRate, Constants.DEFAULT_SAMPLE_RATE)
        } else sampleArray
        val outSampleRate = if (sampleRate != Constants.DEFAULT_SAMPLE_RATE && sampleRate in 8000..48000) Constants.DEFAULT_SAMPLE_RATE else sampleRate
        SonicVaultLogger.i("[AudioDecoder] decoded: samples=${outSamplesArray.size} rate=$outSampleRate duration=${outSamplesArray.size / outSampleRate.toFloat()}s")
        return WavContent(outSampleRate, 1, outSamplesArray)
    }

    /** Simple linear interpolation resample to target sample rate. */
    private fun resampleTo(samples: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate == toRate) return samples
        val ratio = fromRate.toDouble() / toRate
        val outLen = (samples.size / ratio).toInt()
        return ShortArray(outLen) { i ->
            val srcIdx = i * ratio
            val idx0 = srcIdx.toInt().coerceIn(0, samples.size - 1)
            val idx1 = (idx0 + 1).coerceAtMost(samples.size - 1)
            val frac = srcIdx - idx0
            val v = samples[idx0].toInt() + (samples[idx1].toInt() - samples[idx0].toInt()) * frac
            v.toInt().coerceIn(-32768, 32767).toShort()
        }
    }
}
