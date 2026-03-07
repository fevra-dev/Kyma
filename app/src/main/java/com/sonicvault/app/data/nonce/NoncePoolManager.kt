package com.sonicvault.app.data.nonce

import com.sonicvault.app.data.solana.SolanaRpcClient
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Orchestrates the 3-nonce pool lifecycle.
 *
 * Responsibilities:
 *  1. App-startup crash recovery (reconcileOnStartup)
 *  2. Atomic nonce checkout for signing ceremonies
 *  3. Marking nonces consumed + async refresh (markConsumed)
 *  4. One-time setup of nonce accounts on-chain (setupPool) — requires payer keypair
 *  5. Discovery of existing nonce accounts (discoverExistingNonces)
 *
 * InstructionError rule (enforced by callers):
 *   markConsumed() MUST be called on ANY transaction outcome — success, failure, cancellation.
 *   InstructionError still advances the nonce on-chain. Leaving a nonce IN_FLIGHT
 *   after a crash will be caught by reconcileOnStartup() on next app launch.
 */
class NoncePoolManager(
    private val dao: NoncePoolDao,
    private val rpc: SolanaRpcClient,
    private val backgroundScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    companion object {
        private const val TAG = "NoncePool"
        const val DEFAULT_POOL_SIZE = 3
        private const val NONCE_ACCOUNT_SIZE = 80
        private const val COST_PER_NONCE_SOL = 0.00136
    }

    // ─────────────────────────────────────────────────────────────
    // Startup recovery
    // ─────────────────────────────────────────────────────────────

    /**
     * Call from Application.onCreate() or the first screen's init block.
     *
     * For each IN_FLIGHT nonce, fetches the current on-chain nonce value.
     * If it differs from the DB value, the nonce was advanced (TX confirmed or InstructionError).
     * Marks those nonces CONSUMED and schedules an async refresh.
     */
    suspend fun reconcileOnStartup() {
        val inFlight = dao.getAllInFlight()
        if (inFlight.isEmpty()) {
            SonicVaultLogger.d("[NoncePool] reconcileOnStartup: no IN_FLIGHT nonces")
            return
        }
        SonicVaultLogger.i("[NoncePool] reconcileOnStartup: ${inFlight.size} IN_FLIGHT — checking chain")
        inFlight.forEach { nonce ->
            try {
                val onChainNonce = rpc.getNonce(nonce.publicKey)
                if (onChainNonce != null && onChainNonce != nonce.currentNonce) {
                    SonicVaultLogger.i("[NoncePool] Nonce ${nonce.publicKey.take(8)}… advanced — consuming")
                    dao.update(nonce.copy(status = NonceStatus.CONSUMED, currentNonce = onChainNonce))
                    refreshNonceAsync(nonce.copy(currentNonce = onChainNonce))
                } else {
                    SonicVaultLogger.i("[NoncePool] Nonce ${nonce.publicKey.take(8)}… still same — TX may be pending")
                }
            } catch (e: Exception) {
                SonicVaultLogger.w("[NoncePool] reconcileOnStartup failed for ${nonce.publicKey.take(8)}: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Checkout & consume
    // ─────────────────────────────────────────────────────────────

    /**
     * Atomically checks out an AVAILABLE nonce.
     * Returns null if pool is empty or all nonces are IN_FLIGHT / CONSUMED.
     *
     * Callers should show "Setup nonce pool" if this returns null.
     */
    suspend fun checkoutNonce(): NonceAccountEntity? {
        return dao.checkoutNonce().also {
            if (it != null) SonicVaultLogger.i("[NoncePool] Checked out: ${it.publicKey.take(8)}…")
            else SonicVaultLogger.w("[NoncePool] checkoutNonce: no AVAILABLE nonces")
        }
    }

    /**
     * Marks [nonce] as CONSUMED and asynchronously refreshes it from the chain.
     *
     * Call after ANY transaction outcome — success, failure, InstructionError, or user cancel.
     */
    suspend fun markConsumed(nonce: NonceAccountEntity, txSig: String? = null) {
        SonicVaultLogger.i("[NoncePool] Nonce consumed${if (txSig != null) " (TX: ${txSig.take(8)}…)" else ""}")
        dao.update(nonce.copy(status = NonceStatus.CONSUMED, txHashUsedFor = txSig))
        refreshNonceAsync(nonce)
    }

    // ─────────────────────────────────────────────────────────────
    // Pool setup (one-time, from Settings screen)
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates [count] nonce accounts on-chain and stores them in Room.
     *
     * Requires internet + ~[count * COST_PER_NONCE_SOL] SOL in the payer account.
     * Returns base58 pubkeys of created nonce accounts.
     *
     * Note: Requires TransactionBuilder.createNonceAccount — not yet implemented.
     * Use discoverExistingNonces + insert to populate pool from existing accounts.
     */
    suspend fun setupPool(payerPublicKey: String, count: Int = DEFAULT_POOL_SIZE): List<String> {
        SonicVaultLogger.w("[NoncePool] setupPool not implemented — use discoverExistingNonces to populate")
        return emptyList()
    }

    /**
     * Discovers existing nonce accounts owned by [authorityPubkey] via getProgramAccounts.
     *
     * Useful after data loss (reinstall) to recover existing nonce accounts.
     * Filters: dataSize=80 + memcmp at offset 8 = authorityPubkey bytes.
     *
     * Caller should insert discovered accounts into the pool via dao.insert().
     */
    suspend fun discoverExistingNonces(authorityPubkey: String): List<String> {
        val trimmed = authorityPubkey.trim()
        SonicVaultLogger.i("[NoncePool] Discovering nonces for authority ${trimmed.take(8)}…")
        val filters = """[{"dataSize":$NONCE_ACCOUNT_SIZE},{"memcmp":{"offset":8,"bytes":"$trimmed"}}]"""
        return try {
            rpc.getProgramAccounts("11111111111111111111111111111111", filters).also {
                SonicVaultLogger.i("[NoncePool] Discovered ${it.size} nonce account(s)")
            }
        } catch (e: Exception) {
            SonicVaultLogger.e("[NoncePool] discoverExistingNonces failed", e)
            emptyList()
        }
    }

    /**
     * Inserts a newly created nonce account into the pool after on-chain creation.
     * Fetches current nonce value from chain and inserts as AVAILABLE.
     *
     * @return true if inserted successfully
     */
    suspend fun insertNonceAfterCreate(noncePubkey: String): Boolean {
        return try {
            val currentNonce = rpc.getNonce(noncePubkey) ?: return false
            dao.insert(
                NonceAccountEntity(
                    publicKey = noncePubkey,
                    currentNonce = currentNonce,
                    status = NonceStatus.AVAILABLE,
                    txHashUsedFor = null,
                    lastSyncedAt = System.currentTimeMillis()
                )
            )
            SonicVaultLogger.i("[NoncePool] Inserted new nonce: ${noncePubkey.take(8)}…")
            true
        } catch (e: Exception) {
            SonicVaultLogger.e("[NoncePool] insertNonceAfterCreate failed", e)
            false
        }
    }

    /**
     * Inserts discovered nonce accounts into the pool after fetching current nonce value.
     */
    suspend fun importDiscoveredNonces(authorityPubkey: String): List<String> {
        val pubkeys = discoverExistingNonces(authorityPubkey.trim())
        val imported = mutableListOf<String>()
        pubkeys.forEach { pubkey ->
            try {
                val currentNonce = rpc.getNonce(pubkey) ?: return@forEach
                dao.insert(
                    NonceAccountEntity(
                        publicKey = pubkey,
                        currentNonce = currentNonce,
                        status = NonceStatus.AVAILABLE,
                        txHashUsedFor = null,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
                imported.add(pubkey)
            } catch (e: Exception) {
                SonicVaultLogger.w("[NoncePool] Failed to import $pubkey: ${e.message}")
            }
        }
        return imported
    }

    // ─────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────

    private fun refreshNonceAsync(nonce: NonceAccountEntity) {
        backgroundScope.launch {
            try {
                val newNonce = rpc.getNonce(nonce.publicKey) ?: return@launch
                dao.update(nonce.copy(
                    status = NonceStatus.AVAILABLE,
                    currentNonce = newNonce,
                    txHashUsedFor = null,
                    lastSyncedAt = System.currentTimeMillis()
                ))
                SonicVaultLogger.i("[NoncePool] Nonce ${nonce.publicKey.take(8)}… refreshed")
            } catch (e: Exception) {
                SonicVaultLogger.e("[NoncePool] refreshNonceAsync failed for ${nonce.publicKey.take(8)}", e)
            }
        }
    }

    /** Returns total cost estimate string for UI display. */
    fun costEstimate(count: Int = DEFAULT_POOL_SIZE): String =
        "~${"%.5f".format(count * COST_PER_NONCE_SOL)} SOL ($count accounts × ~$COST_PER_NONCE_SOL SOL each)"
}
