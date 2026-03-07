package com.sonicvault.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.sonicvault.app.data.stego.FlacExporter
import com.sonicvault.app.data.stego.MetadataStripperImpl
import com.sonicvault.app.logging.SonicVaultLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** File scheme for URIs; content scheme requires different handling. */
private const val SCHEME_FILE = "file"

/**
 * Export format for stego backup: WAV (universal) or FLAC (50–70% smaller).
 * VINYL was archived; see docs/VINYL_EXPORT_ARCHIVE.md.
 */
enum class ExportFormat {
    /** WAV: universal compatibility; larger file size. */
    WAV,
    /** FLAC: lossless, 50–70% smaller; cloud-friendly. */
    FLAC
}

/**
 * Shares the stego file via FileProvider (content://) so other apps (Photos, Drive, Files) can read it.
 * Required on Android 7+ where file:// URIs are not allowed for sharing.
 *
 * When [format] is WAV: strips embedded metadata (LIST, INFO, etc.) before share (GDPR, user trust).
 * When [format] is FLAC: converts WAV→FLAC first (lossless, smaller); encoder creates minimal metadata.
 * All processing on-device; no cloud.
 *
 * @param context Application context
 * @param wavUri URI of the stego WAV file (source is always WAV; FLAC is created on demand)
 * @param format ExportFormat.WAV (default) or ExportFormat.FLAC
 * @param flacExporter FlacExporter instance for WAV→FLAC conversion when format is FLAC
 */
suspend fun shareStegoFile(
    context: Context,
    wavUri: Uri,
    format: ExportFormat = ExportFormat.WAV,
    flacExporter: FlacExporter? = null
) = withContext(Dispatchers.IO) io@{
    var effectiveFormat = format
    val uriToShare = when (format) {
        ExportFormat.WAV -> {
            val stripper = MetadataStripperImpl(context)
            stripper.stripMetadata(wavUri).getOrNull() ?: wavUri
        }
        ExportFormat.FLAC -> {
            val exporter = flacExporter ?: run {
                SonicVaultLogger.e("[ShareHelper] FLAC export requires FlacExporter")
                return@io
            }
            exporter.convertWavToFlac(wavUri) ?: run {
                SonicVaultLogger.w("[ShareHelper] FLAC conversion failed; falling back to WAV")
                effectiveFormat = ExportFormat.WAV
                val stripper = MetadataStripperImpl(context)
                stripper.stripMetadata(wavUri).getOrNull() ?: wavUri
            }
        }
    }
    withContext(Dispatchers.Main) main@{
        try {
            /* FileProvider requires file:// URIs; path must be valid. */
            val path = uriToShare.path ?: run {
                SonicVaultLogger.e("[ShareHelper] uri has no path: $uriToShare")
                return@main
            }
            if (uriToShare.scheme != SCHEME_FILE) {
                SonicVaultLogger.e("[ShareHelper] expected file URI, got scheme=${uriToShare.scheme}")
                return@main
            }
            val file = File(path)
            if (!file.exists()) {
                SonicVaultLogger.e("[ShareHelper] file does not exist: $path")
                return@main
            }
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, file)
            val (mimeType, chooserTitle) = when (effectiveFormat) {
                ExportFormat.WAV -> "audio/wav" to "Share backup file (WAV)"
                ExportFormat.FLAC -> "audio/flac" to "Share backup file (FLAC)"
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, chooserTitle))
        } catch (e: Exception) {
            SonicVaultLogger.e("[ShareHelper] share failed", e)
            throw e
        }
    }
}
