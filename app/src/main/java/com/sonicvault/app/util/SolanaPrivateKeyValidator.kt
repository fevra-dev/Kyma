package com.sonicvault.app.util

import com.sonicvault.app.logging.SonicVaultLogger

/**
 * Validates Solana Ed25519 private key strings (base58-encoded, typically 87–88 chars).
 * Phantom/Solflare export format. Does NOT validate cryptographic structure — only format.
 */
object SolanaPrivateKeyValidator {

    /** Base58 alphabet (no 0, O, I, l). */
    private val BASE58_CHARS = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toSet()

    /** Solana keypair base58 length: 64 bytes → ~87–88 chars. Allow 80–95 for flexibility. */
    private const val MIN_LEN = 80
    private const val MAX_LEN = 95

    /**
     * Validates that the input looks like a Solana private key:
     * - Non-empty, trimmed
     * - Length in [MIN_LEN, MAX_LEN]
     * - All characters in base58 alphabet
     *
     * @return [PrivateKeyValidation.Valid] or [PrivateKeyValidation.Invalid].
     */
    fun validate(input: String): PrivateKeyValidation {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return PrivateKeyValidation.Invalid("Private key cannot be empty.")
        }
        if (trimmed.length < MIN_LEN || trimmed.length > MAX_LEN) {
            SonicVaultLogger.w("[SolanaPrivateKeyValidator] invalid length=${trimmed.length}")
            return PrivateKeyValidation.Invalid(
                "Solana private key should be 87–88 characters (got ${trimmed.length})."
            )
        }
        val invalid = trimmed.filter { it !in BASE58_CHARS }
        if (invalid.isNotEmpty()) {
            SonicVaultLogger.w("[SolanaPrivateKeyValidator] invalid chars")
            return PrivateKeyValidation.Invalid(
                "Private key must use base58 characters only (no 0, O, I, l)."
            )
        }
        return PrivateKeyValidation.Valid(trimmed)
    }

    /** Returns true if input appears to be a private key (long base58 string). */
    fun looksLikePrivateKey(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.length < 50) return false
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size > 1) return false
        return trimmed.all { it in BASE58_CHARS }
    }
}

sealed class PrivateKeyValidation {
    data class Valid(val key: String) : PrivateKeyValidation()
    data class Invalid(val message: String) : PrivateKeyValidation()
}
