package com.sonicvault.app.data.deadrop

import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.wipe
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
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
     * @param payload plaintext bytes to broadcast
     * @param senderKeyPair ephemeral ECDH keypair
     * @return encrypted packet with MAGIC + pubkey + iv + ciphertext, or null on failure
     */
    fun encryptForBroadcast(payload: ByteArray, senderKeyPair: KeyPair): ByteArray? {
        SonicVaultLogger.i("[DeadDrop] encrypting ${payload.size} bytes for broadcast")

        var sharedSecret: ByteArray? = null
        var encKey: ByteArray? = null

        try {
            // For broadcast mode: derive key from sender's own keypair
            // (all receivers need the public key to derive the same shared secret)
            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(senderKeyPair.private)
            ka.doPhase(senderKeyPair.public, true)
            sharedSecret = ka.generateSecret()

            // KDF: SHA-256 of shared secret
            encKey = java.security.MessageDigest.getInstance("SHA-256").digest(sharedSecret!!)

            // AES-256-GCM encrypt
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(payload)

            // Encode public key
            val pubKeyBytes = (senderKeyPair.public as java.security.interfaces.ECPublicKey).let { pk ->
                val point = pk.w
                val x = point.affineX.toByteArray()
                val y = point.affineY.toByteArray()
                // Uncompressed EC point: 0x04 || x(32) || y(32)
                val encoded = ByteArray(65)
                encoded[0] = 0x04
                val xPad = 32 - x.size.coerceAtMost(32)
                x.copyInto(encoded, 1 + xPad, maxOf(0, x.size - 32), x.size)
                val yPad = 32 - y.size.coerceAtMost(32)
                y.copyInto(encoded, 33 + yPad, maxOf(0, y.size - 32), y.size)
                encoded
            }

            // Assemble packet: MAGIC(4) + pubkey(65) + iv(12) + ciphertext
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
            sharedSecret?.wipe()
            encKey?.wipe()
        }
    }

    /**
     * Decrypts a Dead Drop broadcast packet.
     *
     * @param packet encrypted packet bytes
     * @param receiverKeyPair receiver's keypair (for production per-recipient ECDH)
     * @return decrypted payload, or null if decryption fails
     */
    fun decryptBroadcast(packet: ByteArray, receiverKeyPair: KeyPair? = null): ByteArray? {
        SonicVaultLogger.i("[DeadDrop] decrypting ${packet.size} bytes")

        if (packet.size < MAGIC.size + 65 + GCM_IV_LENGTH + 16) {
            SonicVaultLogger.w("[DeadDrop] packet too short")
            return null
        }

        // Verify magic
        for (i in MAGIC.indices) {
            if (packet[i] != MAGIC[i]) {
                SonicVaultLogger.w("[DeadDrop] magic mismatch")
                return null
            }
        }

        var sharedSecret: ByteArray? = null
        var encKey: ByteArray? = null

        try {
            // Extract components
            val pubKeyBytes = packet.copyOfRange(MAGIC.size, MAGIC.size + 65)
            val iv = packet.copyOfRange(MAGIC.size + 65, MAGIC.size + 65 + GCM_IV_LENGTH)
            val ciphertext = packet.copyOfRange(MAGIC.size + 65 + GCM_IV_LENGTH, packet.size)

            // Reconstruct EC public key from uncompressed point
            val kf = java.security.KeyFactory.getInstance("EC")
            val x = java.math.BigInteger(1, pubKeyBytes.copyOfRange(1, 33))
            val y = java.math.BigInteger(1, pubKeyBytes.copyOfRange(33, 65))
            val ecPoint = java.security.spec.ECPoint(x, y)

            // Use standard curve params
            val ecParams = (generateEphemeralKeyPair().public as java.security.interfaces.ECPublicKey).params
            val pubKeySpec = java.security.spec.ECPublicKeySpec(ecPoint, ecParams)
            val senderPubKey = kf.generatePublic(pubKeySpec)

            // For broadcast mode: derive same shared secret
            val keyPair = receiverKeyPair ?: run {
                // Self-decrypt: use sender's own key agreement
                val ka = KeyAgreement.getInstance("ECDH")
                val tempKp = generateEphemeralKeyPair()
                ka.init(tempKp.private)
                ka.doPhase(senderPubKey, true)
                sharedSecret = ka.generateSecret()
                encKey = java.security.MessageDigest.getInstance("SHA-256").digest(sharedSecret!!)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey!!, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
                return cipher.doFinal(ciphertext)
            }

            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(keyPair.private)
            ka.doPhase(senderPubKey, true)
            sharedSecret = ka.generateSecret()
            encKey = java.security.MessageDigest.getInstance("SHA-256").digest(sharedSecret!!)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey!!, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val plaintext = cipher.doFinal(ciphertext)

            SonicVaultLogger.i("[DeadDrop] decrypted ${plaintext.size} bytes")
            return plaintext

        } catch (e: Exception) {
            SonicVaultLogger.e("[DeadDrop] decryption failed", e)
            return null
        } finally {
            sharedSecret?.wipe()
            encKey?.wipe()
        }
    }

    /**
     * Encrypts payload with passphrase for web-compatible broadcast.
     * Format: SVDD(4) || iv(12) || ciphertext. Key = SHA-256(utf8(passphrase)).
     *
     * @param payload plaintext bytes
     * @param passphrase session code (e.g. "abc123") — same on sender and receiver
     * @return SVDD packet, or null on failure
     */
    fun encryptWithPassphrase(payload: ByteArray, passphrase: String): ByteArray? {
        if (passphrase.isBlank()) return null
        SonicVaultLogger.i("[DeadDrop] encrypting ${payload.size} bytes with passphrase")
        var encKey: ByteArray? = null
        try {
            encKey = java.security.MessageDigest.getInstance("SHA-256").digest(passphrase.toByteArray(Charsets.UTF_8))
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(payload)
            val packet = ByteArray(MAGIC_PASSPHRASE.size + iv.size + ciphertext.size)
            MAGIC_PASSPHRASE.copyInto(packet, 0)
            iv.copyInto(packet, MAGIC_PASSPHRASE.size)
            ciphertext.copyInto(packet, MAGIC_PASSPHRASE.size + iv.size)
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
     * Decrypts SVDD passphrase packet.
     *
     * @param packet SVDD packet
     * @param passphrase same session code used when encrypting
     * @return plaintext, or null on failure
     */
    fun decryptWithPassphrase(packet: ByteArray, passphrase: String): ByteArray? {
        if (passphrase.isBlank() || packet.size < MAGIC_PASSPHRASE.size + GCM_IV_LENGTH + 16) return null
        if (!MAGIC_PASSPHRASE.indices.all { packet[it] == MAGIC_PASSPHRASE[it] }) return null
        var encKey: ByteArray? = null
        try {
            encKey = java.security.MessageDigest.getInstance("SHA-256").digest(passphrase.toByteArray(Charsets.UTF_8))
            val iv = packet.copyOfRange(MAGIC_PASSPHRASE.size, MAGIC_PASSPHRASE.size + GCM_IV_LENGTH)
            val ciphertext = packet.copyOfRange(MAGIC_PASSPHRASE.size + GCM_IV_LENGTH, packet.size)
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
}
