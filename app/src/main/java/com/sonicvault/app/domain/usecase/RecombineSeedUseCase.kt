package com.sonicvault.app.domain.usecase

import android.net.Uri
import androidx.fragment.app.FragmentActivity
import com.sonicvault.app.data.repository.BackupRepository
import com.sonicvault.app.data.shamir.Slip39Shamir
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.Bip39Validator
import com.sonicvault.app.util.SeedValidation

/**
 * Recombines Shamir shares from stego files to recover the BIP39 seed.
 *
 * Flow: extract payload from each file → decrypt → share string → combine → validate seed
 */
class RecombineSeedUseCase(
    private val shamir: Slip39Shamir,
    private val repository: BackupRepository,
    private val bip39Validator: Bip39Validator
) {
    /**
     * Extracts shares from stego files and combines them to recover the seed.
     *
     * @param stegoUris List of share stego file URIs (≥ threshold required)
     * @return Reconstructed BIP39 seed phrase
     */
    suspend operator fun invoke(
        stegoUris: List<Uri>,
        activity: FragmentActivity
    ): Result<String> {
        if (stegoUris.isEmpty()) {
            return Result.failure(Exception("Select at least one share file."))
        }
        return try {
            SonicVaultLogger.i("[Recombine] extracting ${stegoUris.size} shares")
            val shares = mutableListOf<String>()
            for ((i, uri) in stegoUris.withIndex()) {
                val extractResult = repository.extractPayload(uri)
                val extract = extractResult.getOrElse { e ->
                    SonicVaultLogger.e("[Recombine] extract failed for file $i", e)
                    return Result.failure(e)
                }
                val decrypted = repository.recoverSeedWithBiometric(extract, activity).getOrElse { e ->
                    SonicVaultLogger.e("[Recombine] decrypt failed for file $i", e)
                    return Result.failure(e)
                }
                shares.add(decrypted.seed)
            }
            val combineResult = shamir.combine(shares)
            combineResult.fold(
                onSuccess = { phrase ->
                    when (val validation = bip39Validator.validate(phrase)) {
                        is SeedValidation.Valid -> Result.success(validation.phrase)
                        is SeedValidation.Invalid -> Result.failure(Exception(validation.message))
                    }
                },
                onFailure = { Result.failure(it) }
            )
        } catch (e: Exception) {
            SonicVaultLogger.e("[Recombine] unexpected error", e)
            Result.failure(Exception(e.message ?: "Recombine failed.", e))
        }
    }
}
