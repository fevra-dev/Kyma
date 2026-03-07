package com.sonicvault.app.data.crypto

import com.sonicvault.app.util.wipe
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HMAC-SHA256 key derivation for password-based encryption.
 * Pure Java fallback when Argon2 (argon2-jvm/JNA) fails to load on emulator or certain devices.
 * 600k iterations per OWASP 2023 password storage cheat sheet for PBKDF2-HMAC-SHA256.
 * Salt must be 16 bytes, unique per encryption.
 */
object Pbkdf2KeyDerivation {

    private const val ITERATIONS = 600_000
    private const val KEY_BITS = 256
    const val SALT_LENGTH = 16

    /**
     * Derives 256-bit AES key from password via PBKDF2-HMAC-SHA256.
     * Prefer this overload when caller can provide CharArray (wipeable).
     *
     * @param password User password. Wiped after use.
     * @param salt 16-byte random salt (from [generateSalt]).
     * @return 32-byte key for AES-256-GCM.
     */
    fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        require(salt.size == SALT_LENGTH) { "Salt must be $SALT_LENGTH bytes" }
        val spec = PBEKeySpec(
            password,
            salt,
            ITERATIONS,
            KEY_BITS
        )
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
            password.wipe()
        }
    }

    /**
     * Derives 256-bit AES key from password via PBKDF2-HMAC-SHA256.
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

    fun generateSalt(): ByteArray = ByteArray(SALT_LENGTH).apply {
        SecureRandom().nextBytes(this)
    }
}
