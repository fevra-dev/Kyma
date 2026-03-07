package com.sonicvault.app.data.crypto

import android.content.Context
import android.os.Build
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.Constants
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

/**
 * Encrypts/decrypts seed phrase using Android Keystore (TEE/StrongBox) and BiometricPrompt.
 * Key requires user authentication (biometric or device credential) with 15s validity.
 * [encrypt] and [decrypt] require a FragmentActivity for showing the biometric dialog.
 *
 * Returns [BiometricAuthResult] so callers can distinguish user cancellation from
 * authentication failure and show actionable error messages.
 */
interface SeedVaultCrypto {
    fun hasKey(): Boolean
    suspend fun encrypt(plaintext: ByteArray, activity: FragmentActivity): BiometricAuthResult<EncryptedPayload>
    suspend fun decrypt(payload: EncryptedPayload, activity: FragmentActivity): BiometricAuthResult<ByteArray>
    fun ensureKeyCreated()
}

/**
 * Android Keystore + AES-256-GCM implementation with BiometricPrompt.
 * Uses StrongBox when available (API 28+), falls back to TEE. Auth validity 15s.
 */
class AndroidSeedVaultCrypto(
    private val context: Context
) : SeedVaultCrypto {

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    private val executor = ContextCompat.getMainExecutor(context)

    override fun hasKey(): Boolean {
        return keyStore.containsAlias(Constants.KEY_ALIAS)
    }

    override fun ensureKeyCreated() {
        if (keyStore.containsAlias(Constants.KEY_ALIAS)) {
            SonicVaultLogger.d("[SeedVaultCrypto] ensureKeyCreated hasKey=true")
            return
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val useStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        val spec = KeyGenParameterSpec.Builder(
            Constants.KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(
                        15,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(15)
                }
            }
            .setInvalidatedByBiometricEnrollment(true)
            .apply { if (useStrongBox) setIsStrongBoxBacked(true) }
            .build()

        try {
            keyGenerator.init(spec)
            keyGenerator.generateKey()
            SonicVaultLogger.d("[SeedVaultCrypto] ensureKeyCreated hasKey=true backend=${if (useStrongBox) "StrongBox" else "TEE"}")
        } catch (e: StrongBoxUnavailableException) {
            SonicVaultLogger.w("[SeedVaultCrypto] StrongBox unavailable, retrying with TEE", e)
            val fallbackSpec = KeyGenParameterSpec.Builder(
                Constants.KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(
                            15,
                            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setUserAuthenticationValidityDurationSeconds(15)
                    }
                }
                .setInvalidatedByBiometricEnrollment(true)
                .build()
            keyGenerator.init(fallbackSpec)
            keyGenerator.generateKey()
            SonicVaultLogger.d("[SeedVaultCrypto] ensureKeyCreated hasKey=true backend=TEE")
        }
    }

    override suspend fun encrypt(plaintext: ByteArray, activity: FragmentActivity): BiometricAuthResult<EncryptedPayload> = suspendCancellableCoroutine { cont ->
        val cipher = getCipher()
        val secretKey = keyStore.getKey(Constants.KEY_ALIAS, null) as? SecretKey
        if (secretKey == null) {
            SonicVaultLogger.e("[SeedVaultCrypto] encrypt no key")
            cont.resume(BiometricAuthResult.Failed("No encryption key."))
            return@suspendCancellableCoroutine
        }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val cryptoObject = BiometricPrompt.CryptoObject(cipher)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Encrypt seed phrase")
            .setSubtitle("Authenticate to secure your backup")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    SonicVaultLogger.d("[SeedVaultCrypto] biometric onAuthenticationSucceeded")
                    result.cryptoObject?.cipher?.let { c ->
                        try {
                            val encrypted = c.doFinal(plaintext)
                            val payload = EncryptedPayload(c.iv!!, encrypted)
                            SonicVaultLogger.d("[SeedVaultCrypto] encrypt plaintextLen=${plaintext.size} resultLen=${payload.ciphertextWithTag.size}")
                            cont.resume(BiometricAuthResult.Success(payload))
                        } catch (e: Exception) {
                            SonicVaultLogger.e("[SeedVaultCrypto] encrypt doFinal failed", e)
                            cont.resume(BiometricAuthResult.Failed("Encryption failed."))
                        }
                    } ?: run {
                        cont.resume(BiometricAuthResult.Failed("Encryption failed."))
                    }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    SonicVaultLogger.w("[SeedVaultCrypto] biometric onAuthenticationError code=$errorCode $errString")
                    // ERROR_USER_CANCELED=10, ERROR_NEGATIVE_BUTTON=13 (platform BiometricPrompt)
                    val result = if (errorCode == 10 || errorCode == 13) {
                        BiometricAuthResult.Cancelled
                    } else {
                        BiometricAuthResult.Failed(errString.toString())
                    }
                    cont.resume(result)
                }
                override fun onAuthenticationFailed() {
                    SonicVaultLogger.d("[SeedVaultCrypto] biometric onAuthenticationFailed")
                }
            }
        )
        prompt.authenticate(promptInfo, cryptoObject)
    }

    override suspend fun decrypt(payload: EncryptedPayload, activity: FragmentActivity): BiometricAuthResult<ByteArray> = suspendCancellableCoroutine { cont ->
        val cipher = getCipher()
        val secretKey = keyStore.getKey(Constants.KEY_ALIAS, null) as? SecretKey
        if (secretKey == null) {
            SonicVaultLogger.e("[SeedVaultCrypto] decrypt no key")
            cont.resume(BiometricAuthResult.Failed("No decryption key."))
            return@suspendCancellableCoroutine
        }
        val spec = GCMParameterSpec(128, payload.iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val cryptoObject = BiometricPrompt.CryptoObject(cipher)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Decrypt seed phrase")
            .setSubtitle("Authenticate to recover your backup")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    SonicVaultLogger.d("[SeedVaultCrypto] biometric onAuthenticationSucceeded")
                    result.cryptoObject?.cipher?.let { c ->
                        try {
                            val decrypted = c.doFinal(payload.ciphertextWithTag)
                            SonicVaultLogger.d("[SeedVaultCrypto] decrypt payloadLen=${payload.ciphertextWithTag.size}")
                            cont.resume(BiometricAuthResult.Success(decrypted))
                        } catch (e: Exception) {
                            SonicVaultLogger.e("[SeedVaultCrypto] decrypt doFinal failed (tag mismatch?)", e)
                            cont.resume(BiometricAuthResult.Failed("Decryption failed. File may have been modified."))
                        }
                    } ?: run {
                        cont.resume(BiometricAuthResult.Failed("Decryption failed."))
                    }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    SonicVaultLogger.w("[SeedVaultCrypto] biometric onAuthenticationError code=$errorCode $errString")
                    // ERROR_USER_CANCELED=10, ERROR_NEGATIVE_BUTTON=13 (platform BiometricPrompt)
                    val result = if (errorCode == 10 || errorCode == 13) {
                        BiometricAuthResult.Cancelled
                    } else {
                        BiometricAuthResult.Failed(errString.toString())
                    }
                    cont.resume(result)
                }
                override fun onAuthenticationFailed() {
                    SonicVaultLogger.d("[SeedVaultCrypto] biometric onAuthenticationFailed")
                }
            }
        )
        prompt.authenticate(promptInfo, cryptoObject)
    }

    private fun getCipher(): Cipher {
        return Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}")
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }
}
