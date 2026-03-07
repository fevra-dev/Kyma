package com.sonicvault.app.domain.usecase

import android.net.Uri
import androidx.fragment.app.FragmentActivity
import com.sonicvault.app.data.repository.BackupRepository
import com.sonicvault.app.domain.model.ExtractedPayload
import com.sonicvault.app.domain.model.RecoveryResult
import com.sonicvault.app.util.Bip39Validator
import com.sonicvault.app.util.SeedValidation

/**
 * Delegates to [BackupRepository] for recovery. Validates recovered phrase is BIP39.
 */
class RecoverSeedUseCase(
    private val repository: BackupRepository,
    private val bip39Validator: Bip39Validator
) {
    suspend fun extractPayload(stegoAudioUri: Uri): Result<ExtractedPayload> =
        repository.extractPayload(stegoAudioUri)

    suspend fun recoverWithBiometric(extracted: ExtractedPayload, activity: FragmentActivity): Result<RecoveryResult> {
        return repository.recoverSeedWithBiometric(extracted, activity).fold(
            onSuccess = { result -> validateAndReturn(result.seed).map { RecoveryResult(it, result.checksumVerified) } },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun recoverWithPassword(extracted: ExtractedPayload, password: String): Result<RecoveryResult> {
        return repository.recoverSeedWithPassword(extracted, password).fold(
            onSuccess = { result -> validateAndReturn(result.seed).map { RecoveryResult(it, result.checksumVerified) } },
            onFailure = { Result.failure(it) }
        )
    }

    suspend operator fun invoke(stegoAudioUri: Uri, activity: FragmentActivity): Result<String> {
        val result = repository.recoverSeed(stegoAudioUri, activity)
        return result.fold(
            onSuccess = { validateAndReturn(it) },
            onFailure = { Result.failure(it) }
        )
    }

    private fun validateAndReturn(phrase: String): Result<String> =
        when (val validation = bip39Validator.validate(phrase)) {
            is SeedValidation.Valid -> Result.success(validation.phrase)
            is SeedValidation.Invalid -> Result.failure(Exception(validation.message))
        }
}
