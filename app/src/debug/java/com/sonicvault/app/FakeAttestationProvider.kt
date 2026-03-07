package com.sonicvault.app

import android.content.Context
import com.sonicvault.app.data.attestation.AttestationResult
import com.sonicvault.app.data.attestation.DeviceAttestationProvider
import kotlinx.coroutines.delay

/**
 * Test-only attestation: always passes.
 * Allows instrumented tests to run recovery flow without Play Integrity (emulator).
 */
class FakeAttestationProvider : DeviceAttestationProvider {
    override suspend fun attestDevice(context: Context): Result<AttestationResult> {
        delay(10) // Simulate network delay
        return Result.success(AttestationResult.Pass)
    }
}
