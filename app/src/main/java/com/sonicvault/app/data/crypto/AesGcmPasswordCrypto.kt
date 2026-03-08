package com.sonicvault.app.data.crypto

import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.Constants
import com.sonicvault.app.util.wipe
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encrypt/decrypt with raw key (password-derived).
 * Used for duress decoy payload; Keystore remains for real seed.
 */
object AesGcmPasswordCrypto {

    private const val KEY_SIZE_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128

    /**
     * Encrypts plaintext with key. Generates random IV; result = iv || ciphertext.
     */
    fun encrypt(plaintext: ByteArray, key: ByteArray): EncryptedPayload? {
        return try {
            val iv = ByteArray(Constants.GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(key, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            val ciphertext = cipher.doFinal(plaintext)
            EncryptedPayload(iv, ciphertext)
        } catch (_: Exception) {
            SonicVaultLogger.e("[AesGcmPasswordCrypto] encrypt failed")
            null
        } finally {
            key.wipe()
            plaintext.wipe()
        }
    }

    /**
     * Decrypts payload with key. Returns null on auth tag mismatch (wrong key).
     */
    fun decrypt(payload: EncryptedPayload, key: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(key, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            cipher.doFinal(payload.ciphertextWithTag)
        } catch (_: Exception) {
            SonicVaultLogger.d("[AesGcmPasswordCrypto] decrypt failed (wrong key or tampered)")
            null
        } finally {
            key.wipe()
        }
    }
}
