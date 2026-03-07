package com.sonicvault.app.data.solana

import com.solana.core.PublicKey
import com.solana.core.SignaturePubkeyPair
import com.solana.core.Transaction
import com.solana.programs.SystemProgram
import com.sonicvault.app.logging.SonicVaultLogger

/**
 * Builds transactions for SonicSafe cold signing.
 *
 * Uses blockhash from NonceAccountManager (or getLatestBlockhash fallback).
 * For full durable nonce: would prepend NonceAdvance instruction.
 * MVP: simple SOL transfer with recent blockhash.
 */
object DurableNonceTxBuilder {

    /**
     * Builds unsigned SOL transfer for acoustic cold signing.
     *
     * @param fromPubkey fee payer (base58)
     * @param toPubkey recipient (base58)
     * @param lamports amount
     * @param blockhash from NonceAccountManager or RPC
     */
    fun buildTransfer(
        fromPubkey: String,
        toPubkey: String,
        lamports: Long,
        blockhash: String
    ): Transaction? {
        return try {
            val from = PublicKey(fromPubkey)
            val to = PublicKey(toPubkey)
            val tx = Transaction()
            tx.add(SystemProgram.transfer(from, to, lamports))
            tx.setRecentBlockHash(blockhash)
            tx.feePayer = from
            tx.signatures.add(SignaturePubkeyPair(null, from))
            SonicVaultLogger.d("[DurableNonceTxBuilder] built transfer ${lamports} lamports")
            tx
        } catch (e: Exception) {
            SonicVaultLogger.e("[DurableNonceTxBuilder] build failed", e)
            null
        }
    }
}
