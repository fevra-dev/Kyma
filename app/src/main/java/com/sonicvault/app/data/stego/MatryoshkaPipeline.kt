package com.sonicvault.app.data.stego

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.sonicvault.app.logging.SonicVaultLogger
import java.io.ByteArrayOutputStream

/**
 * Matryoshka steganography pipeline — 3-layer nesting.
 *
 * The pipeline embeds data through three successive layers:
 *
 * ENCODE:
 *   1. Audio layer: original cover audio (PCM samples)
 *   2. Spectrogram layer: audio -> spectrogram image
 *   3. Image layer: embed payload via LSB steganography into the spectrogram
 *   4. Reconstruction: modified spectrogram -> audio via Griffin-Lim
 *   5. Spectrogram art: optional branding in 11-18 kHz band
 *
 * DECODE:
 *   1. Audio -> spectrogram (STFT)
 *   2. Extract LSB payload from spectrogram image
 *
 * The result is audio that sounds essentially the same as the original
 * but contains a hidden payload only recoverable by converting back to
 * a spectrogram and extracting the LSBs.
 *
 * This achieves "steganography within steganography" — even if someone
 * suspects audio steganography, the data is hidden in the visual domain
 * of the spectrogram representation, not directly in the audio signal.
 */
object MatryoshkaPipeline {

    private const val SAMPLE_RATE = 44100

    /**
     * Encodes payload into audio via the 3-layer Matryoshka pipeline.
     *
     * @param coverAudio original PCM mono samples
     * @param payload bytes to hide
     * @param sampleRate audio sample rate (default 44100)
     * @param embedArt whether to add spectrogram art branding
     * @return modified audio with embedded payload, or null on failure
     */
    fun encode(
        coverAudio: ShortArray,
        payload: ByteArray,
        sampleRate: Int = SAMPLE_RATE,
        embedArt: Boolean = true
    ): ShortArray? {
        SonicVaultLogger.i("[Matryoshka] encoding ${payload.size} bytes into ${coverAudio.size} samples")

        // Step 1: Generate spectrogram from cover audio
        val spectrogramBitmap = SpectrogramGenerator.generate(coverAudio, sampleRate)
        SonicVaultLogger.i("[Matryoshka] spectrogram: ${spectrogramBitmap.width}x${spectrogramBitmap.height}")

        // Step 2: Embed payload into spectrogram via LSB
        val stegoBitmap = ImageSteganography.embed(spectrogramBitmap, payload)
        if (stegoBitmap == null) {
            SonicVaultLogger.e("[Matryoshka] LSB embedding failed — payload too large for spectrogram")
            return null
        }

        // Step 3: Reconstruct audio from modified spectrogram via Griffin-Lim
        var reconstructed = GriffinLim.reconstruct(stegoBitmap)
        SonicVaultLogger.i("[Matryoshka] reconstructed ${reconstructed.size} samples")

        // Step 4: Optional spectrogram art branding in 11-18 kHz
        if (embedArt) {
            reconstructed = SpectrogramArtLayer.embedText(reconstructed, sampleRate)
            SonicVaultLogger.i("[Matryoshka] spectrogram art applied")
        }

        return reconstructed
    }

    /**
     * Decodes payload from Matryoshka-encoded audio.
     *
     * @param stegoAudio audio containing hidden payload
     * @param sampleRate audio sample rate (default 44100)
     * @return extracted payload bytes, or null if not found
     */
    fun decode(stegoAudio: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray? {
        SonicVaultLogger.i("[Matryoshka] decoding from ${stegoAudio.size} samples")

        // Step 1: Convert audio back to spectrogram
        val spectrogramBitmap = SpectrogramGenerator.generate(stegoAudio, sampleRate)

        // Step 2: Extract LSB payload from spectrogram
        val payload = ImageSteganography.extract(spectrogramBitmap)
        if (payload != null) {
            SonicVaultLogger.i("[Matryoshka] extracted ${payload.size} bytes")
        } else {
            SonicVaultLogger.w("[Matryoshka] no embedded data found in spectrogram")
        }

        return payload
    }

    /**
     * Estimates the maximum payload capacity for a given audio length.
     *
     * @param audioSamples number of PCM samples
     * @return approximate maximum payload size in bytes
     */
    fun estimateCapacity(audioSamples: Int): Int {
        val fftSize = 1024
        val hopSize = 512
        val freqBins = fftSize / 2
        val numFrames = (audioSamples - fftSize) / hopSize + 1
        if (numFrames <= 0) return 0

        val totalPixels = numFrames * freqBins
        // 6 bits per pixel, minus header overhead
        return (totalPixels * 6) / 8 - 8
    }
}
