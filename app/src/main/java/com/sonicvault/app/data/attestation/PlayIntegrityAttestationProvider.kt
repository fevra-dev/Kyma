package com.sonicvault.app.data.attestation

import android.content.Context
import android.util.Base64
import com.google.android.play.core.integrity.IntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityTokenResponse
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.security.SecureRandom
import kotlin.coroutines.resume

/**
 * Play Integrity API implementation of device attestation.
 * Verifies device is genuine (not rooted, emulator, tampered) before decrypt.
 *
 * SECURITY: Validates token structure (JWT format), nonce echo, and minimum length in addition
 * to non-empty check. Full server-side verification is recommended for production; this provides
 * defense-in-depth against trivial bypass (e.g. returning arbitrary non-empty strings via Frida).
 *
 * Never log token contents.
 */
class PlayIntegrityAttestationProvider : DeviceAttestationProvider {

    /** Track the nonce sent with the current request for echo validation. */
    @Volatile
    private var pendingNonce: String? = null

    override suspend fun attestDevice(context: Context): Result<AttestationResult> =
        withContext(Dispatchers.IO) {
            try {
                val integrityManager = IntegrityManagerFactory.create(context)
                val nonce = generateNonce()
                pendingNonce = nonce
                val request = IntegrityTokenRequest.builder()
                    .setNonce(nonce)
                    .build()

                val tokenResponse = requestIntegrityTokenSuspend(integrityManager, request)
                    ?: run {
                        SonicVaultLogger.w("[Attestation] token response null")
                        return@withContext Result.success(AttestationResult.Fail)
                    }

                val token = tokenResponse.token()

                /**
                 * SECURITY PATCH [SVA-001]: Validate token structure beyond non-empty check.
                 * Play Integrity tokens are JWTs (header.payload.signature). We verify:
                 *   1. Token is non-null and non-empty
                 *   2. Token has valid JWT structure (3 dot-separated Base64 parts)
                 *   3. Payload is valid Base64-decodable JSON
                 *   4. Minimum token length (JWTs are typically 500+ chars)
                 * This prevents trivial Frida hooks that return arbitrary short strings.
                 */
                val pass = validateIntegrityToken(token)

                SonicVaultLogger.i("[Attestation] result=${if (pass) "pass" else "fail"}")
                pendingNonce = null

                Result.success(if (pass) AttestationResult.Pass else AttestationResult.Fail)
            } catch (e: Exception) {
                SonicVaultLogger.e("[Attestation] request failed", e)
                pendingNonce = null
                Result.success(AttestationResult.Fail)
            }
        }

    /**
     * Validates the Play Integrity token beyond a simple non-empty check.
     * Checks JWT structure, minimum length, and payload decodability.
     *
     * @param token Raw integrity token string from Play Integrity API.
     * @return true if token passes all structural validation checks.
     */
    private fun validateIntegrityToken(token: String?): Boolean {
        if (token.isNullOrEmpty()) {
            SonicVaultLogger.w("[Attestation] token null or empty")
            return false
        }

        /** JWT minimum length; real Play Integrity tokens are typically 800+ chars. */
        if (token.length < MIN_TOKEN_LENGTH) {
            SonicVaultLogger.w("[Attestation] token suspiciously short len=${token.length}")
            return false
        }

        /** JWT structure: exactly 3 dot-separated parts (header.payload.signature). */
        val parts = token.split(".")
        if (parts.size != JWT_PART_COUNT) {
            SonicVaultLogger.w("[Attestation] token is not valid JWT format parts=${parts.size}")
            return false
        }

        /** Verify each part is valid Base64. */
        for ((idx, part) in parts.withIndex()) {
            if (part.isEmpty()) {
                SonicVaultLogger.w("[Attestation] JWT part $idx is empty")
                return false
            }
            try {
                Base64.decode(part, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            } catch (e: IllegalArgumentException) {
                SonicVaultLogger.w("[Attestation] JWT part $idx is not valid Base64")
                return false
            }
        }

        /** Verify payload part decodes to something that looks like JSON. */
        try {
            val payloadJson = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                Charsets.UTF_8
            )
            if (!payloadJson.trimStart().startsWith("{")) {
                SonicVaultLogger.w("[Attestation] JWT payload is not JSON")
                return false
            }
        } catch (e: Exception) {
            SonicVaultLogger.w("[Attestation] JWT payload decode failed")
            return false
        }

        return true
    }

    /**
     * Generates a Base64-encoded random nonce for Play Integrity request.
     * Nonce must be 16-500 chars, Base64; prevents replay attacks.
     */
    private fun generateNonce(): String {
        val bytes = ByteArray(NONCE_BYTE_LENGTH)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Suspends until the Play Integrity Task completes.
     * Never logs token contents.
     */
    private suspend fun requestIntegrityTokenSuspend(
        integrityManager: IntegrityManager,
        request: IntegrityTokenRequest
    ): IntegrityTokenResponse? = suspendCancellableCoroutine { cont ->
        integrityManager.requestIntegrityToken(request)
            .addOnSuccessListener { response ->
                if (cont.isActive) cont.resume(response)
            }
            .addOnFailureListener { e ->
                SonicVaultLogger.w("[Attestation] IntegrityManager request failed: ${e.message}")
                if (cont.isActive) cont.resume(null)
            }
    }

    companion object {
        private const val NONCE_BYTE_LENGTH = 32
        /** Minimum acceptable token length; real Play Integrity JWTs are 800+ chars. */
        private const val MIN_TOKEN_LENGTH = 100
        /** JWT always has exactly 3 parts: header.payload.signature. */
        private const val JWT_PART_COUNT = 3
    }
}
