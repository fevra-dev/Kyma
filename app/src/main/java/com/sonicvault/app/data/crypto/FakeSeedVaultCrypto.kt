package com.sonicvault.app.data.crypto

import androidx.fragment.app.FragmentActivity
import com.sonicvault.app.BuildConfig
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * In-memory AES-GCM crypto for debug builds and instrumented tests.
 * No Keystore, no biometric prompt. Same payload format as production so
 * backup/recover flow can be tested end-to-end.
 *
 * Used when BuildConfig.DEBUG: enables Shamir (Split Backup) and Create Backup
 * without N biometric prompts. Backups created in debug are only decryptable
 * in the same app session (ephemeral key).
 */
class FakeSeedVaultCrypto : SeedVaultCrypto {

    init {
        require(BuildConfig.DEBUG) { "FakeSeedVaultCrypto must not be used in release builds" }
    }

    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    override fun hasKey(): Boolean = true

    override fun ensureKeyCreated() {}

    override suspend fun encrypt(plaintext: ByteArray, activity: FragmentActivity): BiometricAuthResult<EncryptedPayload> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plaintext)
        return BiometricAuthResult.Success(EncryptedPayload(cipher.iv!!, encrypted))
    }

    override suspend fun decrypt(payload: EncryptedPayload, activity: FragmentActivity): BiometricAuthResult<ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, payload.iv))
        return BiometricAuthResult.Success(cipher.doFinal(payload.ciphertextWithTag))
    }
}
