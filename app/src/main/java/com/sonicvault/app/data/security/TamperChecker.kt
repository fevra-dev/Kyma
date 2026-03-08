package com.sonicvault.app.data.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.sonicvault.app.BuildConfig
import com.sonicvault.app.logging.SonicVaultLogger
import java.security.MessageDigest

/**
 * Runtime APK signature verification to detect repackaging/tampering.
 *
 * Attackers who decompile the APK and strip security checks (biometric, attestation,
 * root detection) must re-sign with a different key. This checker compares the
 * runtime signing certificate fingerprint against the expected release fingerprint.
 *
 * Limitations:
 * - The expected hash is compiled into the APK; a sophisticated attacker can patch it.
 * - This is defense-in-depth, not a standalone protection.
 * - Debug builds use a different signing key; set [expectedSignatureHash] accordingly.
 */
object TamperChecker {

    /**
     * Expected SHA-256 fingerprint of the release signing certificate.
     * Set this to the actual release keystore certificate hash before production.
     * Format: lowercase hex without colons (e.g. "a1b2c3d4...").
     *
     * To get your release key hash:
     *   keytool -list -v -keystore release.keystore | grep SHA256
     */
    private const val EXPECTED_RELEASE_HASH = ""  // TODO: Set before production release

    /**
     * Verifies the APK signing certificate matches the expected fingerprint.
     *
     * @param context Application context.
     * @return [TamperResult.Trusted] if signature matches or check is skipped (debug/empty hash),
     *         [TamperResult.Tampered] if signature mismatch detected.
     */
    fun verify(context: Context): TamperResult {
        if (BuildConfig.DEBUG) {
            val currentHash = getSigningCertificateHash(context)
            SonicVaultLogger.d("[TamperChecker] current signing hash: $currentHash")
        }

        if (EXPECTED_RELEASE_HASH.isEmpty()) {
            SonicVaultLogger.d("[TamperChecker] skipped: no expected hash configured")
            return TamperResult.Trusted
        }

        return try {
            val currentHash = getSigningCertificateHash(context)
            if (currentHash == null) {
                SonicVaultLogger.w("[TamperChecker] could not read signing certificate")
                return TamperResult.Tampered("Signing certificate unavailable")
            }

            if (currentHash.equals(EXPECTED_RELEASE_HASH, ignoreCase = true)) {
                SonicVaultLogger.d("[TamperChecker] signature verified")
                TamperResult.Trusted
            } else {
                SonicVaultLogger.w("[TamperChecker] SIGNATURE MISMATCH — possible repackaging")
                TamperResult.Tampered("APK signature does not match expected release signature")
            }
        } catch (_: Exception) {
            SonicVaultLogger.e("[TamperChecker] signature verification failed")
            TamperResult.Tampered("Signature verification failed")
        }
    }

    /**
     * Computes the SHA-256 hash of the current signing certificate.
     * Use in debug builds to obtain the hash for [EXPECTED_RELEASE_HASH].
     */
    fun computeCurrentHash(context: Context): String? {
        return getSigningCertificateHash(context)
    }

    /**
     * Extracts SHA-256 fingerprint of the first signing certificate.
     * Uses PackageManager.GET_SIGNING_CERTIFICATES on API 28+,
     * falls back to GET_SIGNATURES on older APIs.
     *
     * @return Lowercase hex SHA-256 hash, or null if unavailable.
     */
    @Suppress("DEPRECATION")
    private fun getSigningCertificateHash(context: Context): String? {
        val packageName = context.packageName
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = context.packageManager
                .getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            context.packageManager
                .getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                .signatures
        }

        val cert = signatures?.firstOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(cert.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}

/** Result of APK tamper verification. */
sealed class TamperResult {
    /** APK signature matches expected release fingerprint. */
    data object Trusted : TamperResult()
    /** APK signature mismatch — possible repackaging. */
    data class Tampered(val reason: String) : TamperResult()
}
