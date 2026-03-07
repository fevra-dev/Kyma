package com.sonicvault.app.data.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.sonicvault.app.logging.SonicVaultLogger
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates deterministic album art from seed entropy hash.
 *
 * Each unique seed produces a unique, visually distinct 512x512 artwork.
 * The art is purely derived from SHA-256(seed_entropy), making it reproducible
 * and serving as a visual fingerprint.
 *
 * Art style: geometric shapes (circles, arcs, lines) on a gradient background,
 * inspired by generative art. Colors derived from hash bytes.
 */
object AlbumArtGenerator {

    private const val ART_SIZE = 512

    /**
     * Generates album art from seed phrase entropy.
     *
     * @param seedPhrase the BIP39 mnemonic string
     * @return deterministic 512x512 Bitmap
     */
    fun generate(seedPhrase: String): Bitmap {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(seedPhrase.toByteArray(Charsets.UTF_8))
        return generateFromHash(hash)
    }

    /**
     * Generates album art from a 32-byte hash.
     *
     * Layout:
     * - Background: gradient from hash[0..2] to hash[3..5]
     * - Geometric shapes: positions, sizes, colors from remaining hash bytes
     * - Center circle: accent color from hash
     */
    fun generateFromHash(hash: ByteArray): Bitmap {
        SonicVaultLogger.i("[AlbumArt] generating from hash")

        val bitmap = Bitmap.createBitmap(ART_SIZE, ART_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true }

        // Background gradient
        val bgColor1 = Color.rgb(
            (hash[0].toInt() and 0xFF) / 4 + 20,
            (hash[1].toInt() and 0xFF) / 4 + 20,
            (hash[2].toInt() and 0xFF) / 4 + 40
        )
        val bgColor2 = Color.rgb(
            (hash[3].toInt() and 0xFF) / 5 + 10,
            (hash[4].toInt() and 0xFF) / 5 + 10,
            (hash[5].toInt() and 0xFF) / 5 + 20
        )
        paint.shader = LinearGradient(
            0f, 0f, ART_SIZE.toFloat(), ART_SIZE.toFloat(),
            bgColor1, bgColor2, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, ART_SIZE.toFloat(), ART_SIZE.toFloat(), paint)
        paint.shader = null

        // Accent color
        val accentColor = Color.rgb(
            (hash[6].toInt() and 0xFF).coerceIn(80, 255),
            (hash[7].toInt() and 0xFF).coerceIn(80, 255),
            (hash[8].toInt() and 0xFF).coerceIn(80, 255)
        )

        // Secondary accent
        val accent2 = Color.rgb(
            (hash[9].toInt() and 0xFF).coerceIn(80, 255),
            (hash[10].toInt() and 0xFF).coerceIn(60, 200),
            (hash[11].toInt() and 0xFF).coerceIn(80, 255)
        )

        // Geometric shapes determined by hash
        val shapeCount = 4 + (hash[12].toInt() and 0x03)
        for (i in 0 until shapeCount) {
            val bi = 13 + i * 3
            if (bi + 2 >= hash.size) break

            val cx = ((hash[bi].toInt() and 0xFF) / 255f) * ART_SIZE
            val cy = ((hash[bi + 1].toInt() and 0xFF) / 255f) * ART_SIZE
            val radius = 30f + ((hash[bi + 2].toInt() and 0xFF) / 255f) * 80f

            paint.color = if (i % 2 == 0) accentColor else accent2
            paint.alpha = 60 + (hash[bi].toInt() and 0x3F)
            paint.style = if (i % 3 == 0) Paint.Style.FILL else Paint.Style.STROKE
            paint.strokeWidth = 2f + (hash[bi + 2].toInt() and 0x03)

            canvas.drawCircle(cx, cy, radius, paint)
        }

        // Center accent circle
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = accentColor
        paint.alpha = 180
        canvas.drawCircle(ART_SIZE / 2f, ART_SIZE / 2f, 60f, paint)

        // Concentric ring
        paint.alpha = 80
        paint.strokeWidth = 1.5f
        canvas.drawCircle(ART_SIZE / 2f, ART_SIZE / 2f, 100f, paint)

        // Radial lines from center (sound wave motif)
        val lineCount = 8 + (hash[30].toInt() and 0x07)
        paint.alpha = 40
        paint.strokeWidth = 1f
        for (i in 0 until lineCount) {
            val angle = (2.0 * PI * i / lineCount)
            val endX = ART_SIZE / 2f + cos(angle).toFloat() * ART_SIZE * 0.45f
            val endY = ART_SIZE / 2f + sin(angle).toFloat() * ART_SIZE * 0.45f
            canvas.drawLine(ART_SIZE / 2f, ART_SIZE / 2f, endX, endY, paint)
        }

        paint.reset()
        SonicVaultLogger.i("[AlbumArt] generated ${ART_SIZE}x${ART_SIZE} art")
        return bitmap
    }
}
