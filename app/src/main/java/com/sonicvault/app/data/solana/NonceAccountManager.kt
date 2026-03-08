package com.sonicvault.app.data.solana

import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages durable nonce for SonicSafe cold signing.
 *
 * Blockhash expires ~60s; acoustic round-trip ~15–30s. Durable nonce allows
 * TX to remain valid. Create nonce account one-time, fund with ~0.0015 SOL.
 *
 * Fallback: when no nonce configured, use getLatestBlockhash from RPC.
 */
class NonceAccountManager(
    private val rpcClient: SolanaRpcClient = SolanaRpcClient()
) {

    /** Configured nonce account pubkey (base58). Null = use recent blockhash. */
    var nonceAccountPubkey: String? = null

    /**
     * Fetches current nonce value from on-chain nonce account, or falls back to recent blockhash.
     *
     * When [nonceAccountPubkey] is set, queries the nonce account via getAccountInfo and
     * extracts the stored nonce value (bytes 40-72). This gives transactions unlimited
     * validity — critical for acoustic cold signing where the round-trip is 15-30s.
     *
     * @return nonce value or blockhash string for transaction, or null on failure
     */
    suspend fun fetchCurrentNonce(): String? = withContext(Dispatchers.IO) {
        val pubkey = nonceAccountPubkey
        if (pubkey != null) {
            val nonce = rpcClient.getNonce(pubkey)
            if (nonce != null) {
                SonicVaultLogger.i("[NonceAccountMgr] fetched durable nonce from ${pubkey.take(8)}...")
                return@withContext nonce
            }
            SonicVaultLogger.w("[NonceAccountMgr] nonce account query failed, falling back to blockhash")
        }
        rpcClient.getLatestBlockhash()?.blockhash
    }

    /** @return true if a durable nonce account is configured and will be used for TX construction. */
    val isDurableNonceConfigured: Boolean get() = nonceAccountPubkey != null

    companion object {
        const val NONCE_ACCOUNT_LENGTH = 80
    }
}
