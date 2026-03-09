package com.sonicvault.app.data.stego

import com.sonicvault.app.logging.SonicVaultLogger
import kotlin.math.PI
import kotlin.math.abs

/**
 * Phase coding steganography: embed secret bits in the phase of selected FFT bins.
 * Each block (e.g. 512 samples) carries one bit: phase 0 = 0, phase π = 1.
 * Less capacity than LSB but more robust to some transformations; used in hybrid mode.
 * Multi-bin: encode same bits in multiple bins for redundancy; extract uses majority vote.
 *
 * Skips block 0 (first 512 samples) to avoid intro distortion on custom cover audio.
 */
interface PhaseSteganography {
    /**
     * Embeds [secret] bits into phase of [coverSamples]. Block size must be power of 2.
     * @param phaseBinIndices FFT bin indices for phase (e.g. [1, 2] for redundancy).
     */
    fun embed(
        coverSamples: ShortArray,
        secret: ByteArray,
        blockSize: Int = 512,
        phaseBinIndices: IntArray = intArrayOf(1)
    ): ShortArray

    /**
     * Extracts [payloadLength] bytes from phase of [stegoSamples].
     * With multiple bins, uses majority vote per bit (prefers bin with stronger phase).
     */
    fun extract(
        stegoSamples: ShortArray,
        payloadLength: Int,
        blockSize: Int = 512,
        phaseBinIndices: IntArray = intArrayOf(1)
    ): ByteArray
}

class PhaseSteganographyImpl : PhaseSteganography {

    override fun embed(
        coverSamples: ShortArray,
        secret: ByteArray,
        blockSize: Int,
        phaseBinIndices: IntArray
    ): ShortArray {
        require(blockSize > 0 && (blockSize and (blockSize - 1)) == 0) { "blockSize must be power of 2" }
        phaseBinIndices.forEach { bin ->
            require(bin in 1 until blockSize / 2) { "phaseBinIndex $bin must be in (0, blockSize/2)" }
        }
        val secretBits = secret.flatMap { byte -> (7 downTo 0).map { (byte.toInt() shr it) and 1 } }
        val numBlocks = coverSamples.size / blockSize
        val usableBlocks = (numBlocks - 1).coerceAtLeast(0) // Skip block 0 to avoid intro distortion
        require(secretBits.size <= usableBlocks) {
            "Need ${secretBits.size} blocks but have $usableBlocks usable (blockSize=$blockSize, blocks=$numBlocks)"
        }
        SonicVaultLogger.d("[PhaseSteganography] embed blocks=$numBlocks skipFirst usable=$usableBlocks secretBits=${secretBits.size} bins=${phaseBinIndices.size}")
        val real = DoubleArray(blockSize)
        val imag = DoubleArray(blockSize)
        val out = coverSamples.copyOf()
        for (b in 1 until numBlocks) {
            val bitIndex = b - 1
            if (bitIndex >= secretBits.size) break
            val offset = b * blockSize
            for (i in 0 until blockSize) {
                real[i] = out[offset + i].toDouble()
                imag[i] = 0.0
            }
            FftHelper.fft(real, imag)
            val bit = secretBits[bitIndex]
            val phaseRad = if (bit == 1) PI else 0.0
            for (phaseBinIndex in phaseBinIndices) {
                FftHelper.setPhase(real, imag, phaseBinIndex, phaseRad)
                FftHelper.setPhase(real, imag, blockSize - phaseBinIndex, -phaseRad)
            }
            FftHelper.ifft(real, imag)
            for (i in 0 until blockSize) {
                val v = real[i].toInt().coerceIn(-32768, 32767)
                out[offset + i] = v.toShort()
            }
        }
        return out
    }

    override fun extract(
        stegoSamples: ShortArray,
        payloadLength: Int,
        blockSize: Int,
        phaseBinIndices: IntArray
    ): ByteArray {
        val real = DoubleArray(blockSize)
        val imag = DoubleArray(blockSize)
        val bits = mutableListOf<Int>()
        val numBlocks = stegoSamples.size / blockSize
        for (b in 1 until numBlocks) {
            if (bits.size >= payloadLength * 8) break
            val offset = b * blockSize
            for (i in 0 until blockSize) {
                real[i] = stegoSamples[offset + i].toDouble()
                imag[i] = 0.0
            }
            FftHelper.fft(real, imag)
            // Majority vote: read phase from each bin; prefer bin with stronger confidence
            val phases = phaseBinIndices.map { bin -> FftHelper.phase(real, imag, bin) }
            val votes = phases.map { ph -> if (abs(ph) > PI / 2) 1 else 0 }
            val confidences = phases.map { ph -> abs(abs(ph) - PI / 2) }
            val bestIdx = confidences.indices.maxByOrNull { confidences[it] } ?: 0
            bits.add(votes[bestIdx])
        }
        return bits.chunked(8).map { chunk ->
            chunk.foldIndexed(0) { idx, acc, bit -> acc or (bit shl (7 - idx)) }.toByte()
        }.toByteArray().copyOf(payloadLength.coerceAtMost(bits.size / 8))
    }
}
