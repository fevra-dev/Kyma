package com.sonicvault.app.data.crypto

/**
 * Result of AES-GCM encryption: IV (12 bytes) and ciphertext with appended auth tag (16 bytes).
 * Stored inside LSB stego as: length(4 BE) || iv || ciphertextWithTag.
 *
 * For password mode (Argon2id): salt is prepended; payload format [salt:16][iv:12][ciphertext].
 * [salt] is null for biometric/Keystore mode.
 */
data class EncryptedPayload(
    val iv: ByteArray,
    val ciphertextWithTag: ByteArray,
    val salt: ByteArray? = null
) {
    /** True when payload was encrypted with Argon2 password (not Keystore/biometric). */
    val isPasswordMode: Boolean get() = salt != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedPayload
        if (!iv.contentEquals(other.iv)) return false
        if (!ciphertextWithTag.contentEquals(other.ciphertextWithTag)) return false
        if (salt != null != (other.salt != null)) return false
        if (salt != null && !salt.contentEquals(other.salt)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = iv.contentHashCode()
        result = 31 * result + ciphertextWithTag.contentHashCode()
        result = 31 * result + (salt?.contentHashCode() ?: 0)
        return result
    }
}
