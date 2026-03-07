package com.sonicvault.app.data.codec

import com.sonicvault.app.logging.SonicVaultLogger

/**
 * Reed-Solomon error correction codec over GF(2^8) for acoustic transmission reliability.
 *
 * Wraps payloads with RS parity symbols before ggwave encoding, and strips/corrects
 * after ggwave decoding. Corrects up to [T_CORRECTION] byte errors per block.
 *
 * Primitive polynomial: x^8 + x^4 + x^3 + x^2 + 1 (0x11D) — standard for GF(256).
 * Generator uses consecutive powers of alpha starting at alpha^1.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Reed%E2%80%93Solomon_error_correction">RS Wikipedia</a>
 */
object ReedSolomonCodec {

    /** Number of parity symbols — corrects up to T_CORRECTION = PARITY_SYMBOLS / 2 byte errors. */
    private const val PARITY_SYMBOLS = 32
    const val T_CORRECTION = PARITY_SYMBOLS / 2

    /** Magic header prepended before RS-encoded payload for framing. */
    private val MAGIC = byteArrayOf(0x53, 0x56, 0x52, 0x53) // "SVRS"

    /** GF(2^8) primitive polynomial: x^8 + x^4 + x^3 + x^2 + 1. */
    private const val GF_POLY = 0x11D
    private const val GF_SIZE = 256

    /** Precomputed exp and log tables for GF(2^8) arithmetic. */
    private val gfExp = IntArray(512)
    private val gfLog = IntArray(GF_SIZE)

    init {
        var x = 1
        for (i in 0 until 255) {
            gfExp[i] = x
            gfLog[x] = i
            x = x shl 1
            if (x >= GF_SIZE) x = x xor GF_POLY
        }
        for (i in 255 until 512) {
            gfExp[i] = gfExp[i - 255]
        }
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return gfExp[gfLog[a] + gfLog[b]]
    }

    private fun gfDiv(a: Int, b: Int): Int {
        if (b == 0) throw ArithmeticException("GF division by zero")
        if (a == 0) return 0
        return gfExp[(gfLog[a] - gfLog[b] + 255) % 255]
    }

    private fun gfPow(x: Int, power: Int): Int {
        return gfExp[(gfLog[x] * power) % 255]
    }

    private fun gfInverse(x: Int): Int {
        return gfExp[255 - gfLog[x]]
    }

    /** Multiply two polynomials over GF(2^8). */
    private fun polyMul(p: IntArray, q: IntArray): IntArray {
        val result = IntArray(p.size + q.size - 1)
        for (i in p.indices) {
            for (j in q.indices) {
                result[i + j] = result[i + j] xor gfMul(p[i], q[j])
            }
        }
        return result
    }

    /** Build the RS generator polynomial: prod(x - alpha^i) for i in 1..PARITY_SYMBOLS. */
    private fun buildGeneratorPoly(): IntArray {
        var gen = intArrayOf(1)
        for (i in 1..PARITY_SYMBOLS) {
            gen = polyMul(gen, intArrayOf(1, gfExp[i]))
        }
        return gen
    }

    private val generatorPoly = buildGeneratorPoly()

    /** Evaluate polynomial at x using Horner's method. */
    private fun polyEval(poly: IntArray, x: Int): Int {
        var result = poly[0]
        for (i in 1 until poly.size) {
            result = gfMul(result, x) xor poly[i]
        }
        return result
    }

    /**
     * RS encode: appends [PARITY_SYMBOLS] parity bytes to [data].
     *
     * @param data message bytes (max 223 for a single RS(255,223) block)
     * @return data + parity bytes
     */
    fun rsEncode(data: IntArray): IntArray {
        val encoded = IntArray(data.size + PARITY_SYMBOLS)
        data.copyInto(encoded)

        for (i in data.indices) {
            val coef = encoded[i]
            if (coef != 0) {
                for (j in generatorPoly.indices) {
                    encoded[i + j] = encoded[i + j] xor gfMul(generatorPoly[j], coef)
                }
            }
        }
        // Message portion was XOR'd away during division; restore it
        data.copyInto(encoded)
        return encoded
    }

    /**
     * RS decode with error correction using Berlekamp-Massey + Forney.
     *
     * @param received RS codeword (message + parity)
     * @return corrected message bytes, or null if uncorrectable
     */
    fun rsDecode(received: IntArray): IntArray? {
        val syndromes = IntArray(PARITY_SYMBOLS)
        var hasErrors = false
        for (i in 0 until PARITY_SYMBOLS) {
            syndromes[i] = polyEval(received, gfExp[i + 1])
            if (syndromes[i] != 0) hasErrors = true
        }

        if (!hasErrors) {
            return received.copyOfRange(0, received.size - PARITY_SYMBOLS)
        }

        // Berlekamp-Massey to find error locator polynomial
        val errorLocator = berlekampMassey(syndromes) ?: return null

        // Chien search for error positions
        val errorPositions = chienSearch(errorLocator, received.size) ?: return null

        if (errorPositions.size > T_CORRECTION) return null

        // Forney algorithm for error magnitudes
        val corrected = received.copyOf()
        val errorMagnitudes = forney(syndromes, errorLocator, errorPositions)

        for (i in errorPositions.indices) {
            val pos = received.size - 1 - errorPositions[i]
            if (pos < 0 || pos >= received.size) return null
            corrected[pos] = corrected[pos] xor errorMagnitudes[i]
        }

        // Verify correction worked
        for (i in 0 until PARITY_SYMBOLS) {
            if (polyEval(corrected, gfExp[i + 1]) != 0) return null
        }

        return corrected.copyOfRange(0, corrected.size - PARITY_SYMBOLS)
    }

    /** Berlekamp-Massey algorithm for error locator polynomial. */
    private fun berlekampMassey(syndromes: IntArray): IntArray? {
        var errLoc = intArrayOf(1)
        var oldLoc = intArrayOf(1)

        for (i in syndromes.indices) {
            var delta = syndromes[i]
            for (j in 1 until errLoc.size) {
                delta = delta xor gfMul(errLoc[errLoc.size - 1 - j], syndromes[i - j])
            }

            oldLoc = oldLoc + intArrayOf(0) // shift

            if (delta != 0) {
                if (oldLoc.size > errLoc.size) {
                    val newLoc = IntArray(oldLoc.size)
                    for (j in oldLoc.indices) {
                        newLoc[j] = gfMul(oldLoc[j], delta)
                    }
                    oldLoc = IntArray(errLoc.size)
                    for (j in errLoc.indices) {
                        oldLoc[j] = gfMul(errLoc[j], gfInverse(delta))
                    }
                    errLoc = newLoc
                }

                for (j in oldLoc.indices) {
                    errLoc[errLoc.size - oldLoc.size + j] =
                        errLoc[errLoc.size - oldLoc.size + j] xor gfMul(oldLoc[j], delta)
                }
            }
        }

        val numErrors = errLoc.size - 1
        if (numErrors > T_CORRECTION) return null

        return errLoc
    }

    /** Chien search: find roots of error locator polynomial. */
    private fun chienSearch(errorLocator: IntArray, messageLength: Int): List<Int>? {
        val numErrors = errorLocator.size - 1
        val positions = mutableListOf<Int>()

        for (i in 0 until messageLength) {
            if (polyEval(errorLocator, gfExp[i]) == 0) {
                positions.add(i)
            }
        }

        if (positions.size != numErrors) return null
        return positions
    }

    /** Forney algorithm for error magnitude computation. */
    private fun forney(syndromes: IntArray, errorLocator: IntArray, errorPositions: List<Int>): IntArray {
        // Error evaluator polynomial: syndromes * errorLocator mod x^(2t)
        val omega = IntArray(PARITY_SYMBOLS)
        for (i in 0 until PARITY_SYMBOLS) {
            var val_ = 0
            for (j in 0..i.coerceAtMost(errorLocator.size - 1)) {
                val_ = val_ xor gfMul(syndromes[i - j], errorLocator[j])
            }
            omega[i] = val_
        }

        val magnitudes = IntArray(errorPositions.size)
        for (i in errorPositions.indices) {
            val xiInv = gfExp[255 - errorPositions[i]]

            // Evaluate omega at xiInv
            var omegaVal = 0
            for (j in omega.indices) {
                omegaVal = omegaVal xor gfMul(omega[j], gfPow(xiInv, j))
            }

            // Formal derivative of error locator evaluated at xiInv
            var locDeriv = 0
            for (j in 1 until errorLocator.size step 2) {
                locDeriv = locDeriv xor gfMul(errorLocator[j], gfPow(xiInv, j - 1))
            }

            if (locDeriv == 0) {
                magnitudes[i] = 0
            } else {
                magnitudes[i] = gfMul(gfPow(xiInv, 1), gfDiv(omegaVal, locDeriv))
            }
        }
        return magnitudes
    }

    // --- Public API: encode/decode ByteArray with magic header and RS ---

    /**
     * Encodes payload with magic header + RS parity for acoustic transmission.
     *
     * Format: MAGIC(4) || payload_len(2 BE) || RS_ENCODED(payload)
     * RS block handles up to 223 data bytes. Larger payloads are chunked.
     *
     * @param payload raw data bytes to protect
     * @return framed + RS-encoded bytes ready for ggwave
     */
    fun encode(payload: ByteArray): ByteArray {
        SonicVaultLogger.i("[ReedSolomon] encode payload_len=${payload.size}")
        val dataInts = IntArray(payload.size) { payload[it].toInt() and 0xFF }
        val encoded = rsEncode(dataInts)
        val encodedBytes = ByteArray(encoded.size) { encoded[it].toByte() }

        // Frame: MAGIC(4) + payload_len(2) + encoded
        val result = ByteArray(MAGIC.size + 2 + encodedBytes.size)
        MAGIC.copyInto(result, 0)
        result[MAGIC.size] = ((payload.size shr 8) and 0xFF).toByte()
        result[MAGIC.size + 1] = (payload.size and 0xFF).toByte()
        encodedBytes.copyInto(result, MAGIC.size + 2)

        SonicVaultLogger.i("[ReedSolomon] encoded total=${result.size} parity=$PARITY_SYMBOLS correction_capacity=$T_CORRECTION")
        return result
    }

    /**
     * Decodes RS-encoded payload, correcting up to [T_CORRECTION] byte errors.
     *
     * @param data framed + RS-encoded bytes from ggwave decode
     * @return original payload bytes, or null if magic mismatch or uncorrectable errors
     */
    fun decode(data: ByteArray): ByteArray? {
        SonicVaultLogger.i("[ReedSolomon] decode data_len=${data.size}")

        // Validate magic header
        if (data.size < MAGIC.size + 2) {
            SonicVaultLogger.w("[ReedSolomon] data too short for header")
            return null
        }
        for (i in MAGIC.indices) {
            if (data[i] != MAGIC[i]) {
                SonicVaultLogger.d("[ReedSolomon] magic mismatch — not RS-framed data, passing through")
                return data // Not RS-encoded; return as-is for backward compatibility
            }
        }

        val payloadLen = ((data[MAGIC.size].toInt() and 0xFF) shl 8) or
                (data[MAGIC.size + 1].toInt() and 0xFF)
        val rsData = data.copyOfRange(MAGIC.size + 2, data.size)
        val expectedRsLen = payloadLen + PARITY_SYMBOLS

        if (rsData.size < expectedRsLen) {
            SonicVaultLogger.w("[ReedSolomon] RS block truncated: got=${rsData.size} expected=$expectedRsLen")
            return null
        }

        val receivedInts = IntArray(expectedRsLen) { rsData[it].toInt() and 0xFF }
        val decoded = rsDecode(receivedInts)

        if (decoded == null) {
            SonicVaultLogger.e("[ReedSolomon] decode failed: uncorrectable errors")
            return null
        }

        if (decoded.size != payloadLen) {
            SonicVaultLogger.w("[ReedSolomon] decoded length mismatch: got=${decoded.size} expected=$payloadLen")
            return null
        }

        val result = ByteArray(decoded.size) { decoded[it].toByte() }
        SonicVaultLogger.i("[ReedSolomon] decode success payload_len=${result.size}")
        return result
    }
}
