package com.sonicvault.app.util

import android.content.Context
import com.sonicvault.app.logging.SonicVaultLogger
import java.security.MessageDigest

/**
 * Converts between BIP39 mnemonic phrases and raw entropy bytes.
 * Used for SLIP-0039: master secret must be 128 or 256 bits (entropy from BIP39).
 *
 * BIP39 structure:
 * - 12 words: 132 bits = 128 entropy + 4 checksum (11 bits per word)
 * - 24 words: 264 bits = 256 entropy + 8 checksum
 */
class Bip39Entropy(private val context: Context) {

    private val wordList: List<String> by lazy {
        try {
            context.assets.open("bip39_english.txt").use { input ->
                input.bufferedReader().readLines()
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
            }.also { SonicVaultLogger.d("[Bip39Entropy] loaded ${it.size} words") }
        } catch (e: Exception) {
            SonicVaultLogger.e("[Bip39Entropy] failed to load word list", e)
            emptyList()
        }
    }

    private val wordToIndex: Map<String, Int> by lazy {
        wordList.mapIndexed { idx, word -> word to idx }.toMap()
    }

    /**
     * Extracts entropy from a BIP39 phrase. Validates checksum.
     * @return 16 bytes (128-bit) or 32 bytes (256-bit), or null if invalid
     */
    fun phraseToEntropy(phrase: String): ByteArray? {
        val words = phrase.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size != 12 && words.size != 24) {
            SonicVaultLogger.w("[Bip39Entropy] phraseToEntropy: invalid word count=${words.size}")
            return null
        }
        val entropyBits = if (words.size == 12) 128 else 256
        val totalBits = words.size * 11
        val bits = BooleanArray(totalBits)
        for (w in words.indices) {
            val idx = wordToIndex[words[w]] ?: run {
                SonicVaultLogger.w("[Bip39Entropy] phraseToEntropy: unknown word")
                bits.fill(false) // Wipe intermediate bits on error path
                return null
            }
            for (b in 0 until 11) {
                bits[w * 11 + b] = (idx and (1 shl (10 - b))) != 0
            }
        }
        val entropyBytes = (entropyBits + 7) / 8
        val entropy = ByteArray(entropyBytes)
        for (i in 0 until entropyBits) {
            val byteIdx = i / 8
            val bitIdx = 7 - (i % 8)
            if (bits[i]) {
                entropy[byteIdx] = (entropy[byteIdx].toInt() or (1 shl bitIdx)).toByte()
            }
        }
        /** Wipe intermediate bit array — contains full entropy in expanded form. */
        bits.fill(false)
        val checksumLen = totalBits - entropyBits
        val computedChecksum = MessageDigest.getInstance("SHA-256").digest(entropy)
        val computedBits = when (checksumLen) {
            4 -> (computedChecksum[0].toInt() and 0xFF) shr 4
            8 -> computedChecksum[0].toInt() and 0xFF
            else -> {
                entropy.fill(0)
                return null
            }
        }
        var storedBits = 0
        for (i in 0 until checksumLen) {
            storedBits = (storedBits shl 1) or if (bits[entropyBits + i]) 1 else 0
        }
        if (storedBits != computedBits) {
            SonicVaultLogger.w("[Bip39Entropy] phraseToEntropy: checksum mismatch")
            entropy.fill(0)
            return null
        }
        return entropy
    }

    /**
     * Converts entropy to BIP39 phrase. Adds checksum per BIP39.
     * @param entropy 16 bytes (128-bit) or 32 bytes (256-bit)
     */
    fun entropyToPhrase(entropy: ByteArray): String? {
        if (entropy.size != 16 && entropy.size != 24 && entropy.size != 32) {
            SonicVaultLogger.w("[Bip39Entropy] entropyToPhrase: invalid length=${entropy.size}")
            return null
        }
        val entropyBits = entropy.size * 8
        val checksumBits = entropyBits / 32
        val checksumByte = MessageDigest.getInstance("SHA-256").digest(entropy)[0].toInt() and 0xFF
        val checksum = when (checksumBits) {
            4 -> checksumByte shr 4
            8 -> checksumByte
            else -> return null
        }
        val totalBits = entropyBits + checksumBits
        val wordCount = totalBits / 11
        val bits = BooleanArray(totalBits)
        for (i in 0 until entropyBits) {
            val byteIdx = i / 8
            val bitIdx = 7 - (i % 8)
            bits[i] = (entropy[byteIdx].toInt() and 0xFF) and (1 shl bitIdx) != 0
        }
        for (i in 0 until checksumBits) {
            bits[entropyBits + i] = (checksum and (1 shl (checksumBits - 1 - i))) != 0
        }
        val words = mutableListOf<String>()
        for (w in 0 until wordCount) {
            var index = 0
            for (b in 0 until 11) {
                index = (index shl 1) or if (bits[w * 11 + b]) 1 else 0
            }
            if (index >= wordList.size) return null
            words.add(wordList[index])
        }
        return words.joinToString(" ")
    }
}
