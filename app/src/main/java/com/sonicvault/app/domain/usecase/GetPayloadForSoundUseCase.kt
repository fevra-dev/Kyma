package com.sonicvault.app.domain.usecase

import androidx.fragment.app.FragmentActivity
import com.sonicvault.app.data.repository.BackupRepository
import com.sonicvault.app.util.Bip39Validator
import com.sonicvault.app.util.SeedValidation

/**
 * Returns formatted payload bytes for data-over-sound transmit (encrypt + device binding).
 * Caller encodes bytes to sound and plays via speaker.
 */
class GetPayloadForSoundUseCase(
    private val repository: BackupRepository,
    private val bip39Validator: Bip39Validator
) {
    suspend operator fun invoke(seedPhrase: String, activity: FragmentActivity): Result<ByteArray> {
        return when (val validation = bip39Validator.validate(seedPhrase)) {
            is SeedValidation.Valid -> repository.getPayloadForSoundTransmit(validation.phrase, activity)
            is SeedValidation.Invalid -> Result.failure(Exception(validation.message))
        }
    }
}
