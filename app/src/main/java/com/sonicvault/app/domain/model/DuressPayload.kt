package com.sonicvault.app.domain.model

import com.sonicvault.app.data.crypto.EncryptedPayload

/**
 * Result of extracting payload from stego backup.
 * When hasDuress=true, both real and decoy ciphertexts are present.
 * When unlockTimestamp!=null, decryption is refused until that Unix epoch (seconds).
 * When expectedChecksumRaw is non-null (hybrid mode), recovery verifies SHA-256 of plaintext.
 * When requiresSeBoundDecryption=true, decrypt via Seed Vault signMessage KDF (hardware-bound).
 */
data class ExtractedPayload(
    val hasDuress: Boolean,
    val realPayload: EncryptedPayload,
    val decoyPayload: EncryptedPayload?,
    val unlockTimestamp: Long? = null,
    /** SHA-256 of plaintext from hybrid metadata; verified after decrypt. */
    val expectedChecksumRaw: ByteArray? = null,
    /** SE-bound: decrypt via SeedVaultKeyOracle (same device only). */
    val requiresSeBoundDecryption: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExtractedPayload) return false
        if (hasDuress != other.hasDuress) return false
        if (realPayload != other.realPayload) return false
        if (decoyPayload != other.decoyPayload) return false
        if (unlockTimestamp != other.unlockTimestamp) return false
        if (requiresSeBoundDecryption != other.requiresSeBoundDecryption) return false
        if (expectedChecksumRaw == null != (other.expectedChecksumRaw == null)) return false
        return expectedChecksumRaw == null || expectedChecksumRaw.contentEquals(other.expectedChecksumRaw!!)
    }
    override fun hashCode(): Int {
        var result = hasDuress.hashCode()
        result = 31 * result + realPayload.hashCode()
        result = 31 * result + (decoyPayload?.hashCode() ?: 0)
        result = 31 * result + (unlockTimestamp?.hashCode() ?: 0)
        result = 31 * result + requiresSeBoundDecryption.hashCode()
        result = 31 * result + (expectedChecksumRaw?.contentHashCode() ?: 0)
        return result
    }
}
