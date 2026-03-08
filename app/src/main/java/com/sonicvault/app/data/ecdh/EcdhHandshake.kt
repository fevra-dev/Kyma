package com.sonicvault.app.data.ecdh

import com.sonicvault.app.data.crypto.SasGenerator
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.wipe
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.X25519
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeout

/**
 * X25519 ECDH key exchange over the acoustic channel for per-session forward secrecy.
 *
 * Handshake protocol (2 acoustic transmissions, ~8s overhead):
 * - Initiator broadcasts [0x02][0x01][pubkeyA:32], waits for ECDH_RESP
 * - Responder receives, broadcasts [0x02][0x02][pubkeyB:32]
 * - Both derive shared secret via X25519, HKDF-SHA256 → 32-byte session key
 *
 * Packet format (34 bytes): [version=0x02][type=0x01|0x02][pubkey:32]
 */
interface EcdhHandshake {

    data class HandshakeResult(
        val sessionKey: ByteArray,
        val localEphemeralPubKey: ByteArray,
        val remoteEphemeralPubKey: ByteArray,
        /** 6-char SAS for verbal MITM verification, bound to both pubkeys and session key. */
        val sas: String
    ) : java.io.Closeable {
        /** Wipes session key material. Call when the session is no longer needed. */
        override fun close() { sessionKey.wipe() }
        override fun equals(other: Any?) = (other is HandshakeResult) &&
            sessionKey.contentEquals(other.sessionKey) &&
            localEphemeralPubKey.contentEquals(other.localEphemeralPubKey) &&
            remoteEphemeralPubKey.contentEquals(other.remoteEphemeralPubKey) &&
            sas == other.sas
        override fun hashCode() = sessionKey.contentHashCode()
    }

    /**
     * Initiator: broadcast ECDH_INIT, wait for ECDH_RESP via [responseChannel], derive session key.
     */
    suspend fun initiateAsInitiator(
        onTransmitRequired: suspend (ByteArray) -> Unit,
        responseChannel: ReceiveChannel<ByteArray>,
        onComplete: (EcdhHandshake.HandshakeResult) -> Unit,
        onTimeout: () -> Unit,
        timeoutMs: Long = 30_000L
    )

    /**
     * Responder: receive ECDH_INIT, broadcast ECDH_RESP, derive session key.
     */
    suspend fun initiateAsResponder(
        receivedInitPacket: ByteArray,
        onTransmitRequired: suspend (ByteArray) -> Unit,
        onComplete: (HandshakeResult) -> Unit
    )

    /** Parse raw bytes; returns EcdhPacket if valid ECDH packet, else null. */
    fun parseEcdhPacket(raw: ByteArray): EcdhPacket?

    data class EcdhPacket(val type: PacketType, val publicKey: ByteArray)
    enum class PacketType { ECDH_INIT, ECDH_RESP }
}

class EcdhHandshakeImpl : EcdhHandshake {

    companion object {
        private const val VERSION_BYTE: Byte = 0x02
        private const val TYPE_ECDH_INIT: Byte = 0x01
        private const val TYPE_ECDH_RESP: Byte = 0x02
        private const val PACKET_SIZE = 34
        private const val HKDF_ALGORITHM = "HMACSHA256"
        private const val HKDF_INFO = "SonicVault-v2"
        private const val SESSION_KEY_LEN = 32
    }

    override suspend fun initiateAsInitiator(
        onTransmitRequired: suspend (ByteArray) -> Unit,
        responseChannel: ReceiveChannel<ByteArray>,
        onComplete: (EcdhHandshake.HandshakeResult) -> Unit,
        onTimeout: () -> Unit,
        timeoutMs: Long
    ) {
        SonicVaultLogger.d("[EcdhHandshake] Generating X25519 ephemeral keypair (INITIATOR)")
        val privateKey = X25519.generatePrivateKey()
        val publicKey = X25519.publicFromPrivate(privateKey)

        val initPacket = buildPacket(TYPE_ECDH_INIT, publicKey)
        onTransmitRequired(initPacket)

        try {
            val remotePubKey = withTimeout(timeoutMs) { responseChannel.receive() }
            SonicVaultLogger.i("[EcdhHandshake] ECDH_RESP received")
            val result = deriveSessionKey(privateKey, publicKey, remotePubKey)
            privateKey.wipe()
            SonicVaultLogger.d("[EcdhHandshake] Session key established")
            onComplete(result)
        } catch (e: Exception) {
            privateKey.wipe()
            SonicVaultLogger.w("[EcdhHandshake] Timeout or error: ${e.message}")
            onTimeout()
        }
    }

    override suspend fun initiateAsResponder(
        receivedInitPacket: ByteArray,
        onTransmitRequired: suspend (ByteArray) -> Unit,
        onComplete: (EcdhHandshake.HandshakeResult) -> Unit
    ) {
        val initPacket = parseEcdhPacket(receivedInitPacket)
            ?: throw IllegalArgumentException("Invalid ECDH_INIT packet")
        if (initPacket.type != EcdhHandshake.PacketType.ECDH_INIT) {
            throw IllegalArgumentException("Expected ECDH_INIT, got ${initPacket.type}")
        }

        SonicVaultLogger.d("[EcdhHandshake] Generating X25519 ephemeral keypair (RESPONDER)")
        val privateKey = X25519.generatePrivateKey()
        val publicKey = X25519.publicFromPrivate(privateKey)
        val remotePubKey = initPacket.publicKey

        val respPacket = buildPacket(TYPE_ECDH_RESP, publicKey)
        onTransmitRequired(respPacket)

        val result = deriveSessionKey(privateKey, publicKey, remotePubKey)
        privateKey.wipe()
        SonicVaultLogger.d("[EcdhHandshake] Session key established")
        onComplete(result)
    }

    override fun parseEcdhPacket(raw: ByteArray): EcdhHandshake.EcdhPacket? {
        if (raw.size != PACKET_SIZE) return null
        if (raw[0] != VERSION_BYTE) return null
        val type = when (raw[1]) {
            TYPE_ECDH_INIT -> EcdhHandshake.PacketType.ECDH_INIT
            TYPE_ECDH_RESP -> EcdhHandshake.PacketType.ECDH_RESP
            else -> return null
        }
        return EcdhHandshake.EcdhPacket(type, raw.copyOfRange(2, PACKET_SIZE))
    }

    private fun buildPacket(type: Byte, pubKey: ByteArray): ByteArray =
        byteArrayOf(VERSION_BYTE, type) + pubKey

    /**
     * Derives session key via HKDF with both pubkeys bound in the info parameter,
     * then computes a 6-char SAS for verbal MITM verification.
     */
    private fun deriveSessionKey(
        myPrivKey: ByteArray,
        myPubKey: ByteArray,
        remotePubKey: ByteArray
    ): EcdhHandshake.HandshakeResult {
        val sharedSecret = X25519.computeSharedSecret(myPrivKey, remotePubKey)
        // Bind both public keys into HKDF info for session-specific domain separation
        val info = HKDF_INFO.toByteArray(Charsets.UTF_8) + myPubKey + remotePubKey
        val sessionKey = Hkdf.computeHkdf(
            HKDF_ALGORITHM,
            sharedSecret,
            ByteArray(0),
            info,
            SESSION_KEY_LEN
        )
        sharedSecret.wipe()
        val sas = SasGenerator.generate(sessionKey, "SONICVAULT-ECDH-SAS")
        return EcdhHandshake.HandshakeResult(sessionKey, myPubKey, remotePubKey, sas)
    }
}
