package com.sonicvault.app.data.crypto

import com.sonicvault.app.util.wipe
import de.mkammerer.argon2.Argon2Advanced
import de.mkammerer.argon2.Argon2Factory
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64

/**
 * Argon2id key derivation for optional password-based encryption.
 * Memory-hard KDF per COMPILED_RESEARCH_REPORT: 64 MB, 4–8 threads, ≥1 s/attempt.
 * Used when user prefers password over biometric (cross-device recovery, no fingerprint).
 */
object Argon2KeyDerivation {

    /** Memory cost in KB (65536 KB = 64 MB). Consumer devices balance. */
    private const val MEMORY_KB = 65536

    /** Iterations; tune for ~1 s per attempt on target devices. */
    private const val ITERATIONS = 3

    /** Parallelism (threads). */
    private const val PARALLELISM = 4

    /** Salt length in bytes. Stored with ciphertext. */
    const val SALT_LENGTH = 16

    /** Derived key length (AES-256). */
    private const val KEY_LENGTH = 32

    /**
     * Derives 256-bit AES key from password via Argon2id.
     * Prefer this overload when caller can provide CharArray (wipeable).
     *
     * @param password User password. Wiped from memory after use.
     * @param salt 16-byte random salt (from [generateSalt]).
     * @return 32-byte key for AES-256-GCM.
     */
    fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id) as Argon2Advanced
        return try {
            val encoded = argon2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, password, StandardCharsets.UTF_8, salt)
            // Encoded format: $argon2id$v=19$m=65536,t=3,p=4$b64salt$b64hash — extract last segment
            val parts = encoded.split("$")
            val b64Hash = parts.lastOrNull() ?: throw IllegalArgumentException("Invalid Argon2 hash format")
            val rawHash = Base64.getDecoder().decode(b64Hash)
            rawHash.copyOf(KEY_LENGTH)
        } finally {
            argon2.wipeArray(password)
        }
    }

    /**
     * Derives 256-bit AES key from password via Argon2id.
     * Converts to CharArray internally and wipes; caller should avoid retaining String.
     *
     * @param password User password.
     * @param salt 16-byte random salt (from [generateSalt]).
     * @return 32-byte key for AES-256-GCM.
     */
    fun deriveKey(password: String, salt: ByteArray): ByteArray =
        password.toCharArray().let { pw ->
            try {
                deriveKey(pw, salt)
            } finally {
                pw.wipe()
            }
        }

    /**
     * Generates cryptographically random 16-byte salt for Argon2id.
     * Must be stored with ciphertext for decryption.
     */
    fun generateSalt(): ByteArray = ByteArray(SALT_LENGTH).apply {
        SecureRandom().nextBytes(this)
    }
}
