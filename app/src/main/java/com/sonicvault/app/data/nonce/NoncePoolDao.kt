package com.sonicvault.app.data.nonce

import androidx.room.*

/**
 * Room DAO for the nonce account pool.
 *
 * Critical: checkoutNonce() is annotated @Transaction to prevent double-spend.
 * Two coroutines racing on checkoutNonce() will serialize — only one gets the nonce.
 */
@Dao
interface NoncePoolDao {

    @Query("SELECT * FROM nonce_accounts WHERE status = 'AVAILABLE' LIMIT 1")
    suspend fun getFirstAvailable(): NonceAccountEntity?

    @Query("SELECT * FROM nonce_accounts WHERE status = 'IN_FLIGHT'")
    suspend fun getAllInFlight(): List<NonceAccountEntity>

    @Query("SELECT * FROM nonce_accounts")
    suspend fun getAll(): List<NonceAccountEntity>

    @Query("SELECT * FROM nonce_accounts WHERE publicKey = :pubkey")
    suspend fun getByPublicKey(pubkey: String): NonceAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: NonceAccountEntity)

    @Update
    suspend fun update(account: NonceAccountEntity)

    @Delete
    suspend fun delete(account: NonceAccountEntity)

    @Query("DELETE FROM nonce_accounts")
    suspend fun deleteAll()

    /**
     * Atomically checks out one AVAILABLE nonce, marking it IN_FLIGHT.
     *
     * Returns null if no nonces are available (pool empty or all consumed/in-flight).
     * The @Transaction guarantees no two callers can check out the same nonce.
     */
    @Transaction
    suspend fun checkoutNonce(): NonceAccountEntity? {
        val available = getFirstAvailable() ?: return null
        val checkedOut = available.copy(
            status = NonceStatus.IN_FLIGHT,
            lastSyncedAt = System.currentTimeMillis()
        )
        update(checkedOut)
        return checkedOut
    }
}
