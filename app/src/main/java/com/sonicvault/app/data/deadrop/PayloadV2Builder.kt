package com.sonicvault.app.data.deadrop

import com.sonicvault.app.logging.SonicVaultLogger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Builds Payload v2 packets for ECDH session-key encryption.
 *
 * Layout: [version:1][nonce:8][binding:32][pubkey:32][iv:12][ciphertext+tag][sha256:32]
 */
object PayloadV2Builder {

    private const val GCM_TAG_BITS = 128

    /**
     * Builds a v2 packet from plaintext and session key.
     *
     * @param plaintext Message bytes to encrypt
     * @param sessionKey 32-byte AES key from ECDH handshake (will not be modified)
     * @param nonce Monotonic session nonce, big-endian
     * @param deviceBinding 32-byte device binding hash
     * @param ecdhEphemeralPubKey 32-byte X25519 public key
     * @return v2 packet, or null on failure
     */
    fun build(
        plaintext: ByteArray,
        sessionKey: ByteArray,
        nonce: Long,
        deviceBinding: ByteArray,
        ecdhEphemeralPubKey: ByteArray
    ): ByteArray? {
        if (sessionKey.size != 32) return null
        if (deviceBinding.size != PayloadV2Spec.DEVICE_BINDING_BYTES) return null
        if (ecdhEphemeralPubKey.size != PayloadV2Spec.ECDH_PUBKEY_BYTES) return null

        return try {
            val iv = ByteArray(PayloadV2Spec.IV_BYTES)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(sessionKey, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            val ciphertextAndTag = cipher.doFinal(plaintext)

            val sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(plaintext)

            val packet = ByteArray(
                PayloadV2Spec.HEADER_BYTES + ciphertextAndTag.size + PayloadV2Spec.SHA256_BYTES
            )
            var offset = 0
            packet[offset++] = PayloadV2Spec.VERSION_BYTE
            ByteBuffer.wrap(packet, offset, PayloadV2Spec.SESSION_NONCE_BYTES)
                .order(ByteOrder.BIG_ENDIAN).putLong(nonce)
            offset += PayloadV2Spec.SESSION_NONCE_BYTES
            deviceBinding.copyInto(packet, offset)
            offset += PayloadV2Spec.DEVICE_BINDING_BYTES
            ecdhEphemeralPubKey.copyInto(packet, offset)
            offset += PayloadV2Spec.ECDH_PUBKEY_BYTES
            iv.copyInto(packet, offset)
            offset += PayloadV2Spec.IV_BYTES
            ciphertextAndTag.copyInto(packet, offset)
            offset += ciphertextAndTag.size
            sha256.copyInto(packet, offset)

            SonicVaultLogger.i("[PayloadV2Builder] built packet: ${packet.size} bytes")
            packet
        } catch (e: Exception) {
            SonicVaultLogger.e("[PayloadV2Builder] build failed", e)
            null
        }
    }
}
