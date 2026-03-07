package com.sonicvault.app.data.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.sonicvault.app.logging.SonicVaultLogger
import java.security.MessageDigest

/**
 * Generates visual fingerprints (identicons) from audio file hashes.
 *
 * Each backup WAV gets a unique 8x8 symmetric visual identity derived from
 * SHA-256 of the file bytes. Users can visually verify backup integrity by
 * comparing the identicon at creation time vs recovery time.
 *
 * Also produces a 4-character hex suffix for textual identification
 * (e.g., "Backup #A3F2").
 */
object SoundFingerprint {

    /** Grid size for identicon (half-width due to symmetry). */
    private const val GRID_SIZE = 8
    private const val HALF_GRID = GRID_SIZE / 2

    /**
     * Color palette derived from hash — 5 distinct colors per hash.
     * Uses HSL model with hue from hash bytes, fixed saturation/lightness.
     */
    private val BASE_COLORS = listOf(
        0xFF1565C0.toInt(), // blue
        0xFF2E7D32.toInt(), // green
        0xFFC62828.toInt(), // red
        0xFF6A1B9A.toInt(), // purple
        0xFFEF6C00.toInt(), // orange
        0xFF00838F.toInt(), // teal
        0xFFAD1457.toInt(), // pink
        0xFF4E342E.toInt()  // brown
    )

    /**
     * Generates an identicon bitmap from a SHA-256 hash.
     *
     * @param hash 32-byte SHA-256 hash
     * @param sizePx output bitmap size in pixels
     * @return square Bitmap with the identicon pattern
     */
    fun generateIdenticon(hash: ByteArray, sizePx: Int = 256): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cellSize = sizePx.toFloat() / GRID_SIZE

        // Foreground color derived from first 3 hash bytes
        val colorIndex = (hash[0].toInt() and 0xFF) % BASE_COLORS.size
        val fgColor = BASE_COLORS[colorIndex]
        val bgColor = 0xFF1A1A1A.toInt() // dark background

        val paint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = false
        }

        // Fill background
        paint.color = bgColor
        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)

        // Generate symmetric pattern from hash bytes
        paint.color = fgColor
        for (row in 0 until GRID_SIZE) {
            for (col in 0 until HALF_GRID) {
                val byteIndex = (row * HALF_GRID + col) % hash.size
                val filled = (hash[byteIndex].toInt() and 0xFF) > 127

                if (filled) {
                    // Left side
                    canvas.drawRect(
                        col * cellSize,
                        row * cellSize,
                        (col + 1) * cellSize,
                        (row + 1) * cellSize,
                        paint
                    )
                    // Right side (mirror)
                    val mirrorCol = GRID_SIZE - 1 - col
                    canvas.drawRect(
                        mirrorCol * cellSize,
                        row * cellSize,
                        (mirrorCol + 1) * cellSize,
                        (row + 1) * cellSize,
                        paint
                    )
                }
            }
        }

        return bitmap
    }

    /**
     * Computes SHA-256 hash and generates identicon from WAV file bytes.
     *
     * @param wavBytes raw WAV file bytes
     * @param sizePx identicon size in pixels
     * @return identicon bitmap
     */
    fun fromWavBytes(wavBytes: ByteArray, sizePx: Int = 256): Bitmap {
        val hash = MessageDigest.getInstance("SHA-256").digest(wavBytes)
        SonicVaultLogger.i("[SoundFingerprint] generated identicon from ${wavBytes.size} bytes")
        return generateIdenticon(hash, sizePx)
    }

    /**
     * Generates a 4-character hex suffix from SHA-256 hash.
     * Used as a short identifier: "Backup #A3F2".
     */
    fun shortId(hash: ByteArray): String {
        return hash.take(2).joinToString("") { "%02X".format(it) }
    }

    /**
     * Computes SHA-256 of data and returns short ID.
     */
    fun shortIdFromBytes(data: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(data)
        return shortId(hash)
    }
}
