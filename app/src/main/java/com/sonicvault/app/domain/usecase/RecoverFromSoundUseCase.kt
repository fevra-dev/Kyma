package com.sonicvault.app.domain.usecase

import androidx.fragment.app.FragmentActivity
import com.sonicvault.app.data.repository.BackupRepository
import com.sonicvault.app.util.Bip39Validator
import com.sonicvault.app.util.SeedValidation

/**
 * Recovers seed from payload bytes received via data-over-sound (record → decode → bytes).
 */
class RecoverFromSoundUseCase(
    private val repository: BackupRepository,
    private val bip39Validator: Bip39Validator
) {
    suspend operator fun invoke(payloadBytes: ByteArray, activity: FragmentActivity): Result<String> {
        val result = repository.recoverFromPayloadBytes(payloadBytes, activity)
        return result.fold(
            onSuccess = { phrase ->
                when (val validation = bip39Validator.validate(phrase)) {
                    is SeedValidation.Valid -> Result.success(validation.phrase)
                    is SeedValidation.Invalid -> Result.failure(Exception(validation.message))
                }
            },
            onFailure = { Result.failure(it) }
        )
    }
}
