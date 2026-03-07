package com.sonicvault.app.data.stego

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.sonicvault.app.logging.SonicVaultLogger
import java.io.ByteArrayOutputStream

/**
 * Image LSB steganography for the Matryoshka pipeline.
 *
 * Embeds/extracts byte payloads in the LSBs of image pixel channels (R, G, B).
 * Uses 2-bit LSB embedding across 3 color channels = 6 bits per pixel.
 *
 * Capacity: 512x512 PNG = 262,144 pixels x 6 bits = 196,608 bytes (~192 KB).
 * Typical spectrogram PNG is ~50-100 KB, well within capacity.
 *
 * Format: MAGIC(4) || payload_length(4 BE) || payload || padding
 */
object ImageSteganography {

    /** Magic header for embedded data detection. */
    private val MAGIC = byteArrayOf(0x4D, 0x54, 0x52, 0x59) // "MTRY"
    private const val HEADER_SIZE = 8 // MAGIC(4) + length(4)
    private const val BITS_PER_CHANNEL = 2
    private const val CHANNELS = 3 // R, G, B
    private const val BITS_PER_PIXEL = BITS_PER_CHANNEL * CHANNELS

    /**
     * Embeds payload into image pixels via LSB steganography.
     *
     * @param coverBitmap the cover image (must be ARGB_8888, lossless format)
     * @param payload bytes to embed
     * @return modified bitmap with embedded data, or null if capacity exceeded
     */
    fun embed(coverBitmap: Bitmap, payload: ByteArray): Bitmap? {
        val mutable = coverBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val totalPixels = mutable.width * mutable.height
        val capacityBytes = (totalPixels * BITS_PER_PIXEL) / 8

        // Build framed payload: MAGIC + length + data
        val framed = ByteArray(HEADER_SIZE + payload.size)
        MAGIC.copyInto(framed, 0)
        framed[4] = ((payload.size shr 24) and 0xFF).toByte()
        framed[5] = ((payload.size shr 16) and 0xFF).toByte()
        framed[6] = ((payload.size shr 8) and 0xFF).toByte()
        framed[7] = (payload.size and 0xFF).toByte()
        payload.copyInto(framed, HEADER_SIZE)

        if (framed.size > capacityBytes) {
            SonicVaultLogger.e("[ImageStego] capacity exceeded: need=${framed.size} have=$capacityBytes")
            return null
        }

        SonicVaultLogger.i("[ImageStego] embedding ${payload.size} bytes into ${mutable.width}x${mutable.height} image")

        // Convert framed data to bit stream
        val bits = toBitArray(framed)
        var bitIdx = 0

        for (y in 0 until mutable.height) {
            for (x in 0 until mutable.width) {
                if (bitIdx >= bits.size) break
                val pixel = mutable.getPixel(x, y)

                var r = Color.red(pixel)
                var g = Color.green(pixel)
                var b = Color.blue(pixel)
                val a = Color.alpha(pixel)

                // Embed BITS_PER_CHANNEL bits in each channel
                r = embedBits(r, bits, bitIdx, BITS_PER_CHANNEL); bitIdx += BITS_PER_CHANNEL
                if (bitIdx < bits.size) {
                    g = embedBits(g, bits, bitIdx, BITS_PER_CHANNEL); bitIdx += BITS_PER_CHANNEL
                }
                if (bitIdx < bits.size) {
                    b = embedBits(b, bits, bitIdx, BITS_PER_CHANNEL); bitIdx += BITS_PER_CHANNEL
                }

                mutable.setPixel(x, y, Color.argb(a, r, g, b))
            }
        }

        SonicVaultLogger.i("[ImageStego] embedded ${bits.size} bits")
        return mutable
    }

    /**
     * Extracts payload from image pixels.
     *
     * @param stegoBitmap image with embedded data
     * @return extracted payload bytes, or null if no valid data found
     */
    fun extract(stegoBitmap: Bitmap): ByteArray? {
        SonicVaultLogger.i("[ImageStego] extracting from ${stegoBitmap.width}x${stegoBitmap.height}")

        // Extract enough bits for header first
        val headerBits = HEADER_SIZE * 8
        val headerExtracted = extractBits(stegoBitmap, headerBits)
        val headerBytes = fromBitArray(headerExtracted)

        // Verify magic
        for (i in MAGIC.indices) {
            if (headerBytes[i] != MAGIC[i]) {
                SonicVaultLogger.d("[ImageStego] magic mismatch — no embedded data")
                return null
            }
        }

        // Read payload length
        val payloadLen = ((headerBytes[4].toInt() and 0xFF) shl 24) or
                ((headerBytes[5].toInt() and 0xFF) shl 16) or
                ((headerBytes[6].toInt() and 0xFF) shl 8) or
                (headerBytes[7].toInt() and 0xFF)

        if (payloadLen <= 0 || payloadLen > 500_000) {
            SonicVaultLogger.w("[ImageStego] invalid payload length: $payloadLen")
            return null
        }

        // Extract full payload
        val totalBits = (HEADER_SIZE + payloadLen) * 8
        val allBits = extractBits(stegoBitmap, totalBits)
        val allBytes = fromBitArray(allBits)

        val payload = allBytes.copyOfRange(HEADER_SIZE, HEADER_SIZE + payloadLen)
        SonicVaultLogger.i("[ImageStego] extracted ${payload.size} bytes")
        return payload
    }

    /** Extracts N bits from image pixels. */
    private fun extractBits(bitmap: Bitmap, numBits: Int): BooleanArray {
        val bits = BooleanArray(numBits)
        var bitIdx = 0

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (bitIdx >= numBits) return bits
                val pixel = bitmap.getPixel(x, y)

                extractChannelBits(Color.red(pixel), bits, bitIdx, BITS_PER_CHANNEL); bitIdx += BITS_PER_CHANNEL
                if (bitIdx < numBits) {
                    extractChannelBits(Color.green(pixel), bits, bitIdx, BITS_PER_CHANNEL); bitIdx += BITS_PER_CHANNEL
                }
                if (bitIdx < numBits) {
                    extractChannelBits(Color.blue(pixel), bits, bitIdx, BITS_PER_CHANNEL); bitIdx += BITS_PER_CHANNEL
                }
            }
        }
        return bits
    }

    /** Embeds [count] bits from [bits] array starting at [offset] into the LSBs of [value]. */
    private fun embedBits(value: Int, bits: BooleanArray, offset: Int, count: Int): Int {
        var result = value
        val mask = (1 shl count) - 1
        result = result and mask.inv()
        var embedded = 0
        for (i in 0 until count) {
            if (offset + i < bits.size && bits[offset + i]) {
                embedded = embedded or (1 shl (count - 1 - i))
            }
        }
        return result or embedded
    }

    /** Extracts [count] LSBs from [value] into [bits] at [offset]. */
    private fun extractChannelBits(value: Int, bits: BooleanArray, offset: Int, count: Int) {
        for (i in 0 until count) {
            if (offset + i < bits.size) {
                bits[offset + i] = ((value shr (count - 1 - i)) and 1) == 1
            }
        }
    }

    /** Converts byte array to boolean bit array. */
    private fun toBitArray(bytes: ByteArray): BooleanArray {
        val bits = BooleanArray(bytes.size * 8)
        for (i in bytes.indices) {
            for (b in 7 downTo 0) {
                bits[i * 8 + (7 - b)] = ((bytes[i].toInt() shr b) and 1) == 1
            }
        }
        return bits
    }

    /** Converts boolean bit array to byte array. */
    private fun fromBitArray(bits: BooleanArray): ByteArray {
        val bytes = ByteArray((bits.size + 7) / 8)
        for (i in bits.indices) {
            if (bits[i]) {
                bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (7 - (i % 8)))).toByte()
            }
        }
        return bytes
    }
}
