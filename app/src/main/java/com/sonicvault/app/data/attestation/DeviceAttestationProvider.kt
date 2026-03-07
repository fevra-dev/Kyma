package com.sonicvault.app.data.attestation

import android.content.Context

/**
 * Result of device attestation (Play Integrity API).
 * - Pass: Device meets policy (not rooted, not emulator, not tampered).
 * - Fail: Refuse decrypt; show "Device may be compromised".
 */
sealed class AttestationResult {
    data object Pass : AttestationResult()
    data object Fail : AttestationResult()
}

/**
 * Optional device attestation before decrypt.
 * Verifies device integrity (rooted, tampered, emulator) via Play Integrity API.
 * Per roadmap Part 2.5 Device Binding + TEE Attestation.
 */
interface DeviceAttestationProvider {
    /**
     * Attests device integrity. Blocks decrypt on high-risk devices.
     * @param context Application context
     * @return Result.success(Pass) if device meets policy, Result.success(Fail) or Result.failure if not
     */
    suspend fun attestDevice(context: Context): Result<AttestationResult>
}
