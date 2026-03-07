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
     * Fetches current nonce or recent blockhash.
     *
     * When nonce account is configured, getAccountInfo would return nonce data.
     * For MVP: always use getLatestBlockhash. Full nonce support requires RPC getAccountInfo.
     *
     * @return blockhash/nonce string for transaction, or null on failure
     */
    suspend fun fetchCurrentNonce(): String? = withContext(Dispatchers.IO) {
        rpcClient.getLatestBlockhash()?.blockhash
    }

    companion object {
        const val NONCE_ACCOUNT_LENGTH = 80
    }
}
