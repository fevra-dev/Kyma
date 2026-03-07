package com.sonicvault.app.data.sound

import com.sonicvault.app.logging.SonicVaultLogger
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.sqrt
import java.util.Random

/**
 * LT-code (Luby Transform) fountain codes for noise-robust acoustic transmission.
 *
 * WHY FOUNTAIN CODES:
 *   Current approach: transmit payload 2–3 times, hope one decode succeeds.
 *   This fails under 30%+ packet loss (budget Android at 3m, noisy environments).
 *
 *   LT codes are RATELESS: the transmitter generates an endless stream of
 *   encoded "droplets". The receiver accumulates until it has enough to decode.
 *   ANY subset of ~k + overhead droplets works — it doesn't matter WHICH arrive.
 *   Tolerates up to ~50% loss without degradation.
 *
 * ALGORITHM — ROBUST SOLITON DISTRIBUTION:
 *   Each droplet has a 2-byte random seed.
 *   The seed → degree d (from Robust Soliton) → d source symbol indices.
 *   Droplet payload = XOR of those d source symbols.
 *   Receiver builds XOR equation system, solves via belief propagation.
 *
 * PARAMETERS FOR SONICVAULT:
 *   payload = 157 bytes (v2 overhead + 24-word seed)
 *   k = ceil(157 / 16) = 10 symbols of 16 bytes
 *   Expected droplets to decode: ~k * 1.5 = 15–20
 *   Each droplet = 18 bytes (16 data + 2 seed header)
 *   At 10 B/s ggwave DSS: ~2s per droplet → decode in ~30–40s
 *
 * PATENT NOTE:
 *   Raptor codes (RaptorQ, RFC 6330) have Qualcomm patents that may have
 *   expired ~2025. This implementation uses LT codes which are unencumbered.
 */
interface FountainCodec {

    fun createEncoder(data: ByteArray, symbolSizeBytes: Int = 16): FountainEncoder
    fun createDecoder(totalDataBytes: Int, symbolSizeBytes: Int = 16): FountainDecoder
}

interface FountainEncoder {
    /** Returns next droplet: [seed:2][data:16] */
    fun nextDroplet(): ByteArray
    val symbolCount: Int
}

interface FountainDecoder {
    /** Feed a received droplet. Returns decoded ByteArray when done, null if more needed. */
    fun feedDroplet(droplet: ByteArray): ByteArray?
    val receivedDropletCount: Int
    val isDecoded: Boolean
}

/**
 * LT Fountain codec implementation.
 */
class LtFountainCodec : FountainCodec {

    override fun createEncoder(data: ByteArray, symbolSizeBytes: Int): FountainEncoder =
        LtEncoder(data, symbolSizeBytes)

    override fun createDecoder(totalDataBytes: Int, symbolSizeBytes: Int): FountainDecoder =
        LtDecoder(totalDataBytes, symbolSizeBytes)
}

// ─── Encoder ─────────────────────────────────────────────────────────────────

private class LtEncoder(
    data: ByteArray,
    private val symbolSize: Int
) : FountainEncoder {

    private val symbols: Array<ByteArray>
    override val symbolCount: Int

    private var dropletCount = 0
    private val rngSeed = java.util.concurrent.atomic.AtomicInteger(0)

    init {
        val paddedLen = ceil(data.size.toDouble() / symbolSize).toInt() * symbolSize
        val padded = data.copyOf(paddedLen)
        symbolCount = paddedLen / symbolSize
        symbols = Array(symbolCount) { i ->
            padded.copyOfRange(i * symbolSize, (i + 1) * symbolSize)
        }
        SonicVaultLogger.d("[FountainEncoder] ${data.size} bytes → $symbolCount symbols × $symbolSize bytes")
    }

    override fun nextDroplet(): ByteArray {
        val seed = rngSeed.getAndIncrement()
        val rng = Random(seed.toLong())
        val degree = RobustSoliton.sample(rng, symbolCount)
        val indices = pickDistinctIndices(rng, degree, symbolCount)

        val dropletData = symbols[indices[0]].copyOf()
        for (i in 1 until degree) {
            val sym = symbols[indices[i]]
            for (j in dropletData.indices) dropletData[j] = (dropletData[j].toInt() xor sym[j].toInt()).toByte()
        }

        val result = ByteArray(2 + symbolSize)
        result[0] = (seed and 0xFF).toByte()
        result[1] = ((seed shr 8) and 0xFF).toByte()
        dropletData.copyInto(result, destinationOffset = 2)

        dropletCount++
        SonicVaultLogger.d("[FountainEncoder] droplet #$dropletCount degree=$degree")
        return result
    }
}

// ─── Decoder ─────────────────────────────────────────────────────────────────

private class LtDecoder(
    private val totalDataBytes: Int,
    private val symbolSize: Int
) : FountainDecoder {

    override var receivedDropletCount = 0
        private set
    override var isDecoded = false
        private set

    private val k: Int = ceil(totalDataBytes.toDouble() / symbolSize).toInt()

    private data class Equation(val indices: MutableSet<Int>, val value: ByteArray)
    private val equations = mutableListOf<Equation>()

    private val solved = arrayOfNulls<ByteArray>(k)
    private var solvedCount = 0

    override fun feedDroplet(droplet: ByteArray): ByteArray? {
        if (isDecoded) return assembleSolution()

        val seed = ((droplet[0].toInt() and 0xFF) or ((droplet[1].toInt() and 0xFF) shl 8))
        val data = droplet.copyOfRange(2, droplet.size)

        val rng = Random(seed.toLong())
        val degree = RobustSoliton.sample(rng, k)
        val indices = pickDistinctIndices(rng, degree, k).toMutableSet()

        receivedDropletCount++
        SonicVaultLogger.d("[FountainDecoder] droplet #$receivedDropletCount degree=$degree")

        equations.add(Equation(indices, data.copyOf()))
        beliefPropagate()

        if (solvedCount == k) {
            isDecoded = true
            val overhead = receivedDropletCount - k
            SonicVaultLogger.i("[FountainDecoder] decoded after $receivedDropletCount droplets (k=$k, overhead=$overhead)")
            return assembleSolution()
        }

        SonicVaultLogger.d("[FountainDecoder] progress: $solvedCount/$k symbols")
        return null
    }

    private fun beliefPropagate() {
        var progress = true
        while (progress) {
            progress = false

            for (eq in equations) {
                val toRemove = eq.indices.filter { solved[it] != null }.toList()
                for (idx in toRemove) {
                    val sym = solved[idx]!!
                    for (j in eq.value.indices) eq.value[j] = (eq.value[j].toInt() xor sym[j].toInt()).toByte()
                    eq.indices.remove(idx)
                    progress = true
                }
            }

            for (eq in equations) {
                if (eq.indices.size == 1) {
                    val idx = eq.indices.first()
                    if (solved[idx] == null) {
                        solved[idx] = eq.value.copyOf()
                        solvedCount++
                        progress = true
                    }
                }
            }

            equations.removeAll { it.indices.isEmpty() }
        }
    }

    private fun assembleSolution(): ByteArray {
        val full = ByteArray(k * symbolSize)
        for (i in 0 until k) {
            solved[i]?.copyInto(full, i * symbolSize) ?: ByteArray(symbolSize).copyInto(full, i * symbolSize)
        }
        return full.copyOf(totalDataBytes)
    }
}

// ─── Robust Soliton Distribution ─────────────────────────────────────────────

private object RobustSoliton {
    /**
     * Sample degree d from Robust Soliton Distribution for k symbols.
     * Parameters: c=0.03, delta=0.5 (tuned for small k ~10–20).
     */
    fun sample(rng: Random, k: Int): Int {
        val c = 0.03
        val delta = 0.5
        val R = c * sqrt(k.toDouble()) * ln(k.toDouble() / delta)

        val dist = DoubleArray(k + 1)
        dist[1] = 1.0 / k
        for (d in 2..k) {
            dist[d] = 1.0 / (d.toDouble() * (d - 1).toDouble())
        }

        val spikePos = (k / R).toInt().coerceIn(2, k)
        for (d in 1..spikePos) {
            dist[d] += R / (d.toDouble() * k.toDouble())
        }
        dist[spikePos] += R / k

        val total = dist.sum()
        for (d in dist.indices) dist[d] /= total

        val u = rng.nextDouble()
        var cdf = 0.0
        for (d in 1..k) {
            cdf += dist[d]
            if (u <= cdf) return d
        }
        return k
    }
}

private fun pickDistinctIndices(rng: Random, count: Int, k: Int): IntArray {
    val set = LinkedHashSet<Int>(count * 2)
    while (set.size < count) {
        set.add(rng.nextInt(k))
    }
    return set.toIntArray()
}
