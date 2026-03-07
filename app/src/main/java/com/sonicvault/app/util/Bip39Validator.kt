package com.sonicvault.app.util

import android.content.Context
import com.sonicvault.app.logging.SonicVaultLogger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest

/**
 * Validates BIP39 mnemonic seed phrases (12 or 24 words) using the official English word list.
 * Used for validation only; no key derivation.
 *
 * SECURITY PATCH [SVA-006]: Added checksum bit verification per BIP39 spec.
 * Previously only validated word list membership, which accepted invalid mnemonics
 * that could never produce a valid seed — giving users a false sense of security.
 *
 * BIP39 checksum: SHA-256(entropy), first CS bits appended to entropy.
 * 12 words = 128 bits entropy + 4 bits checksum = 132 bits.
 * 24 words = 256 bits entropy + 8 bits checksum = 264 bits.
 */
class Bip39Validator(private val context: Context) {

    private val wordSet: Set<String> by lazy { wordList.toSet() }

    /** Ordered BIP39 word list for derivation (e.g. decoy seed). Index = 11-bit value. */
    val wordList: List<String> by lazy {
        try {
            context.assets.open(BIP39_ASSET_PATH).use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    reader.readLines().map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                }
            }.also { SonicVaultLogger.d("Bip39Validator loaded ${it.size} words") }
        } catch (e: Exception) {
            SonicVaultLogger.e("Bip39Validator failed to load word list", e)
            emptyList()
        }
    }

    /** Map for O(1) word-to-index lookup (needed for checksum verification). */
    private val wordIndexMap: Map<String, Int> by lazy {
        wordList.withIndex().associate { (index, word) -> word to index }
    }

    /**
     * Validates that the phrase is non-empty, has 12 or 24 words, each word is in the BIP39 list,
     * and the checksum bits are correct per BIP39 specification.
     *
     * @return [SeedValidation.Valid] or [SeedValidation.Invalid] with reason.
     */
    fun validate(phrase: String): SeedValidation {
        val trimmed = phrase.trim()
        if (trimmed.isEmpty()) {
            SonicVaultLogger.w("seed phrase validation failed: empty")
            return SeedValidation.Invalid("Seed phrase cannot be empty.")
        }
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size != 12 && words.size != 24) {
            SonicVaultLogger.w("seed phrase validation failed wordCount=${words.size}")
            return SeedValidation.Invalid("Seed phrase must be 12 or 24 words (got ${words.size}).")
        }
        val invalid = words.filter { it.lowercase() !in wordSet }
        if (invalid.isNotEmpty()) {
            SonicVaultLogger.w("seed phrase validation failed: unknown words")
            return SeedValidation.Invalid(
                "Unknown word(s): ${invalid.take(3).joinToString()}" + if (invalid.size > 3) "…" else ""
            )
        }
        /** Verify BIP39 checksum bits to prevent backing up invalid mnemonics. */
        if (!verifyChecksum(words)) {
            SonicVaultLogger.w("seed phrase validation failed: checksum mismatch")
            return SeedValidation.Invalid("Invalid seed phrase — checksum verification failed.")
        }
        return SeedValidation.Valid(trimmed)
    }

    /**
     * Verifies BIP39 checksum bits.
     *
     * Algorithm:
     * 1. Convert each word to its 11-bit index in the word list.
     * 2. Concatenate all bits (12 words = 132 bits, 24 words = 264 bits).
     * 3. Split into entropy (first ENT bits) and checksum (last CS bits).
     *    ENT = wordCount * 11 - CS, where CS = ENT / 32.
     *    12 words: ENT=128, CS=4. 24 words: ENT=256, CS=8.
     * 4. SHA-256(entropy), take first CS bits, compare to extracted checksum.
     *
     * @param words List of lowercase BIP39 words.
     * @return true if checksum is valid.
     */
    private fun verifyChecksum(words: List<String>): Boolean {
        if (wordIndexMap.isEmpty()) return true  // Graceful fallback if word list failed to load

        val totalBits = words.size * 11  // 132 for 12 words, 264 for 24 words
        val checksumBits = words.size / 3  // 4 for 12 words, 8 for 24 words
        val entropyBits = totalBits - checksumBits  // 128 for 12 words, 256 for 24 words
        val entropyBytes = entropyBits / 8

        /** Convert words to a concatenated bit array. */
        val bits = BooleanArray(totalBits)
        for ((wordIdx, word) in words.withIndex()) {
            val index = wordIndexMap[word.lowercase()] ?: return false
            for (bit in 0 until 11) {
                bits[wordIdx * 11 + bit] = (index shr (10 - bit)) and 1 == 1
            }
        }

        /** Extract entropy bytes from the bit array. */
        val entropy = ByteArray(entropyBytes)
        for (i in 0 until entropyBits) {
            if (bits[i]) {
                entropy[i / 8] = (entropy[i / 8].toInt() or (1 shl (7 - (i % 8)))).toByte()
            }
        }

        /** Compute SHA-256 of entropy and extract first CS bits. */
        val hash = MessageDigest.getInstance("SHA-256").digest(entropy)
        for (i in 0 until checksumBits) {
            val hashBit = (hash[i / 8].toInt() shr (7 - (i % 8))) and 1 == 1
            val mnemonicBit = bits[entropyBits + i]
            if (hashBit != mnemonicBit) return false
        }

        return true
    }

    companion object {
        private const val BIP39_ASSET_PATH = "bip39_english.txt"
    }
}

/** Result of BIP39 validation. */
sealed class SeedValidation {
    data class Valid(val phrase: String) : SeedValidation()
    data class Invalid(val message: String) : SeedValidation()
}
