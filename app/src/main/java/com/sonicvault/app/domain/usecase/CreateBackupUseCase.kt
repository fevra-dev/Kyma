package com.sonicvault.app.domain.usecase

import android.net.Uri
import androidx.fragment.app.FragmentActivity
import com.sonicvault.app.data.repository.BackupRepository
import com.sonicvault.app.data.repository.CreateBackupResult
import com.sonicvault.app.util.SeedValidation
import com.sonicvault.app.util.Bip39Validator
import com.sonicvault.app.util.PrivateKeyValidation
import com.sonicvault.app.util.SolanaPrivateKeyValidator

/**
 * Validates seed phrase (BIP39) or Solana private key and delegates to [BackupRepository.createBackup].
 */
class CreateBackupUseCase(
    private val repository: BackupRepository,
    private val bip39Validator: Bip39Validator
) {
    suspend operator fun invoke(
        seedPhrase: String,
        coverAudioUri: Uri,
        activity: FragmentActivity,
        duressPassword: String? = null,
        usePasswordMode: Boolean = false,
        password: String? = null,
        unlockTimestamp: Long? = null,
        useHybridMode: Boolean = false,
        useHardwareBound: Boolean = false
    ): Result<CreateBackupResult> {
        /** Enforce minimum 8-character password for password mode (OWASP recommendation). */
        if (usePasswordMode && (password == null || password.length < MIN_PASSWORD_LENGTH)) {
            return Result.failure(Exception("Password must be at least $MIN_PASSWORD_LENGTH characters."))
        }
        /** Enforce minimum 6-character duress password (separate from main password). */
        if (duressPassword != null && duressPassword.length < MIN_DURESS_PASSWORD_LENGTH) {
            return Result.failure(Exception("Duress password must be at least $MIN_DURESS_PASSWORD_LENGTH characters."))
        }
        val bip39Result = bip39Validator.validate(seedPhrase)
        if (bip39Result is SeedValidation.Valid) {
            return repository.createBackup(bip39Result.phrase, coverAudioUri, activity, duressPassword, usePasswordMode, password, unlockTimestamp, useHybridMode, useHardwareBound)
        }
        val pkResult = SolanaPrivateKeyValidator.validate(seedPhrase)
        return when (pkResult) {
            is PrivateKeyValidation.Valid -> repository.createBackup(pkResult.key, coverAudioUri, activity, duressPassword, usePasswordMode, password, unlockTimestamp, useHybridMode, useHardwareBound)
            is PrivateKeyValidation.Invalid -> Result.failure(Exception("Enter a valid seed phrase (12 or 24 words) or Solana private key (87–88 base58 chars)."))
        }
    }

    companion object {
        /** Minimum password length for password-mode encryption (cross-device recovery). */
        const val MIN_PASSWORD_LENGTH = 8
        /** Minimum duress password length (matches main password policy). */
        const val MIN_DURESS_PASSWORD_LENGTH = 8
    }
}
