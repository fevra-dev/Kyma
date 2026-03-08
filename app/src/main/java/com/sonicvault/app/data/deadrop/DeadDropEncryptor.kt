package com.sonicvault.app.data.deadrop

import com.google.crypto.tink.subtle.Hkdf
import com.sonicvault.app.data.crypto.Argon2KeyDerivation
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.wipe
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ECDH + AES-256-GCM encryption for Dead Drop 1-to-many broadcast.
 *
 * Each broadcast:
 * 1. Sender generates ephemeral ECDH keypair
 * 2. Payload is encrypted with AES-256-GCM using a key derived from ECDH shared secret
 * 3. Ephemeral public key is prepended to ciphertext
 * 4. Any receiver with the shared context can derive the same key
 *
 * For hackathon demo: uses a simplified shared-key model where all nearby
 * devices use the same ephemeral key exchange (broadcast mode).
 * Production would use per-recipient ECDH with pre-exchanged public keys.
 *
 * Packet format: MAGIC(4) || ephemeral_pubkey(65) || iv(12) || ciphertext+tag
 */
object DeadDropEncryptor {

    /** Magic bytes for Dead Drop packet identification (ECDH mode). */
    private val MAGIC = byteArrayOf(0x44, 0x44, 0x52, 0x50) // "DDRP"

    /** Magic for passphrase-encrypted packets (web-compatible). */
    private val MAGIC_PASSPHRASE = byteArrayOf(0x53, 0x56, 0x44, 0x44) // "SVDD"

    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val EC_CURVE = "secp256r1"

    /**
     * Generates an ephemeral ECDH keypair for a broadcast session.
     */
    fun generateEphemeralKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(java.security.spec.ECGenParameterSpec(EC_CURVE))
        return kpg.generateKeyPair()
    }

    /**
     * Encrypts payload for Dead Drop broadcast.
     *
     * Broadcast mode: key is deterministically derived from the ephemeral public key via HKDF.
     * Any receiver with the packet can derive the same key from the embedded pubkey.
     * NOT secure against eavesdroppers — for secure transfer, use passphrase mode instead.
     *
     * @param payload plaintext bytes to broadcast
     * @param senderKeyPair ephemeral ECDH keypair (pubkey embedded in packet for key derivation)
     * @return encrypted packet with MAGIC + pubkey + iv + ciphertext, or null on failure
     */
    fun encryptForBroadcast(payload: ByteArray, senderKeyPair: KeyPair): ByteArray? {
        SonicVaultLogger.i("[DeadDrop] encrypting ${payload.size} bytes for broadcast")

        var encKey: ByteArray? = null

        try {
            val pubKeyBytes = encodeEcPublicKey(senderKeyPair.public as java.security.interfaces.ECPublicKey)

            encKey = deriveBroadcastKey(pubKeyBytes)

            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(payload)

            val packet = ByteArray(MAGIC.size + pubKeyBytes.size + iv.size + ciphertext.size)
            var offset = 0
            MAGIC.copyInto(packet, offset); offset += MAGIC.size
            pubKeyBytes.copyInto(packet, offset); offset += pubKeyBytes.size
            iv.copyInto(packet, offset); offset += iv.size
            ciphertext.copyInto(packet, offset)

            SonicVaultLogger.i("[DeadDrop] encrypted packet: ${packet.size} bytes")
            return packet

        } catch (e: Exception) {
            SonicVaultLogger.e("[DeadDrop] encryption failed", e)
            return null
        } finally {
            encKey?.wipe()
        }
    }

    /**
     * Decrypts a Dead Drop broadcast packet.
     *
     * Broadcast mode: derives the same deterministic key from the embedded pubkey via HKDF.
     * No keypair generation needed on the receiver side.
     *
     * @param packet encrypted packet bytes
     * @param receiverKeyPair unused in broadcast mode; retained for API compatibility
     * @return decrypted payload, or null if decryption fails
     */
    @Suppress("UNUSED_PARAMETER")
    fun decryptBroadcast(packet: ByteArray, receiverKeyPair: KeyPair? = null): ByteArray? {
        SonicVaultLogger.i("[DeadDrop] decrypting ${packet.size} bytes")

        if (packet.size < MAGIC.size + 65 + GCM_IV_LENGTH + 16) {
            SonicVaultLogger.w("[DeadDrop] packet too short")
            return null
        }

        for (i in MAGIC.indices) {
            if (packet[i] != MAGIC[i]) {
                SonicVaultLogger.w("[DeadDrop] magic mismatch")
                return null
            }
        }

        var encKey: ByteArray? = null

        try {
            val pubKeyBytes = packet.copyOfRange(MAGIC.size, MAGIC.size + 65)
            val iv = packet.copyOfRange(MAGIC.size + 65, MAGIC.size + 65 + GCM_IV_LENGTH)
            val ciphertext = packet.copyOfRange(MAGIC.size + 65 + GCM_IV_LENGTH, packet.size)

            encKey = deriveBroadcastKey(pubKeyBytes)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val plaintext = cipher.doFinal(ciphertext)

            SonicVaultLogger.i("[DeadDrop] decrypted ${plaintext.size} bytes")
            return plaintext

        } catch (e: Exception) {
            SonicVaultLogger.e("[DeadDrop] decryption failed", e)
            return null
        } finally {
            encKey?.wipe()
        }
    }

    /**
     * Encrypts payload with passphrase using Argon2id key derivation.
     * Format: SVDD(4) || salt(16) || iv(12) || ciphertext+tag.
     *
     * @param payload plaintext bytes
     * @param passphrase session code (e.g. "abc123") — same on sender and receiver
     * @return SVDD packet, or null on failure
     */
    fun encryptWithPassphrase(payload: ByteArray, passphrase: String): ByteArray? {
        if (passphrase.isBlank()) return null
        SonicVaultLogger.i("[DeadDrop] encrypting ${payload.size} bytes with passphrase (Argon2id)")
        var encKey: ByteArray? = null
        try {
            val salt = Argon2KeyDerivation.generateSalt()
            encKey = Argon2KeyDerivation.deriveKey(passphrase, salt)
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(payload)
            // Packet: SVDD(4) || salt(16) || iv(12) || ciphertext+tag
            val packet = ByteArray(MAGIC_PASSPHRASE.size + ARGON2_SALT_LENGTH + iv.size + ciphertext.size)
            var offset = 0
            MAGIC_PASSPHRASE.copyInto(packet, offset); offset += MAGIC_PASSPHRASE.size
            salt.copyInto(packet, offset); offset += ARGON2_SALT_LENGTH
            iv.copyInto(packet, offset); offset += iv.size
            ciphertext.copyInto(packet, offset)
            SonicVaultLogger.i("[DeadDrop] passphrase packet: ${packet.size} bytes")
            return packet
        } catch (e: Exception) {
            SonicVaultLogger.e("[DeadDrop] passphrase encrypt failed", e)
            return null
        } finally {
            encKey?.wipe()
        }
    }

    /**
     * Decrypts SVDD passphrase packet (Argon2id KDF).
     * Packet format: SVDD(4) || salt(16) || iv(12) || ciphertext+tag.
     *
     * @param packet SVDD packet
     * @param passphrase same session code used when encrypting
     * @return plaintext, or null on failure
     */
    fun decryptWithPassphrase(packet: ByteArray, passphrase: String): ByteArray? {
        val minSize = MAGIC_PASSPHRASE.size + ARGON2_SALT_LENGTH + GCM_IV_LENGTH + 16
        if (passphrase.isBlank() || packet.size < minSize) return null
        if (!MAGIC_PASSPHRASE.indices.all { packet[it] == MAGIC_PASSPHRASE[it] }) return null
        var encKey: ByteArray? = null
        try {
            var offset = MAGIC_PASSPHRASE.size
            val salt = packet.copyOfRange(offset, offset + ARGON2_SALT_LENGTH); offset += ARGON2_SALT_LENGTH
            val iv = packet.copyOfRange(offset, offset + GCM_IV_LENGTH); offset += GCM_IV_LENGTH
            val ciphertext = packet.copyOfRange(offset, packet.size)
            encKey = Argon2KeyDerivation.deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            return cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            SonicVaultLogger.e("[DeadDrop] passphrase decrypt failed", e)
            return null
        } finally {
            encKey?.wipe()
        }
    }

    /**
     * Checks if a byte array is a Dead Drop packet by verifying the magic header.
     */
    fun isDeadDropPacket(data: ByteArray): Boolean {
        if (data.size < MAGIC.size) return false
        return MAGIC.indices.all { data[it] == MAGIC[it] }
    }

    /** Checks if data is SVDD passphrase packet. */
    fun isPassphrasePacket(data: ByteArray): Boolean {
        if (data.size < MAGIC_PASSPHRASE.size) return false
        return MAGIC_PASSPHRASE.indices.all { data[it] == MAGIC_PASSPHRASE[it] }
    }

    private const val ARGON2_SALT_LENGTH = 16
    private val HKDF_BROADCAST_INFO = "SonicVault-broadcast-v2".toByteArray(Charsets.UTF_8)

    /**
     * Derives a deterministic 256-bit key from an EC public key via HKDF.
     * Both sender and receiver derive the same key from the embedded pubkey.
     */
    private fun deriveBroadcastKey(pubKeyBytes: ByteArray): ByteArray {
        val ikm = MessageDigest.getInstance("SHA-256").digest(pubKeyBytes)
        return Hkdf.computeHkdf("HMACSHA256", ikm, ByteArray(0), HKDF_BROADCAST_INFO, 32)
    }

    /** Encodes an EC public key as uncompressed point: 0x04 || x(32) || y(32). */
    private fun encodeEcPublicKey(pk: java.security.interfaces.ECPublicKey): ByteArray {
        val point = pk.w
        val x = point.affineX.toByteArray()
        val y = point.affineY.toByteArray()
        val encoded = ByteArray(65)
        encoded[0] = 0x04
        val xPad = 32 - x.size.coerceAtMost(32)
        x.copyInto(encoded, 1 + xPad, maxOf(0, x.size - 32), x.size)
        val yPad = 32 - y.size.coerceAtMost(32)
        y.copyInto(encoded, 33 + yPad, maxOf(0, y.size - 32), y.size)
        return encoded
    }
}
