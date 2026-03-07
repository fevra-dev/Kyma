package com.sonicvault.app.util

import com.sonicvault.app.logging.SonicVaultLogger
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

/**
 * Secure file deletion: overwrites file contents with random data before unlinking.
 *
 * Standard File.delete() only removes the directory entry; the actual data blocks
 * remain on disk until overwritten by new files. Forensic tools (Cellebrite, UFED)
 * can recover unlinked data. This utility overwrites the file's entire content with
 * cryptographically random data before deletion, making forensic recovery impractical.
 *
 * Limitations:
 * - Flash storage (eMMC/UFS) may use wear-leveling, keeping old blocks.
 *   True secure erase requires TRIM/DISCARD support which is filesystem-level.
 * - Journaled filesystems (ext4) may retain journal copies.
 * - This is best-effort defense-in-depth, not a NIST 800-88 compliant purge.
 *
 * Usage: Replace File.delete() with SecureFileDeleter.delete(file) for sensitive files.
 */
object SecureFileDeleter {

    /** Buffer size for overwrite operations (8 KB). */
    private const val BUFFER_SIZE = 8192

    /** Number of overwrite passes (1 pass is sufficient for flash storage). */
    private const val OVERWRITE_PASSES = 1

    /**
     * Securely deletes a file by overwriting its contents with random data
     * then deleting the file entry.
     *
     * @param file File to securely delete.
     * @return true if file was successfully deleted, false otherwise.
     */
    fun delete(file: File): Boolean {
        if (!file.exists()) return true

        return try {
            overwriteFile(file)
            val deleted = file.delete()
            if (deleted) {
                SonicVaultLogger.d("[SecureDelete] securely deleted: ${file.name}")
            } else {
                SonicVaultLogger.w("[SecureDelete] overwrite succeeded but delete failed: ${file.name}")
            }
            deleted
        } catch (e: Exception) {
            SonicVaultLogger.w("[SecureDelete] secure delete failed, attempting normal delete: ${e.message}")
            /** Fall back to normal delete if overwrite fails (e.g. read-only file). */
            file.delete()
        }
    }

    /**
     * Overwrites file contents with cryptographically random data.
     * Uses RandomAccessFile with "rws" mode to force sync writes (bypass page cache).
     *
     * @param file File to overwrite.
     */
    private fun overwriteFile(file: File) {
        val random = SecureRandom()
        val buffer = ByteArray(BUFFER_SIZE)

        for (pass in 0 until OVERWRITE_PASSES) {
            RandomAccessFile(file, "rws").use { raf ->
                val size = raf.length()
                raf.seek(0)
                var written = 0L
                while (written < size) {
                    random.nextBytes(buffer)
                    val toWrite = minOf(BUFFER_SIZE.toLong(), size - written).toInt()
                    raf.write(buffer, 0, toWrite)
                    written += toWrite
                }
                /** Force sync to ensure data reaches storage controller. */
                raf.fd.sync()
            }
        }

        /** Wipe the random buffer itself. */
        buffer.fill(0)
    }

    /**
     * Securely deletes all files in a directory matching a name pattern.
     * Does not recurse into subdirectories.
     *
     * @param directory Directory to scan.
     * @param prefix File name prefix to match.
     * @param suffix File name suffix to match.
     * @return Number of files successfully deleted.
     */
    fun deleteMatching(directory: File, prefix: String, suffix: String): Int {
        if (!directory.isDirectory) return 0
        val files = directory.listFiles() ?: return 0
        var count = 0
        for (f in files) {
            if (f.name.startsWith(prefix) && f.name.endsWith(suffix)) {
                if (delete(f)) count++
            }
        }
        return count
    }
}
