package com.sonicvault.app.data.voice

import com.sonicvault.app.logging.SonicVaultLogger
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Extracts a fixed-size "voiceprint" vector from PCM for speaker verification.
 * Concept aligned with WhisperX/pyannote: speaker embedding = unique characteristics of voice.
 * This implementation uses acoustic features (energy bands, zero-crossing, spectral-ish) as a
 * placeholder; production should use FRILL TFLite or pyannote-style neural embeddings.
 * @see "Speaker embedding: For each segment, a neural network generates a fixed-length vector
 *       that captures the unique characteristics of the speaker's voice." (WhisperX/pyannote)
 */
interface VoiceEmbeddingExtractor {
    /** Expected embedding dimension (e.g. 32). */
    val embeddingDimension: Int

    /**
     * Extracts embedding from 16-bit mono PCM at 16 kHz (or 44.1k; we downsample internally if needed).
     * Minimum ~1–2 seconds of speech recommended.
     */
    fun extractFromPcm(samples: ShortArray, sampleRate: Int): FloatArray
}

/**
 * Placeholder extractor using band energy + zero-crossing + simple stats.
 * Not as robust as neural embeddings (FRILL/pyannote) but runs on-device with no TFLite dependency.
 * Replace with VoiceEmbeddingExtractor backed by FRILL TFLite for production voice biometrics.
 */
class FeatureBasedVoiceEmbeddingExtractor : VoiceEmbeddingExtractor {

    override val embeddingDimension: Int = EMBED_DIM

    override fun extractFromPcm(samples: ShortArray, sampleRate: Int): FloatArray {
        // Normalize to roughly 16 kHz frame length for consistency (e.g. 16000 * 2 = 32000 samples = 2s)
        val targetLen = minOf(samples.size, sampleRate * 2)
        val src = if (samples.size > targetLen) samples.copyOfRange(0, targetLen) else samples
        val rate = sampleRate.toFloat()

        val frameSize = (rate * 0.025f).toInt()   // 25 ms
        val step = (rate * 0.010f).toInt()       // 10 ms
        val numBands = 8
        val bandEnergies = FloatArray(numBands)
        val bandCounts = IntArray(numBands)
        var zcrSum = 0f
        var energySum = 0f
        var frameCount = 0

        for (start in 0 until (src.size - frameSize) step step) {
            val frame = ShortArray(frameSize) { src[start + it] }
            val energy = frame.map { (it.toInt() * it.toInt()).toFloat() }.average().toFloat()
            energySum += energy
            var zc = 0
            for (i in 1 until frame.size) {
                if ((frame[i].toInt() >= 0) != (frame[i - 1].toInt() >= 0)) zc++
            }
            zcrSum += zc.toFloat() / frame.size
            // Simple "band" = quartiles of frame magnitude
            for (i in frame.indices) {
                val band = (i * numBands / frame.size).coerceIn(0, numBands - 1)
                bandEnergies.set(band, bandEnergies.get(band) + abs(frame[i].toInt()))
                bandCounts[band]++
            }
            frameCount++
        }
        if (frameCount == 0) {
            SonicVaultLogger.w("[VoiceEmbedding] no frames")
            return FloatArray(EMBED_DIM)
        }
        for (b in 0 until numBands) {
            if (bandCounts[b] > 0) bandEnergies.set(b, bandEnergies.get(b) / bandCounts[b])
        }
        val meanEnergy = energySum / frameCount
        val meanZcr = zcrSum / frameCount
        val energyVar = (energySum / frameCount).let { mean -> if (mean > 0) 1f / (1f + mean) else 0f }

        // Build 32-dim vector: band energies (8), normalized stats, repeat for stability
        val out = FloatArray(EMBED_DIM)
        for (i in 0 until numBands) out[i] = bandEnergies[i] / 32768f
        out[8] = meanEnergy / 1e8f
        out[9] = meanZcr
        out[10] = energyVar
        out[11] = sqrt(meanEnergy) / 32768f
        for (i in 12 until EMBED_DIM) out.set(i, out.get(i % 12) * (1f + i * 0.01f))
        return out
    }

    companion object {
        const val EMBED_DIM = 32
    }
}

/**
 * Cosine similarity between two embeddings. Returns value in [-1, 1]; typical threshold for
 * "same speaker" is > 0.7 (neural embeddings); with feature-based placeholder, tune per device.
 */
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size)
    var dot = 0.0
    var na = 0.0
    var nb = 0.0
    for (i in a.indices) {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
    }
    val denom = sqrt(na) * sqrt(nb)
    return if (denom > 1e-9) (dot / denom).toFloat().coerceIn(-1f, 1f) else 0f
}
