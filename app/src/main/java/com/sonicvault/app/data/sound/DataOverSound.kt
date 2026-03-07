package com.sonicvault.app.data.sound

import com.sonicvault.app.data.codec.ReedSolomonCodec
import com.sonicvault.app.domain.model.Protocol
import com.sonicvault.app.logging.SonicVaultLogger

/**
 * Data-over-sound API for SonicVault.
 * Uses ggwave (ultrasonic protocols) at 48 kHz, with Reed-Solomon ECC wrapping
 * for byte-level error correction beyond ggwave's built-in FSK robustness.
 */
object DataOverSoundConstants {
    /** @deprecated Use GgwaveDataOverSound.SAMPLE_RATE (48 kHz) for sound transmit/receive. */
    const val SAMPLE_RATE = 44100
}

/**
 * Encodes a byte array to PCM (16-bit mono @ 48 kHz) via ggwave, optionally with Reed-Solomon.
 *
 * Pipeline (useReedSolomon=true): payload -> RS encode (add parity) -> ggwave modulate -> PCM
 * Pipeline (useReedSolomon=false): payload -> ggwave modulate -> PCM (raw, ggwave-js compatible)
 *
 * Use RS for sensitive/long payloads (Dead Drop, backup). Use raw for short, compatibility-sensitive
 * flows (Solana Pay, simple text) so receivers like ggwave.ggerganov.com can decode.
 *
 * Note: [AcousticProtocols.MAX_PAYLOAD_BYTES] applies to a single ggwave frame (140 bytes).
 * GgwaveNative chunks payloads > 140 bytes internally.
 *
 * @param data Payload bytes.
 * @param protocol Protocol for transmission (AUDIBLE or ULTRASONIC).
 * @param useReedSolomon When true, wrap with RS before ggwave. When false, raw ggwave only.
 * @param applyFingerprintRandomization when true and protocol is Ultrasonic, randomizes spectral envelope
 * @return PCM samples, or null on encode failure.
 */
fun encodeDataOverSound(
    data: ByteArray,
    protocol: Protocol = Protocol.ULTRASONIC,
    useReedSolomon: Boolean = true,
    applyFingerprintRandomization: Boolean = false
): ShortArray? {
    val toEncode = if (useReedSolomon) {
        val rsEncoded = ReedSolomonCodec.encode(data)
        SonicVaultLogger.i("[DataOverSound] RS wrapped ${data.size} -> ${rsEncoded.size} bytes")
        rsEncoded
    } else {
        SonicVaultLogger.i("[DataOverSound] raw ggwave ${data.size} bytes (no RS)")
        data
    }
    return GgwaveDataOverSound.encode(toEncode, protocol, useDss = false, applyFingerprintRandomization = applyFingerprintRandomization)
}

/**
 * Decodes PCM (16-bit mono @ 48 kHz) to byte array via ggwave + Reed-Solomon.
 *
 * Pipeline: PCM -> ggwave demodulate -> RS decode (correct errors) -> payload
 *
 * @param samples Recorded PCM.
 * @param sampleRate Sample rate of recording (default 48 kHz).
 * @return Extracted payload with errors corrected, or null if no valid payload.
 */
fun decodeDataOverSound(samples: ShortArray, sampleRate: Int = GgwaveDataOverSound.SAMPLE_RATE): ByteArray? {
    val rawPayload = GgwaveDataOverSound.decode(samples, sampleRate) ?: return null
    val decoded = ReedSolomonCodec.decode(rawPayload)
    if (decoded != null) {
        SonicVaultLogger.i("[DataOverSound] RS decoded ${rawPayload.size} -> ${decoded.size} bytes")
    } else {
        SonicVaultLogger.w("[DataOverSound] RS decode failed — uncorrectable errors")
    }
    return decoded
}

