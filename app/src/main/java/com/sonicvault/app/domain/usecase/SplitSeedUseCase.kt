package com.sonicvault.app.domain.usecase

import android.net.Uri
import androidx.fragment.app.FragmentActivity
import com.sonicvault.app.data.repository.BackupRepository
import com.sonicvault.app.data.repository.CreateBackupResult
import com.sonicvault.app.data.shamir.Slip39Shamir
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.Bip39Validator
import com.sonicvault.app.util.SeedValidation

/**
 * Splits a BIP39 seed into N Shamir shares (M required to recover).
 * Each share is encrypted and embedded in a cover WAV.
 *
 * Flow: validate seed → split via SLIP-0039 → for each share: encrypt → embed → write WAV
 */
class SplitSeedUseCase(
    private val shamir: Slip39Shamir,
    private val repository: BackupRepository,
    private val bip39Validator: Bip39Validator
) {
    /**
     * Splits seed into shares and creates one stego file per share.
     * Uses the same cover for all shares (embedded in copy each time).
     *
     * @return List of (stegoUri, shareIndex) on success
     */
    suspend operator fun invoke(
        seedPhrase: String,
        threshold: Int,
        totalShares: Int,
        coverAudioUri: Uri,
        activity: FragmentActivity
    ): Result<List<CreateBackupResult>> {
        return when (val validation = bip39Validator.validate(seedPhrase)) {
            is SeedValidation.Invalid -> {
                SonicVaultLogger.w("[SplitSeed] validation failed: ${validation.message}")
                Result.failure(Exception(validation.message))
            }
            is SeedValidation.Valid -> {
                val splitResult = shamir.split(validation.phrase, threshold, totalShares)
                splitResult.fold(
                    onSuccess = { shares ->
                        SonicVaultLogger.i("[SplitSeed] creating ${shares.size} share backups")
                        val results = mutableListOf<CreateBackupResult>()
                        for ((i, share) in shares.withIndex()) {
                            val backupResult = repository.createShareBackup(share, coverAudioUri, activity)
                            backupResult.fold(
                                onSuccess = { results.add(it) },
                                onFailure = { e ->
                                    SonicVaultLogger.e("[SplitSeed] share $i backup failed", e)
                                    return Result.failure(e)
                                }
                            )
                        }
                        Result.success(results)
                    },
                    onFailure = { Result.failure(it) }
                )
            }
        }
    }
}
