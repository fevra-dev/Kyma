package com.sonicvault.app.data.stego

import android.content.Context
import net.sourceforge.javaflacencoder.FLAC_FileEncoder
import android.net.Uri
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Converts WAV (stego) files to FLAC for smaller, lossless backup export.
 * FLAC is 50–70% smaller than WAV; preserves data integrity for LSB steganography.
 *
 * Uses javaFlacEncoder (FLAC_FileEncoder) for pure-Java WAV→FLAC conversion.
 * Input must be valid 16-bit PCM WAV (mono or stereo).
 */
interface FlacExporter {
    /**
     * Converts the WAV file at [wavUri] to FLAC and returns the FLAC file URI.
     * @param wavUri URI of the source WAV file (file:// or content://)
     * @return URI of the created FLAC file, or null on failure
     */
    suspend fun convertWavToFlac(wavUri: Uri): Uri?
}

/**
 * Implementation using javaFlacEncoder.FLAC_FileEncoder for WAV→FLAC conversion.
 */
class FlacExporterImpl(private val context: Context) : FlacExporter {

    override suspend fun convertWavToFlac(wavUri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            val inputPath = when (wavUri.scheme) {
                "file" -> wavUri.path
                "content" -> {
                    // Copy content URI to temp file (FLAC_FileEncoder expects File)
                    val tempWav = File(context.cacheDir, "sonicvault_temp_${System.currentTimeMillis()}.wav")
                    context.contentResolver.openInputStream(wavUri)?.use { input ->
                        tempWav.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: run {
                        SonicVaultLogger.w("[FLAC] could not open content URI: $wavUri")
                        return@withContext null
                    }
                    tempWav.absolutePath
                }
                else -> {
                    SonicVaultLogger.w("[FLAC] unsupported uri scheme: ${wavUri.scheme}")
                    return@withContext null
                }
            } ?: run {
                SonicVaultLogger.w("[FLAC] could not resolve wav path for uri=$wavUri")
                return@withContext null
            }

            val inputFile = File(inputPath)
            if (!inputFile.exists()) {
                SonicVaultLogger.e("[FLAC] input WAV does not exist: $inputPath")
                return@withContext null
            }

            val outputFile = File(context.cacheDir, "sonicvault_stego_${System.currentTimeMillis()}.flac")
            val encoder = FLAC_FileEncoder()
            encoder.useThreads(false) // Avoid threading issues on Android

            val status = encoder.encode(inputFile, outputFile)
            val samplesEncoded = encoder.lastTotalSamplesEncoded

            when (status) {
                net.sourceforge.javaflacencoder.FLAC_FileEncoder.Status.FULL_ENCODE -> {
                    val sizeBytes = outputFile.length()
                    SonicVaultLogger.i("[FLAC] encoded $samplesEncoded samples -> $sizeBytes bytes")
                    Uri.fromFile(outputFile)
                }
                else -> {
                    SonicVaultLogger.e("[FLAC] encode failed status=$status")
                    outputFile.delete()
                    null
                }
            }
        } catch (e: Exception) {
            SonicVaultLogger.e("[FLAC] convertWavToFlac failed", e)
            null
        }
    }
}
