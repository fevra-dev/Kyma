package com.sonicvault.app.data.nonce

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single durable nonce account in the pool.
 *
 * Pool invariants:
 *  - AVAILABLE   → ready to be checked out for a new signing ceremony
 *  - IN_FLIGHT   → checked out; a signing ceremony is in progress
 *  - CONSUMED    → nonce was advanced on-chain; needs refresh before reuse
 *
 * InstructionError note:
 *   When a durable nonce transaction fails with InstructionError, the nonce IS
 *   advanced on-chain even though the transaction failed. ALWAYS call markConsumed()
 *   on ANY outcome (success, InstructionError, cancellation). Never leave IN_FLIGHT
 *   without resolution — the app-startup reconciler (NoncePoolManager.reconcileOnStartup)
 *   handles crash recovery by comparing on-chain state to local DB state.
 */
@Entity(tableName = "nonce_accounts")
data class NonceAccountEntity(
    @PrimaryKey val publicKey: String,  // base58 nonce account pubkey

    /** Current nonce VALUE (base58) — used as recentBlockhash in durable nonce TXs. */
    val currentNonce: String,

    val status: NonceStatus,

    /** TX signature if IN_FLIGHT — allows app-restart recovery to identify the TX. */
    val txHashUsedFor: String?,

    /** Wall clock epoch millis of last successful on-chain sync. */
    val lastSyncedAt: Long
)

enum class NonceStatus { AVAILABLE, IN_FLIGHT, CONSUMED }
