package com.sonicvault.app.data.solana

import com.solana.core.DerivationPath
import com.solana.core.HotAccount

/**
 * Derives Solana address from BIP39 mnemonic using standard path m/44'/501'/0'/0'.
 * Used for vanity address search (prefix/suffix matching).
 *
 * SECURITY: HotAccount.fromMnemonic creates a full Ed25519 keypair in JVM heap.
 * SolanaKT does not expose raw secret bytes for wiping. The keypair reference is
 * discarded after extraction of the public key, but the secret material persists
 * until GC. Only use this in controlled contexts (setup, not hot path).
 */
object SolanaAddressDeriver {

    /**
     * Derives the Solana public address (base58) from a BIP39 mnemonic.
     *
     * @param mnemonic space-separated BIP39 seed phrase (12 or 24 words)
     * @return base58 Solana address, or null if derivation fails
     */
    fun deriveAddress(mnemonic: String): String? {
        return try {
            val words = mnemonic.trim().split("\\s+".toRegex())
            val account = HotAccount.fromMnemonic(words, "", DerivationPath.BIP44_M_44H_501H_0H)
            account.publicKey.toString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if the Solana address matches the given prefix (case-sensitive).
     */
    fun matchesPrefix(address: String, prefix: String): Boolean {
        if (prefix.isEmpty()) return true
        return address.startsWith(prefix)
    }
}
