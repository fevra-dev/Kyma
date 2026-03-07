package com.sonicvault.app.data.deadrop

import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.wipe
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Parses Payload v2 packets and decrypts with session key.
 *
 * Layout: [version:1][nonce:8][binding:32][pubkey:32][iv:12][ciphertext+tag][sha256:32]
 */
object PayloadV2Parser {

    private const val GCM_TAG_BITS = 128

    /**
     * Parses and decrypts a v2 packet.
     *
     * @param packet Raw v2 packet bytes
     * @param sessionKey 32-byte AES key from ECDH handshake (will not be modified)
     * @return Plaintext bytes, or null on parse/decrypt/verify failure
     */
    fun parse(packet: ByteArray, sessionKey: ByteArray): ByteArray? {
        if (packet.size < PayloadV2Spec.MIN_PACKET_BYTES) {
            SonicVaultLogger.w("[PayloadV2Parser] packet too short: ${packet.size}")
            return null
        }
        if (packet[0] != PayloadV2Spec.VERSION_BYTE) {
            SonicVaultLogger.w("[PayloadV2Parser] wrong version byte: 0x${packet[0].toString(16)}")
            return null
        }
        if (sessionKey.size != 32) return null

        return try {
            var offset = 1 + PayloadV2Spec.SESSION_NONCE_BYTES + PayloadV2Spec.DEVICE_BINDING_BYTES + PayloadV2Spec.ECDH_PUBKEY_BYTES
            val iv = packet.copyOfRange(offset, offset + PayloadV2Spec.IV_BYTES)
            offset += PayloadV2Spec.IV_BYTES
            val ciphertextAndTag = packet.copyOfRange(
                offset,
                packet.size - PayloadV2Spec.SHA256_BYTES
            )
            val sha256Stored = packet.copyOfRange(
                packet.size - PayloadV2Spec.SHA256_BYTES,
                packet.size
            )

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(sessionKey, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            val plaintext = cipher.doFinal(ciphertextAndTag)

            val sha256Computed = java.security.MessageDigest.getInstance("SHA-256").digest(plaintext)
            if (!sha256Computed.contentEquals(sha256Stored)) {
                SonicVaultLogger.w("[PayloadV2Parser] SHA-256 integrity check failed")
                plaintext.wipe()
                return null
            }

            SonicVaultLogger.i("[PayloadV2Parser] decrypted ${plaintext.size} bytes")
            plaintext
        } catch (e: Exception) {
            SonicVaultLogger.e("[PayloadV2Parser] parse failed", e)
            null
        }
    }

    /** Checks if packet is v2 format (version byte 0x08). */
    fun isV2Packet(packet: ByteArray): Boolean {
        return packet.isNotEmpty() && packet[0] == PayloadV2Spec.VERSION_BYTE
    }
}
