package com.sonicvault.app.data.stego

import com.sonicvault.app.logging.SonicVaultLogger

/**
 * LSB steganography: embed secret bytes into the lowest bits of audio samples,
 * extract by reading those bits in order. Capacity = (samples.size * numLSB) / 8 bytes.
 */
interface LSBSteganography {
    fun embed(coverSamples: ShortArray, secret: ByteArray, numLSB: Int = 2): ShortArray
    fun extract(stegoSamples: ShortArray, payloadLength: Int, numLSB: Int = 2): ByteArray
}

class LSBSteganographyImpl : LSBSteganography {

    override fun embed(coverSamples: ShortArray, secret: ByteArray, numLSB: Int): ShortArray {
        require(coverSamples.size * numLSB >= secret.size * 8) {
            "Cover capacity ${coverSamples.size * numLSB / 8} bytes < secret ${secret.size} bytes"
        }
        SonicVaultLogger.d("[LSBSteganography] embed coverSamples=${coverSamples.size} secretBytes=${secret.size} numLSB=$numLSB")
        val secretBits = secret.flatMap { byte -> (7 downTo 0).map { (byte.toInt() shr it) and 1 } }
        val stego = coverSamples.copyOf()
        var bitIdx = 0
        for (i in stego.indices) {
            if (bitIdx >= secretBits.size) break
            var s = stego[i].toInt()
            s = s and (-1 shl numLSB)
            for (j in 0 until numLSB) {
                if (bitIdx < secretBits.size) {
                    s = s or (secretBits[bitIdx] shl j)
                    bitIdx++
                }
            }
            stego[i] = s.toShort()
        }
        return stego
    }

    override fun extract(stegoSamples: ShortArray, payloadLength: Int, numLSB: Int): ByteArray {
        SonicVaultLogger.d("[LSBSteganography] extract stegoSamples=${stegoSamples.size} payloadLength=$payloadLength")
        val bits = mutableListOf<Int>()
        for (sample in stegoSamples) {
            for (j in 0 until numLSB) {
                if (bits.size >= payloadLength * 8) break
                bits.add((sample.toInt() shr j) and 1)
            }
        }
        return bits.chunked(8).map { chunk ->
            chunk.foldIndexed(0) { idx, acc, b -> acc or (b shl (7 - idx)) }.toByte()
        }.toByteArray().copyOf(payloadLength.coerceAtMost(bits.size / 8))
    }
}
