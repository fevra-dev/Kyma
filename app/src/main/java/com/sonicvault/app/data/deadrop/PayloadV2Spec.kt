package com.sonicvault.app.data.deadrop

import com.sonicvault.app.util.Constants

/**
 * Payload v2 structure for acoustic transfer with ECDH forward secrecy (Tier 3).
 *
 * Layout: [version:1][session_nonce:8][device_binding:32][ecdh_ephemeral_pubkey:32][iv:12][ciphertext][gcm_tag:16][sha256:32]
 *
 * - version: 0x08 (PAYLOAD_VERSION_V2_ACOUSTIC)
 * - session_nonce: monotonic, big-endian, replay protection
 * - device_binding: SHA-256 of device identity
 * - ecdh_ephemeral_pubkey: X25519 public key (32 bytes) for session key derivation
 * - iv: 12-byte GCM IV
 * - ciphertext: AES-256-GCM encrypted payload (variable)
 * - gcm_tag: 16-byte GCM authentication tag
 * - sha256: integrity checksum of plaintext
 *
 * Backward compat: receiver checks version byte; if 0x01 (legacy), uses v1 flow.
 */
object PayloadV2Spec {

    const val VERSION_BYTE = Constants.PAYLOAD_VERSION_V2_ACOUSTIC.toByte()
    const val SESSION_NONCE_BYTES = 8
    const val DEVICE_BINDING_BYTES = 32
    const val ECDH_PUBKEY_BYTES = 32
    const val IV_BYTES = Constants.GCM_IV_LENGTH
    const val GCM_TAG_BYTES = 16
    const val SHA256_BYTES = 32

    /** Fixed header size before ciphertext. */
    const val HEADER_BYTES = 1 + SESSION_NONCE_BYTES + DEVICE_BINDING_BYTES + ECDH_PUBKEY_BYTES + IV_BYTES

    /** Trailer size after ciphertext. */
    const val TRAILER_BYTES = GCM_TAG_BYTES + SHA256_BYTES

    /** Minimum packet size (header + empty ciphertext + trailer). */
    const val MIN_PACKET_BYTES = HEADER_BYTES + TRAILER_BYTES
}
