package com.sonicvault.app.data.crypto

import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.Constants
import com.sonicvault.app.util.wipe
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based encrypt/decrypt for optional password mode.
 * Argon2id KDF → AES-256-GCM when available. Falls back to PBKDF2 when Argon2 fails to load
 * (e.g. emulator missing JNA native libs). Salt format: 16 bytes = Argon2; 17 bytes with [0]=1 = PBKDF2.
 *
 * Callers should avoid retaining the password String after the call. Prefer CharArray overload when available.
 */
object PasswordSeedVaultCrypto {

    private const val GCM_TAG_LENGTH_BITS = 128
    /** First byte of salt when PBKDF2 was used (Argon2 uses 16-byte salt, no marker). Public for parsePasswordFromBytes. */
    const val PBKDF2_SALT_MARKER: Byte = 1

    /**
     * Encrypts plaintext with password. Salt random per encryption; stored with ciphertext.
     * Tries Argon2 first; on UnsatisfiedLinkError (emulator) falls back to PBKDF2.
     * Converts String to CharArray internally and wipes; caller should avoid retaining password.
     *
     * @param plaintext Seed phrase bytes.
     * @param password User password.
     * @return EncryptedPayload(salt, iv, ciphertext) or null on failure.
     */
    fun encryptWithPassword(plaintext: ByteArray, password: String): EncryptedPayload? =
        password.toCharArray().let { pw ->
            try {
                encryptWithPassword(plaintext, pw)
            } finally {
                pw.wipe()
            }
        }

    /**
     * Encrypts plaintext with password (CharArray). Prefer when caller can provide wipeable array.
     */
    private fun encryptWithPassword(plaintext: ByteArray, password: CharArray): EncryptedPayload? {
        return try {
            SonicVaultLogger.i("[PasswordCrypto] encrypt with password")
            val salt = Pbkdf2KeyDerivation.generateSalt()
            var usedPbkdf2 = false
            val key = try {
                Argon2KeyDerivation.deriveKey(password, salt)
            } catch (e: LinkageError) {
                SonicVaultLogger.w("[PasswordCrypto] Argon2 not available (emulator/JNA?), using PBKDF2 fallback")
                usedPbkdf2 = true
                Pbkdf2KeyDerivation.deriveKey(password, salt)
            }
            try {
                val saltToStore = if (usedPbkdf2) byteArrayOf(PBKDF2_SALT_MARKER) + salt else salt
                val iv = ByteArray(Constants.GCM_IV_LENGTH).apply { SecureRandom().nextBytes(this) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                val ciphertext = cipher.doFinal(plaintext)
                EncryptedPayload(iv, ciphertext, saltToStore)
            } finally {
                key.wipe()
            }
        } catch (e: Exception) {
            SonicVaultLogger.e("[PasswordCrypto] encrypt failed", e)
            null
        }
    }

    /**
     * Decrypts payload with password. Returns null if wrong password.
     * Converts String to CharArray internally and wipes; caller should avoid retaining password.
     *
     * @return Decrypted plaintext or null. Caller must wipe the returned ByteArray when done.
     */
    fun decryptWithPassword(payload: EncryptedPayload, password: String): ByteArray? =
        password.toCharArray().let { pw ->
            try {
                decryptWithPassword(payload, pw)
            } finally {
                pw.wipe()
            }
        }

    /**
     * Decrypts payload with password (CharArray). Detects KDF from salt: 17 bytes with first byte=1 use PBKDF2; else Argon2.
     */
    private fun decryptWithPassword(payload: EncryptedPayload, password: CharArray): ByteArray? {
        val salt = payload.salt ?: return null
        return try {
            val key = when {
                salt.size == 17 && salt[0] == PBKDF2_SALT_MARKER -> {
                    val actualSalt = salt.copyOfRange(1, 17)
                    Pbkdf2KeyDerivation.deriveKey(password, actualSalt)
                }
                else -> {
                    try {
                        Argon2KeyDerivation.deriveKey(password, salt)
                    } catch (_: LinkageError) {
                        SonicVaultLogger.w("[PasswordCrypto] Argon2 not available; cannot decrypt Argon2 payload")
                        return null
                    }
                }
            }
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.iv))
                cipher.doFinal(payload.ciphertextWithTag)
            } finally {
                key.wipe()
            }
        } catch (e: Exception) {
            SonicVaultLogger.d("[PasswordCrypto] decrypt failed (wrong password or tampered)")
            null
        }
    }
}
