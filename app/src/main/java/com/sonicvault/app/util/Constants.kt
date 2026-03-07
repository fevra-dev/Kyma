package com.sonicvault.app.util

/**
 * Application-wide constants for SonicVault MVP.
 * LSB and capacity rules align with spec (2 LSB, ~11 KB/s @ 44.1 kHz mono).
 */
object Constants {
    /** Android Keystore alias for the master encryption key (TEE-backed). */
    const val KEY_ALIAS = "sonicvault_master_key"

    /** LSB bits per sample used for steganography (balance of capacity vs. imperceptibility). */
    const val DEFAULT_NUM_LSB = 2

    /** Minimum cover audio duration in seconds to ensure capacity for payload + safety margin. */
    const val MIN_COVER_DURATION_SECONDS = 5

    /** AES-GCM IV length in bytes. */
    const val GCM_IV_LENGTH = 12

    /** Payload length prefix in bytes (big-endian). */
    const val PAYLOAD_LENGTH_PREFIX_BYTES = 4

    /** Device binding hash length (SHA-256); prepended to payload so recovery only on same device. */
    const val DEVICE_BINDING_HASH_BYTES = 32

    /** Standard WAV header size for 16-bit PCM (RIFF + fmt + data chunk headers). */
    const val WAV_HEADER_SIZE = 44

    /** Default sample rate for written WAV (match typical cover). */
    const val DEFAULT_SAMPLE_RATE = 44100

    /** Payload format version: 1 = single ciphertext, 2 = dual (real + decoy). */
    const val PAYLOAD_VERSION_SINGLE = 1
    const val PAYLOAD_VERSION_DURESS = 2
    /** Payload format version: 3 = password mode (Argon2id), salt+iv+ct, cross-device recovery. */
    const val PAYLOAD_VERSION_PASSWORD = 3
    /** Payload format version: 4 = timelock (inheritance), unlock_timestamp(8) || iv(12) || ct. */
    const val PAYLOAD_VERSION_TIMELOCK = 4
    /** Payload format version: 5 = hybrid (metadata in phase, payload in LSB replacement). */
    const val PAYLOAD_VERSION_HYBRID = 5
    /** Payload format version: 7 = hybrid with LSB Matching + PRNG scatter (steganalysis-resistant). */
    const val PAYLOAD_VERSION_HYBRID_LSB_MATCHING = 7
    /** Payload format version: 6 = SE-bound (Seed Vault signMessage KDF key). */
    const val PAYLOAD_VERSION_SE_BOUND = 6
    /**
     * Payload format version: 8 = v2 acoustic (ECDH forward secrecy).
     * Layout: [version:1][session_nonce:8][device_binding:32][ecdh_ephemeral_pubkey:32][iv:12][ciphertext][gcm_tag:16][sha256:32]
     * Fixed overhead: 133 bytes + ciphertext. Backward compat: receiver falls back to v1 when version=0x01.
     */
    const val PAYLOAD_VERSION_V2_ACOUSTIC = 8

    /** Timelock: unlock timestamp size in bytes (Unix epoch, big-endian). */
    const val TIMELOCK_TIMESTAMP_BYTES = 8

    /** Hybrid metadata size: version(1) + payload_len(4) + checksum(32) = 37 bytes. */
    const val HYBRID_METADATA_BYTES = 37
}
