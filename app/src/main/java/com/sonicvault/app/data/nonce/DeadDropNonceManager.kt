package com.sonicvault.app.data.nonce

import android.content.SharedPreferences
import com.sonicvault.app.logging.SonicVaultLogger

/**
 * Monotonic nonce counter for Dead Drop replay protection.
 *
 * Each ECDH transmission embeds an 8-byte monotonic counter. The sender increments
 * before each broadcast. The receiver stores the highest nonce seen per device_binding_hash.
 * If received nonce <= stored last-seen -> REPLAY -> discard.
 *
 * Storage MUST be EncryptedSharedPreferences (caller creates via MasterKey + AES256_GCM).
 */
class DeadDropNonceManager(private val securePrefs: SharedPreferences) {

    companion object {
        private const val SEND_NONCE_KEY = "dead_drop_send_nonce"
        private const val RECEIVE_NONCE_PREFIX = "dead_drop_nonce_"
    }

    /**
     * Atomically read current send nonce and increment.
     * Returns the nonce value to include in the current transmission.
     */
    fun getAndIncrementSendNonce(): Long {
        val current = securePrefs.getLong(SEND_NONCE_KEY, 0L)
        securePrefs.edit().putLong(SEND_NONCE_KEY, current + 1L).apply()
        SonicVaultLogger.d("[DeadDropNonce] send nonce issued: $current")
        return current
    }

    /**
     * Validate received nonce for a given sender device.
     * Returns true if receivedNonce > lastSeen for this device (and stores it).
     * Returns false if replay (receivedNonce <= lastSeen).
     */
    fun validateAndStoreReceivedNonce(
        deviceBindingHash: ByteArray,
        receivedNonce: Long
    ): Boolean {
        val key = "$RECEIVE_NONCE_PREFIX${deviceBindingHash.joinToString("") { "%02x".format(it) }.take(16)}"
        val lastSeen = securePrefs.getLong(key, -1L)

        return if (receivedNonce <= lastSeen) {
            SonicVaultLogger.w("[DeadDropNonce] REPLAY: nonce=$receivedNonce <= last=$lastSeen")
            false
        } else {
            securePrefs.edit().putLong(key, receivedNonce).apply()
            SonicVaultLogger.d("[DeadDropNonce] valid: $receivedNonce (prev: $lastSeen)")
            true
        }
    }
}
