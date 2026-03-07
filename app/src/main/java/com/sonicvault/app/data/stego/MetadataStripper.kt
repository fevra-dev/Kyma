package com.sonicvault.app.data.stego

import android.content.Context
import android.net.Uri
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Strips embedded metadata (LIST, INFO, EXIF, etc.) from WAV files before share.
 * Keeps only RIFF header, fmt, and data chunks to minimize leakage (GDPR, user trust).
 * All processing on-device; no cloud.
 */
interface MetadataStripper {
    suspend fun stripMetadata(inputUri: Uri): Result<Uri>
}

/**
 * WAV metadata stripper. Parses RIFF structure; keeps only fmt + data chunks.
 * WAV format: RIFF(4) + size(4 LE) + WAVE(4) | [fmt | data | LIST | INFO | ...]
 */
class MetadataStripperImpl(private val context: Context) : MetadataStripper {

    override suspend fun stripMetadata(inputUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        SonicVaultLogger.i("[MetadataStripper] strip uri=$inputUri")
        try {
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                val output = ByteArrayOutputStream()
                stripWavMetadata(input, output)
                val bytes = output.toByteArray()
                // Patch RIFF size (bytes 4-7, little-endian): file size - 8
                val size = bytes.size - 8
                bytes[4] = (size and 0xFF).toByte()
                bytes[5] = (size shr 8 and 0xFF).toByte()
                bytes[6] = (size shr 16 and 0xFF).toByte()
                bytes[7] = (size shr 24 and 0xFF).toByte()
                val outFile = File(context.cacheDir, "sonicvault_stripped_${System.currentTimeMillis()}.wav")
                FileOutputStream(outFile).use { it.write(bytes) }
                SonicVaultLogger.i("[MetadataStripper] stripped OK")
                Result.success(Uri.fromFile(outFile))
            } ?: Result.failure(IllegalArgumentException("Unable to open file. Try another file."))
        } catch (e: Exception) {
            SonicVaultLogger.e("[MetadataStripper] strip failed", e)
            Result.failure(e)
        }
    }

    /**
     * Parses WAV, keeps only RIFF header + fmt + data chunks; discards LIST, INFO, etc.
     */
    private fun stripWavMetadata(input: java.io.InputStream, output: java.io.OutputStream) {
        val reader = java.io.DataInputStream(input)
        // RIFF header (12 bytes)
        val riff = ByteArray(4)
        reader.readFully(riff)
        if (String(riff, Charsets.US_ASCII) != "RIFF") throw IllegalArgumentException("Not a RIFF file")
        readLittleEndianInt(reader) // file size - 8 (unused; we patch at end)
        val wave = ByteArray(4)
        reader.readFully(wave)
        if (String(wave, Charsets.US_ASCII) != "WAVE") throw IllegalArgumentException("Not WAVE format")
        output.write(riff)
        output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array()) // placeholder
        output.write(wave)
        var foundData = false
        while (true) {
            val chunkId = ByteArray(4)
            if (reader.read(chunkId) != 4) break
            val chunkSize = readLittleEndianInt(reader)
            val chunkIdStr = String(chunkId, Charsets.US_ASCII)
            val chunkData = ByteArray(chunkSize)
            reader.readFully(chunkData)
            if (chunkIdStr == "fmt " || chunkIdStr == "data") {
                output.write(chunkId)
                output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkSize).array())
                output.write(chunkData)
            }
            if (chunkIdStr == "data") {
                foundData = true
                break
            }
        }
        if (!foundData) throw IllegalArgumentException("WAV missing data chunk")
    }

    private fun readLittleEndianInt(reader: java.io.DataInputStream): Int {
        val bytes = ByteArray(4)
        reader.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }
}
