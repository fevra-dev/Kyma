package com.sonicvault.app.data.stego

import android.content.Context
import android.net.Uri
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads and writes 16-bit PCM WAV (mono or stereo; stereo converted to mono on read).
 * Properly parses RIFF chunk structure — skips LIST, JUNK, INFO, etc. between fmt and data
 * so only actual PCM samples are read. Fixes wrong duration and white-noise corruption
 * when cover WAVs contain metadata chunks.
 */
interface WavAudioHandler {
    suspend fun readWav(uri: Uri): WavContent
    suspend fun writeWav(samples: ShortArray, sampleRate: Int, outputUri: Uri? = null): Uri
}

data class WavContent(val sampleRate: Int, val channelCount: Int, val samples: ShortArray)

class WavAudioHandlerImpl(private val context: Context) : WavAudioHandler {

    override suspend fun readWav(uri: Uri): WavContent = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val reader = DataInputStream(input)
            val (sampleRate, channelCount, dataBytes) = parseWavChunks(reader)
            val shortBuffer = ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            val shortArray = ShortArray(dataBytes.size / 2) { shortBuffer.short }
            val samples = if (channelCount == 2 && shortArray.size >= 2) {
                shortArray.toList().chunked(2).map { (l, r) -> ((l.toInt() + r) / 2).toShort() }.toShortArray()
            } else {
                shortArray
            }
            SonicVaultLogger.d("[WavAudioHandler] readWav uri=$uri samples=${samples.size} sampleRate=$sampleRate duration=${samples.size / sampleRate.toFloat()}s")
            WavContent(sampleRate, 1, samples)
        } ?: throw IllegalArgumentException("Could not open uri: $uri")
    }

    /**
     * Parses RIFF/WAV structure, skips LIST/JUNK/INFO chunks, reads only the data chunk.
     * @return Triple(sampleRate, channelCount, raw PCM bytes)
     */
    private fun parseWavChunks(reader: DataInputStream): Triple<Int, Int, ByteArray> {
        val riff = ByteArray(4)
        reader.readFully(riff)
        if (String(riff, Charsets.US_ASCII) != "RIFF") throw IllegalArgumentException("Not a RIFF WAV")
        readLittleEndianInt(reader) // file size - 8 (unused)
        val wave = ByteArray(4)
        reader.readFully(wave)
        if (String(wave, Charsets.US_ASCII) != "WAVE") throw IllegalArgumentException("Not WAVE format")

        var sampleRate = 0
        var channelCount = 0
        var dataBytes: ByteArray? = null

        while (true) {
            val chunkId = ByteArray(4)
            if (reader.read(chunkId) != 4) break
            val chunkSize = readLittleEndianInt(reader) and 0x7FFF_FFFF // avoid sign issues for large files
            val chunkIdStr = String(chunkId, Charsets.US_ASCII)

            when (chunkIdStr) {
                "fmt " -> {
                    val fmtData = ByteArray(chunkSize)
                    reader.readFully(fmtData)
                    if (fmtData.size >= 16) {
                        val format = ByteBuffer.wrap(fmtData, 0, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                        channelCount = ByteBuffer.wrap(fmtData, 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                        sampleRate = ByteBuffer.wrap(fmtData, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                        val bitsPerSample = ByteBuffer.wrap(fmtData, 14, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                        if (format != 1 || bitsPerSample != 16) {
                            throw IllegalArgumentException(
                                "WAV must be 16-bit PCM. Got format=$format bits=$bitsPerSample. " +
                                    "Convert your file to 16-bit WAV (e.g. in Audacity: Tracks → Resample → 44100, Export as WAV)."
                            )
                        }
                    }
                }
                "data" -> {
                    dataBytes = ByteArray(chunkSize)
                    reader.readFully(dataBytes)
                    break
                }
                else -> {
                    // Skip LIST, JUNK, INFO, etc. Use readFully (not skipBytes) to consume exactly
                    // chunkSize bytes — skipBytes can return early and misalign the stream.
                    if (chunkSize > 0) {
                        val discard = ByteArray(chunkSize.coerceAtMost(64 * 1024))
                        var remaining = chunkSize
                        while (remaining > 0) {
                            val toRead = minOf(remaining, discard.size)
                            reader.readFully(discard, 0, toRead)
                            remaining -= toRead
                        }
                    }
                }
            }
        }

        if (dataBytes == null) throw IllegalArgumentException("WAV missing data chunk")
        if (sampleRate <= 0 || channelCount <= 0) throw IllegalArgumentException("WAV fmt chunk invalid or missing")
        if (dataBytes.size % 2 != 0) throw IllegalArgumentException("WAV data chunk has odd byte count (not 16-bit)")
        val sampleCount = dataBytes.size / 2 / channelCount
        val durationSec = sampleCount.toFloat() / sampleRate
        if (durationSec > 3600) throw IllegalArgumentException("WAV data chunk too large (${durationSec.toInt()}s). Max 1 hour.")
        SonicVaultLogger.d("[WavAudioHandler] parseWavChunks samples=$sampleCount rate=$sampleRate ch=$channelCount duration=${durationSec}s")
        return Triple(sampleRate, channelCount, dataBytes)
    }

    private fun readLittleEndianInt(reader: DataInputStream): Int {
        val bytes = ByteArray(4)
        reader.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }

    override suspend fun writeWav(samples: ShortArray, sampleRate: Int, outputUri: Uri?): Uri = withContext(Dispatchers.IO) {
        val file = outputUri?.path?.let { File(it) }
            ?: File(context.cacheDir, "sonicvault_stego_${System.currentTimeMillis()}.wav")
        FileOutputStream(file).use { out ->
            writeWavHeader(out, samples.size, sampleRate)
            val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach { buffer.putShort(it) }
            out.write(buffer.array())
        }
        SonicVaultLogger.d("[WavAudioHandler] writeWav samples=${samples.size} sampleRate=$sampleRate output=${file.absolutePath}")
        Uri.fromFile(file)
    }

    private fun writeWavHeader(out: java.io.OutputStream, numSamples: Int, sampleRate: Int) {
        val dataSize = numSamples * 2
        val byteRate = sampleRate * 2
        out.write("RIFF".toByteArray())
        out.write(intToBytes(36 + dataSize))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(intToBytes(16))
        out.write(shortToBytes(1))
        out.write(shortToBytes(1))
        out.write(intToBytes(sampleRate))
        out.write(intToBytes(byteRate))
        out.write(shortToBytes(2))
        out.write(shortToBytes(16))
        out.write("data".toByteArray())
        out.write(intToBytes(dataSize))
    }

    private fun intToBytes(v: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).apply { putInt(v) }.array()
    private fun shortToBytes(v: Int): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).apply { putShort(v.toShort()) }.array()
}
