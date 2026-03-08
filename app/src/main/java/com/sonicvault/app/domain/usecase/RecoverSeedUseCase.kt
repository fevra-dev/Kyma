package com.sonicvault.app.domain.usecase

import android.net.Uri
import androidx.fragment.app.FragmentActivity
import com.sonicvault.app.data.repository.BackupRepository
import com.sonicvault.app.domain.model.ExtractedPayload
import com.sonicvault.app.domain.model.RecoveryResult
import com.sonicvault.app.util.Bip39Validator
import com.sonicvault.app.util.PrivateKeyValidation
import com.sonicvault.app.util.SeedValidation
import com.sonicvault.app.util.SolanaPrivateKeyValidator

/**
 * Delegates to [BackupRepository] for recovery. Validates recovered phrase is BIP39.
 * Implements exponential backoff rate limiting on failed password-based recovery attempts.
 */
class RecoverSeedUseCase(
    private val repository: BackupRepository,
    private val bip39Validator: Bip39Validator
) {
    /** Consecutive failed password recovery attempts (reset on success). */
    private var failedAttempts = 0
    /** Timestamp (ms) until which further password recovery attempts are blocked. */
    private var lockoutUntilMs = 0L

    suspend fun extractPayload(stegoAudioUri: Uri): Result<ExtractedPayload> =
        repository.extractPayload(stegoAudioUri)

    suspend fun recoverWithBiometric(extracted: ExtractedPayload, activity: FragmentActivity): Result<RecoveryResult> {
        return repository.recoverSeedWithBiometric(extracted, activity).fold(
            onSuccess = { result -> validateAndReturn(result.seed).map { (seed, isPk) -> RecoveryResult(seed, result.checksumVerified, isPk) } },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun recoverWithPassword(extracted: ExtractedPayload, password: String): Result<RecoveryResult> {
        val now = System.currentTimeMillis()
        if (now < lockoutUntilMs) {
            val waitSec = ((lockoutUntilMs - now) / 1000) + 1
            return Result.failure(Exception("Too many attempts. Try again in ${waitSec}s."))
        }
        return repository.recoverSeedWithPassword(extracted, password).fold(
            onSuccess = { result ->
                failedAttempts = 0
                lockoutUntilMs = 0L
                validateAndReturn(result.seed).map { (seed, isPk) -> RecoveryResult(seed, result.checksumVerified, isPk) }
            },
            onFailure = {
                failedAttempts++
                lockoutUntilMs = System.currentTimeMillis() + (1000L * (1L shl failedAttempts.coerceAtMost(MAX_BACKOFF_EXPONENT)))
                Result.failure(it)
            }
        )
    }

    suspend operator fun invoke(stegoAudioUri: Uri, activity: FragmentActivity): Result<String> {
        val result = repository.recoverSeed(stegoAudioUri, activity)
        return result.fold(
            onSuccess = { validateAndReturn(it).map { (seed, _) -> seed } },
            onFailure = { Result.failure(it) }
        )
    }

    companion object {
        /** Max exponent for exponential backoff: 2^5 = 32 seconds max lockout. */
        private const val MAX_BACKOFF_EXPONENT = 5
    }

    /** Returns (validated seed/key, isPrivateKey). Accepts BIP39 or Solana private key. */
    private fun validateAndReturn(phrase: String): Result<Pair<String, Boolean>> {
        when (val v = bip39Validator.validate(phrase)) {
            is SeedValidation.Valid -> return Result.success(v.phrase to false)
            is SeedValidation.Invalid -> { /* try private key */ }
        }
        return when (val v = SolanaPrivateKeyValidator.validate(phrase)) {
            is PrivateKeyValidation.Valid -> Result.success(v.key to true)
            is PrivateKeyValidation.Invalid -> Result.failure(Exception("Recovered data is not a valid seed phrase or private key."))
        }
    }
}
