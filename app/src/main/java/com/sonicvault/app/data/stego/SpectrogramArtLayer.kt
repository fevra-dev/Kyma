package com.sonicvault.app.data.stego

import com.sonicvault.app.logging.SonicVaultLogger
import kotlin.math.PI
import kotlin.math.sin

/**
 * Embeds visual art (text/logo) into the 11-18 kHz frequency band of audio.
 *
 * When the resulting WAV is opened in a spectrogram viewer (Audacity, Sonic Visualiser),
 * the embedded text/image is visible as a bright pattern in the high frequency region.
 *
 * Frequency partition:
 * - 0-10 kHz:  untouched (audible content)
 * - 11-18 kHz: art zone (logo, text — visible in spectrogram)
 * - 15-19.5 kHz: ggwave data zone (ultrasonic transmission)
 *
 * The art is synthesized by generating sine tones at frequencies corresponding to
 * "lit" pixels in a bitmap representation of the desired text/image.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Aphex_Twin#Selected_Ambient_Works_Volume_II">Aphex Twin technique</a>
 */
object SpectrogramArtLayer {

    /** Frequency range for art embedding (Hz). */
    private const val ART_FREQ_LOW = 11000.0
    private const val ART_FREQ_HIGH = 18000.0

    /** Amplitude of art tones relative to full scale. Low enough to be inaudible, preserves cover audio. */
    private const val ART_AMPLITUDE = 0.008f

    /**
     * 5x7 bitmap font for uppercase letters + digits, used for spectrogram text.
     * Each character is a 5-wide x 7-tall boolean grid stored as 7 rows of 5 bits.
     */
    private val FONT_5X7: Map<Char, List<Int>> = mapOf(
        'S' to listOf(0b01110, 0b10001, 0b10000, 0b01110, 0b00001, 0b10001, 0b01110),
        'O' to listOf(0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110),
        'N' to listOf(0b10001, 0b11001, 0b10101, 0b10011, 0b10001, 0b10001, 0b10001),
        'I' to listOf(0b01110, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110),
        'C' to listOf(0b01110, 0b10001, 0b10000, 0b10000, 0b10000, 0b10001, 0b01110),
        'V' to listOf(0b10001, 0b10001, 0b10001, 0b10001, 0b01010, 0b01010, 0b00100),
        'A' to listOf(0b00100, 0b01010, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001),
        'U' to listOf(0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110),
        'L' to listOf(0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111),
        'T' to listOf(0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100),
        '2' to listOf(0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0b01000, 0b11111),
        '0' to listOf(0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110),
        '6' to listOf(0b01110, 0b10001, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110),
        ' ' to listOf(0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b00000),
        'E' to listOf(0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b11111),
        'D' to listOf(0b11110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b11110),
        'R' to listOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10100, 0b10010, 0b10001),
        'K' to listOf(0b10001, 0b10010, 0b10100, 0b11000, 0b10100, 0b10010, 0b10001),
        'P' to listOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10000, 0b10000, 0b10000)
    )

    /**
     * Renders text into a boolean bitmap grid suitable for spectrogram embedding.
     *
     * @param text uppercase text to render (unsupported chars become spaces)
     * @return 2D boolean array [row][col], true = "lit" pixel
     */
    fun renderTextBitmap(text: String): Array<BooleanArray> {
        val chars = text.uppercase().toCharArray()
        val charWidth = 6 // 5 + 1 spacing
        val totalWidth = chars.size * charWidth
        val height = 7

        val bitmap = Array(height) { BooleanArray(totalWidth) }

        for ((ci, ch) in chars.withIndex()) {
            val glyph = FONT_5X7[ch] ?: FONT_5X7[' ']!!
            for (row in 0 until height) {
                for (col in 0 until 5) {
                    if ((glyph[row] shr (4 - col)) and 1 == 1) {
                        bitmap[row][ci * charWidth + col] = true
                    }
                }
            }
        }
        return bitmap
    }

    /**
     * Embeds spectrogram art text into audio samples.
     *
     * Each column of the text bitmap occupies a time slice of the audio.
     * Each row maps to a frequency between [ART_FREQ_LOW] and [ART_FREQ_HIGH].
     * "Lit" pixels produce a sine tone at that frequency for that time slice.
     *
     * Callers must not pass sensitive text; content may be logged in debug builds.
     *
     * @param samples PCM samples to modify (modified in place)
     * @param sampleRate audio sample rate (e.g. 44100)
     * @param text text to embed (default: "SONICVAULT 2026")
     * @return modified samples (same array, mutated)
     */
    fun embedText(
        samples: ShortArray,
        sampleRate: Int,
        text: String = "SONICVAULT 2026"
    ): ShortArray {
        SonicVaultLogger.d("[SpectrogramArt] embedding ${text.length} chars in ${ART_FREQ_LOW.toInt()}-${ART_FREQ_HIGH.toInt()}Hz band")

        val bitmap = renderTextBitmap(text)
        val numRows = bitmap.size
        val numCols = bitmap[0].size

        // Time allocation: place art in the middle 60% of the audio
        val startSample = (samples.size * 0.2).toInt()
        val endSample = (samples.size * 0.8).toInt()
        val artDuration = endSample - startSample
        val colWidth = artDuration / numCols

        // Frequency mapping: row 0 = highest freq, row 6 = lowest (natural spectrogram orientation)
        val freqStep = (ART_FREQ_HIGH - ART_FREQ_LOW) / (numRows - 1)

        for (col in 0 until numCols) {
            val colStart = startSample + col * colWidth
            val colEnd = colStart + colWidth

            for (row in 0 until numRows) {
                if (!bitmap[row][col]) continue

                // Row 0 = top of spectrogram = highest frequency
                val freq = ART_FREQ_HIGH - row * freqStep

                for (i in colStart until colEnd.coerceAtMost(samples.size)) {
                    val t = (i - colStart).toDouble() / sampleRate
                    val artSample = (ART_AMPLITUDE * Short.MAX_VALUE * sin(2.0 * PI * freq * t)).toInt()
                    // Additive mixing: add art tone to existing audio
                    val mixed = samples[i].toInt() + artSample
                    samples[i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
        }

        SonicVaultLogger.i("[SpectrogramArt] embedded ${numCols} columns x ${numRows} rows")
        return samples
    }
}
