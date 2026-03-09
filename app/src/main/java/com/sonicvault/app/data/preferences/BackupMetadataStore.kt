package com.sonicvault.app.data.preferences

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists backup metadata (timestamp, stego URI, checksum hex) for album-art gallery.
 * Album art is regenerated from checksum via [com.sonicvault.app.data.media.AlbumArtGenerator.generateFromHash].
 */
data class BackupMetadata(
    val timestamp: Long,
    val stegoUri: String,
    val checksumHex: String?
)

class BackupMetadataStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Append a backup entry. Keeps last 50 for gallery. */
    fun addBackup(timestamp: Long, stegoUri: Uri, checksumHex: String?) {
        val list = getBackups().toMutableList()
        list.add(0, BackupMetadata(timestamp, stegoUri.toString(), checksumHex))
        val kept = list.take(50)
        val arr = JSONArray()
        kept.forEach { b ->
            arr.put(JSONObject().apply {
                put(KEY_TS, b.timestamp)
                put(KEY_URI, b.stegoUri)
                put(KEY_CHECKSUM, b.checksumHex ?: "")
            })
        }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    fun getBackups(): List<BackupMetadata> {
        val json = prefs.getString(KEY_LIST, "[]") ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                BackupMetadata(
                    timestamp = obj.getLong(KEY_TS),
                    stegoUri = obj.getString(KEY_URI),
                    checksumHex = obj.optString(KEY_CHECKSUM).takeIf { it.isNotEmpty() }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "sonicvault_backup_metadata"
        private const val KEY_LIST = "backup_list"
        private const val KEY_TS = "ts"
        private const val KEY_URI = "uri"
        private const val KEY_CHECKSUM = "checksum"
    }
}
