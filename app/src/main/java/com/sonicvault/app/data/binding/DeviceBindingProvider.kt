package com.sonicvault.app.data.binding

import android.content.Context
import android.provider.Settings
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.Constants
import java.security.MessageDigest

/**
 * Provides a stable device binding identifier for "backup only on this device".
 * Used to enforce that recovery is only allowed on the device that created the backup.
 * Optionally supports TEE attestation (key attestation certificate) for stronger binding.
 */
interface DeviceBindingProvider {
    /**
     * Returns a 32-byte SHA-256 hash identifying this device for backup binding.
     * Same device always returns the same value (until factory reset / app reinstall affects components).
     */
    fun getDeviceBindingHash(): ByteArray

    /**
     * Returns true if [storedBindingHash] matches the current device's binding hash.
     */
    fun isSameDevice(storedBindingHash: ByteArray): Boolean
}

/**
 * Implementation using Android ID + package name (stable per device per app).
 * On Android 8+, ANDROID_ID is unique per app+device; we still add package for clarity.
 *
 * SECURITY NOTE [SVA-011]: ANDROID_ID can be spoofed on rooted devices via Xposed/LSPosed
 * frameworks. Stronger binding factors (TEE key attestation, hardware serial, Build.FINGERPRINT)
 * are recommended for future versions but would break backward compatibility with existing backups.
 * Password mode (v3) already bypasses device binding for cross-device recovery.
 * Constant-time comparison prevents timing side-channel attacks on the hash.
 */
class AndroidDeviceBindingProvider(private val context: Context) : DeviceBindingProvider {

    override fun getDeviceBindingHash(): ByteArray {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        val packageName = context.packageName
        val combined = "$androidId|$packageName"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(combined.toByteArray(Charsets.UTF_8))
        SonicVaultLogger.d("[DeviceBinding] getDeviceBindingHash len=${hash.size}")
        return hash
    }

    override fun isSameDevice(storedBindingHash: ByteArray): Boolean {
        if (storedBindingHash.size != Constants.DEVICE_BINDING_HASH_BYTES) return false
        val current = getDeviceBindingHash()
        /** Constant-time comparison prevents timing side-channel leaking binding hash bytes. */
        val same = java.security.MessageDigest.isEqual(current, storedBindingHash)
        if (!same) SonicVaultLogger.w("[DeviceBinding] isSameDevice=false (different device or app)")
        return same
    }

}

/**
 * Optional: TEE attestation checker. When creating a key with attestation challenge,
 * the attestation certificate chain can be verified to ensure key is hardware-backed.
 * For "backup only on this device" we use [DeviceBindingProvider]; this is for future
 * server-side or strict attestation verification.
 */
interface AttestationChecker {
    /**
     * Returns attestation payload (e.g. certificate chain or token) for the Keystore key, if supported.
     * API 28+ supports key attestation.
     */
    fun getAttestationIfSupported(): ByteArray?
}

/**
 * TEE-backed attestation checker using TeeKeyAttestationProvider.
 * Generates a Keystore key with attestation challenge and verifies hardware backing.
 * Returns attestation status as a serialized byte (0=unavailable, 1=software, 2=hardware).
 */
class TeeAttestationCheckerImpl(private val context: Context) : AttestationChecker {
    override fun getAttestationIfSupported(): ByteArray? {
        return try {
            val result = TeeKeyAttestationProvider.attest()
            val statusByte = when (result) {
                is TeeAttestationResult.HardwareBacked -> 2.toByte()
                is TeeAttestationResult.SoftwareBacked -> 1.toByte()
                is TeeAttestationResult.Unavailable -> 0.toByte()
            }
            SonicVaultLogger.d("[AttestationChecker] TEE attestation status=$statusByte")
            byteArrayOf(statusByte)
        } catch (e: Exception) {
            SonicVaultLogger.w("[AttestationChecker] TEE attestation failed: ${e.message}")
            null
        }
    }
}
