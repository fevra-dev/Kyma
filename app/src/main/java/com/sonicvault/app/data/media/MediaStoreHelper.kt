package com.sonicvault.app.data.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Registers stego WAV files with Android MediaStore so they appear in music apps.
 *
 * Plausible deniability: backups look like music tracks in the device gallery.
 * Uses MediaStore.Audio on API 29+ for scoped storage, falls back to
 * MediaScannerConnection on older APIs.
 */
object MediaStoreHelper {

    /**
     * Copies or moves a stego WAV to the Music directory and registers with MediaStore.
     *
     * @param context application context
     * @param sourceUri original stego file URI (app-private)
     * @param title track title (e.g., "Rain on Glass")
     * @param artist artist name (e.g., "SonicVault")
     * @param genre genre tag for plausible deniability
     * @return MediaStore content URI, or null on failure
     */
    suspend fun registerAsMusic(
        context: Context,
        sourceUri: Uri,
        title: String,
        artist: String = "Field Recordings",
        genre: String = "Ambient"
    ): Uri? {
        SonicVaultLogger.i("[MediaStore] registering '$title' as $genre by $artist")

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                registerViaMediaStoreApi(context, sourceUri, title, artist, genre)
            } else {
                registerViaScanner(context, sourceUri, title)
            }
        } catch (e: Exception) {
            SonicVaultLogger.e("[MediaStore] registration failed", e)
            null
        }
    }

    /**
     * API 29+ (scoped storage): inserts into MediaStore.Audio.Media with metadata.
     */
    private fun registerViaMediaStoreApi(
        context: Context,
        sourceUri: Uri,
        title: String,
        artist: String,
        genre: String
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "${title.replace(" ", "_")}.wav")
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/SonicVault")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val mediaUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        // Copy source bytes into MediaStore entry
        resolver.openOutputStream(mediaUri)?.use { outStream ->
            resolver.openInputStream(sourceUri)?.use { inStream ->
                inStream.copyTo(outStream)
            }
        }

        // Mark as no longer pending so music apps can see it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val update = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(mediaUri, update, null, null)
        }

        SonicVaultLogger.i("[MediaStore] registered via MediaStore API: $mediaUri")
        return mediaUri
    }

    /**
     * Pre-API 29: trigger MediaScannerConnection to index the file.
     */
    private suspend fun registerViaScanner(
        context: Context,
        sourceUri: Uri,
        title: String
    ): Uri? = suspendCancellableCoroutine { cont ->
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val destDir = File(musicDir, "SonicVault")
        destDir.mkdirs()
        val destFile = File(destDir, "${title.replace(" ", "_")}.wav")

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        MediaScannerConnection.scanFile(
            context,
            arrayOf(destFile.absolutePath),
            arrayOf("audio/wav")
        ) { _, uri ->
            SonicVaultLogger.i("[MediaStore] registered via scanner: $uri")
            cont.resume(uri)
        }
    }
}
