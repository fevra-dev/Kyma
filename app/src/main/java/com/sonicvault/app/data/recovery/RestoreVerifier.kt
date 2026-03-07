package com.sonicvault.app.data.recovery

import com.sonicvault.app.data.solana.SolanaAddressDeriver
import com.sonicvault.app.data.solana.SolanaTxParser

/**
 * Post-restore verification: derives Solana address from mnemonic for user to confirm.
 *
 * MVP: show derived address before Seed Vault import so user can visually verify
 * after import. No getAccounts() call needed (would require MWA re-auth).
 */
object RestoreVerifier {

    /**
     * Derives the Solana address from mnemonic for display before Seed Vault import.
     *
     * @param mnemonic space-separated BIP39 seed phrase
     * @return base58 derived address, or null if derivation fails
     */
    fun deriveAddressForDisplay(mnemonic: String): String? {
        return SolanaAddressDeriver.deriveAddress(mnemonic)
    }

    /** Truncates address for display (first 6 + … + last 4). */
    fun truncateAddress(address: String): String {
        return SolanaTxParser.truncateAddress(address)
    }
}
