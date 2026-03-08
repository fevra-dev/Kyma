package com.sonicvault.app.data.binding

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.sonicvault.app.logging.SonicVaultLogger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate

/**
 * TEE Key Attestation provider for stronger device binding.
 *
 * Generates a Keystore key with an attestation challenge and extracts the
 * certificate chain. If the key is hardware-backed (TEE/StrongBox), the
 * attestation certificate can prove the device is genuine.
 *
 * Used as supplementary binding alongside ANDROID_ID. Existing backups
 * still verify by ANDROID_ID hash; new backups can include TEE attestation
 * flag for stronger verification.
 *
 * Requires Android 8.0+ (API 26) for key attestation.
 */
object TeeKeyAttestationProvider {

    private const val ATTESTATION_KEY_ALIAS = "sonicvault_tee_attestation"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    /**
     * Generates a key with attestation challenge and returns the attestation result.
     *
     * @return [TeeAttestationResult.HardwareBacked] if TEE/StrongBox backed,
     *         [TeeAttestationResult.SoftwareBacked] if software-only,
     *         [TeeAttestationResult.Unavailable] if attestation not supported.
     */
    fun attest(): TeeAttestationResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            SonicVaultLogger.d("[TeeAttestation] API ${Build.VERSION.SDK_INT} < 26; attestation unavailable")
            return TeeAttestationResult.Unavailable("Requires Android 8.0+")
        }

        return try {
            /** Generate an EC key pair with attestation challenge. */
            val challenge = java.security.SecureRandom().let { sr ->
                ByteArray(32).also { sr.nextBytes(it) }
            }

            val spec = KeyGenParameterSpec.Builder(
                ATTESTATION_KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(challenge)
                .build()

            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE
            )
            kpg.initialize(spec)
            kpg.generateKeyPair()

            /** Extract certificate chain and verify hardware backing. */
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val certChain = keyStore.getCertificateChain(ATTESTATION_KEY_ALIAS)

            if (certChain.isNullOrEmpty()) {
                SonicVaultLogger.w("[TeeAttestation] no certificate chain returned")
                return TeeAttestationResult.Unavailable("No attestation certificate chain")
            }

            /** Parse attestation extension from leaf certificate. */
            val leafCert = certChain[0] as? X509Certificate
            val isHardwareBacked = isHardwareAttestation(leafCert)

            /** Clean up the attestation key (single-use for verification). */
            keyStore.deleteEntry(ATTESTATION_KEY_ALIAS)

            if (isHardwareBacked) {
                SonicVaultLogger.d("[TeeAttestation] hardware-backed attestation confirmed")
                TeeAttestationResult.HardwareBacked(certChain.size)
            } else {
                SonicVaultLogger.d("[TeeAttestation] software-only attestation")
                TeeAttestationResult.SoftwareBacked
            }
        } catch (e: Exception) {
            SonicVaultLogger.w("[TeeAttestation] attestation failed: ${e.message}")
            /** Clean up key if it was partially created. */
            try {
                val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
                ks.load(null)
                ks.deleteEntry(ATTESTATION_KEY_ALIAS)
            } catch (_: Exception) { /* ignore cleanup failure */ }
            TeeAttestationResult.Unavailable(e.message ?: "Attestation failed")
        }
    }

    /**
     * Checks if the attestation certificate indicates hardware-backed key.
     * Parses the Android Key Attestation Extension (OID 1.3.6.1.4.1.11129.2.1.17).
     *
     * The root ASN.1 SEQUENCE layout (per Google's spec):
     *   [0] attestationVersion INTEGER
     *   [1] attestationSecurityLevel INTEGER  ← 0=Software, 1=TEE, 2=StrongBox
     *   [2] keymasterVersion INTEGER
     *   [3] keymasterSecurityLevel INTEGER
     *   ...
     */
    private fun isHardwareAttestation(cert: X509Certificate?): Boolean {
        if (cert == null) return false
        val extensionBytes = cert.getExtensionValue("1.3.6.1.4.1.11129.2.1.17")
            ?: return false
        try {
            val inner = org.bouncycastle.asn1.ASN1OctetString.getInstance(extensionBytes).octets
            val seq = org.bouncycastle.asn1.ASN1Sequence.getInstance(inner)
            val securityLevel = (seq.getObjectAt(1) as org.bouncycastle.asn1.ASN1Integer).intValueExact()
            return securityLevel >= 1
        } catch (e: Exception) {
            SonicVaultLogger.w("[TeeAttestation] ASN.1 parse failed, assuming software-backed")
            return false
        }
    }
}

/** Result of TEE key attestation check. */
sealed class TeeAttestationResult {
    /** Key is backed by TEE or StrongBox hardware. */
    data class HardwareBacked(val certChainLength: Int) : TeeAttestationResult()
    /** Key exists but is software-only (emulator, older device). */
    data object SoftwareBacked : TeeAttestationResult()
    /** Attestation not available on this device/API level. */
    data class Unavailable(val reason: String) : TeeAttestationResult()
}
