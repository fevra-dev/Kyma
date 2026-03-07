package com.sonicvault.app.data.crypto

import com.sonicvault.app.logging.SonicVaultLogger
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Derives decoy encryption key and BIP39 decoy seed from duress password.
 *
 * SECURITY NOTES [SVA-002]:
 * - Salt is FIXED ("sonicvault_duress") by design: duress must be deterministic so the same
 *   password always produces the same decoy seed across backup/recovery cycles. A random salt
 *   would need to be stored alongside the payload, revealing that duress mode was used.
 * - Iterations set to 600,000 per OWASP MASVS 2024 recommendation for PBKDF2-HMAC-SHA256.
 *   Previous value of 100K was vulnerable to offline brute-force on modern GPUs.
 * - The fixed salt means precomputation attacks are possible but mitigated by high iteration count.
 *   Duress passwords should be >= 8 chars to maintain adequate security margin.
 */
object DuressKeyDerivation {

    private const val SALT = "sonicvault_duress"
    /** OWASP 2024: minimum 600K iterations for PBKDF2-HMAC-SHA256. Increased from 100K. */
    private const val ITERATIONS = 600_000
    private const val KEY_BITS = 256

    /** Legacy iteration count for backward-compatible decryption of pre-v2 backups. */
    private const val LEGACY_ITERATIONS = 100_000

    /**
     * Derives 256-bit key from password via PBKDF2-HMAC-SHA256.
     * Uses current (600K) iterations for new encryptions.
     */
    fun deriveKey(password: String): ByteArray {
        return deriveKeyWithIterations(password, ITERATIONS)
    }

    /**
     * Derives key with legacy (100K) iterations for decrypting old backups.
     * Called as fallback when current-iteration decryption fails, enabling
     * smooth migration from pre-v2 backups without data loss.
     */
    fun deriveKeyLegacy(password: String): ByteArray {
        SonicVaultLogger.d("[DuressKey] deriving with legacy iterations=$LEGACY_ITERATIONS")
        return deriveKeyWithIterations(password, LEGACY_ITERATIONS)
    }

    /**
     * Core PBKDF2-HMAC-SHA256 key derivation with configurable iteration count.
     * @param password User's duress password.
     * @param iterations PBKDF2 iteration count (600K for current, 100K for legacy).
     * @return 256-bit derived key.
     */
    fun deriveKeyWithIterations(password: String, iterations: Int): ByteArray {
        val saltBytes = SALT.toByteArray(Charsets.UTF_8)
        val passwordChars = password.toCharArray()
        val spec = PBEKeySpec(
            passwordChars,
            saltBytes,
            iterations,
            KEY_BITS
        )
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            /** Wipe password char array to minimize exposure window. */
            spec.clearPassword()
            passwordChars.fill('\u0000')
        }
    }

    /**
     * Generates 12-word BIP39 decoy seed from duress password.
     * Uses first 128 bits of derived key as entropy; adds 4-bit checksum per BIP39.
     *
     * @param password Duress password.
     * @param bip39WordList Ordered BIP39 English word list (2048 words).
     * @return 12-word mnemonic string, space-separated.
     */
    fun deriveDecoySeed(password: String, bip39WordList: List<String>): String {
        val key = deriveKey(password)
        val entropy = key.copyOfRange(0, 16)  // 128 bits
        /** Wipe full key immediately — only entropy slice is needed. */
        key.fill(0)
        val checksumByte = MessageDigest.getInstance("SHA-256").digest(entropy)[0].toInt() and 0xFF
        val checksumBits = checksumByte shr 4  // First 4 bits
        // 12 words = 132 bits: 128 entropy + 4 checksum; each word = 11 bits
        val bits = BooleanArray(132)
        for (i in 0 until 128) {
            val byteIdx = i / 8
            val bitIdx = 7 - (i % 8)
            bits[i] = (entropy[byteIdx].toInt() and 0xFF) and (1 shl bitIdx) != 0
        }
        for (i in 0 until 4) {
            bits[128 + i] = (checksumBits and (1 shl (3 - i))) != 0
        }
        val words = mutableListOf<String>()
        for (w in 0 until 12) {
            var index = 0
            for (b in 0 until 11) {
                index = (index shl 1) or if (bits[w * 11 + b]) 1 else 0
            }
            words.add(bip39WordList[index])
        }
        /** Wipe entropy and bits arrays after mnemonic generation. */
        entropy.fill(0)
        bits.fill(false)
        return words.joinToString(" ")
    }
}
