package com.sonicvault.app.data.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters

/**
 * HKDF-SHA256 key derivation for SE-bound encryption.
 *
 * Derives a 32-byte AES key from Seed Vault signature (64B Ed25519).
 * Uses Bouncy Castle; no magic numbers per security conventions.
 */
object HkdfKeyDeriver {

    private const val AES_KEY_LENGTH = 32

    /**
     * Derives a 32-byte AES-256 key from the given input key material (IKM).
     *
     * @param ikm input key material (e.g. 64-byte Ed25519 signature)
     * @param salt optional salt (null = zeros); use empty or random for key derivation
     * @param info context/application specific info (e.g. "SonicVault:AES-Key-Derivation:v1")
     * @return 32-byte AES key, or null on failure
     */
    fun derive(ikm: ByteArray, salt: ByteArray?, info: ByteArray): ByteArray? {
        return try {
            val params = HKDFParameters(ikm, salt, info)
            val generator = HKDFBytesGenerator(SHA256Digest())
            generator.init(params)
            val key = ByteArray(AES_KEY_LENGTH)
            generator.generateBytes(key, 0, AES_KEY_LENGTH)
            key
        } catch (e: Exception) {
            null
        }
    }
}
