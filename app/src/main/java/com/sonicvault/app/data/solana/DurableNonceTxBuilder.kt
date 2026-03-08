package com.sonicvault.app.data.solana

import com.solana.core.PublicKey
import com.solana.core.SignaturePubkeyPair
import com.solana.core.Transaction
import com.solana.programs.SystemProgram
import com.sonicvault.app.logging.SonicVaultLogger

/**
 * Builds transactions for SonicSafe cold signing.
 *
 * When nonce account params are provided, delegates to [SolanaTransactionBuilder.buildDurableNonceTx]
 * which prepends a NonceAdvance instruction and uses the nonce value as recentBlockhash.
 * Otherwise builds a plain SOL transfer with a recent blockhash.
 */
object DurableNonceTxBuilder {

    /**
     * Builds unsigned SOL transfer for acoustic cold signing.
     *
     * @param fromPubkey fee payer (base58)
     * @param toPubkey recipient (base58)
     * @param lamports amount
     * @param blockhash nonce value (if durable nonce) or recent blockhash
     * @param nonceAccountPubkey base58 nonce account; when set, builds a durable nonce TX
     * @param nonceAuthorityPubkey base58 nonce authority (signer of NonceAdvance)
     */
    fun buildTransfer(
        fromPubkey: String,
        toPubkey: String,
        lamports: Long,
        blockhash: String,
        nonceAccountPubkey: String? = null,
        nonceAuthorityPubkey: String? = null
    ): Transaction? {
        if (nonceAccountPubkey != null && nonceAuthorityPubkey != null) {
            SonicVaultLogger.d("[DurableNonceTxBuilder] building durable nonce transfer $lamports lamports")
            return SolanaTransactionBuilder.buildDurableNonceTx(
                nonceAccountPubkey, nonceAuthorityPubkey,
                fromPubkey, toPubkey, lamports, blockhash
            )
        }
        return try {
            val from = PublicKey(fromPubkey)
            val to = PublicKey(toPubkey)
            val tx = Transaction()
            tx.add(SystemProgram.transfer(from, to, lamports))
            tx.setRecentBlockHash(blockhash)
            tx.feePayer = from
            tx.signatures.add(SignaturePubkeyPair(null, from))
            SonicVaultLogger.d("[DurableNonceTxBuilder] built transfer $lamports lamports (recent blockhash)")
            tx
        } catch (e: Exception) {
            SonicVaultLogger.e("[DurableNonceTxBuilder] build failed", e)
            null
        }
    }
}
