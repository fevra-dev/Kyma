package com.sonicvault.app.data.crypto

import com.sonicvault.app.data.solana.SolanaAddressDeriver
import com.sonicvault.app.logging.SonicVaultLogger

/**
 * Verifies that an entered mnemonic matches the Seed Vault accounts.
 *
 * Derives pubkey from mnemonic via SolanaKT; compares to Seed Vault getAccounts().
 * Used to confirm backup restore matches the hardware-bound key.
 *
 * Note: getAccounts() is via MWA authorize/transact; caller must provide
 * the Seed Vault account public keys (base58) to compare.
 */
object SeedVaultVerifier {

    /**
     * Verifies that the derived address from mnemonic matches one of the Seed Vault accounts.
     *
     * @param mnemonic BIP39 seed phrase (12 or 24 words)
     * @param seedVaultAccounts list of base58 public keys from Seed Vault getAccounts()
     * @return true if mnemonic derives to an address in seedVaultAccounts
     */
    fun verifyBackupMatchesSeedVault(mnemonic: String, seedVaultAccounts: List<String>): Boolean {
        return try {
            val derived = SolanaAddressDeriver.deriveAddress(mnemonic) ?: return false
            val matches = seedVaultAccounts.any { it == derived }
            SonicVaultLogger.d("[SeedVaultVerifier] derived=${derived.take(8)}… matches=$matches")
            matches
        } catch (e: Exception) {
            SonicVaultLogger.e("[SeedVaultVerifier] verify failed", e)
            false
        }
    }
}
