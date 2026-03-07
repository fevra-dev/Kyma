package com.sonicvault.app.data.sound

import com.sonicvault.app.domain.model.Protocol
import com.sonicvault.app.logging.SonicVaultLogger

/**
 * ggwave-based data-over-sound for SonicVault.
 * Replaces custom FSK with ggwave for Reed-Solomon ECC, ultrasonic protocols,
 * and better BER in noisy environments.
 *
 * Uses 48 kHz for ultrasonic compatibility. GgwaveNative chunks payloads > 140 bytes
 * internally. Protocol IDs from [AcousticProtocols]; see ggwave.h for enum reference.
 */
object GgwaveDataOverSound {

    /** ggwave sample rate for ultrasonic; required for 18–20 kHz carrier. */
    const val SAMPLE_RATE = 48000

    /**
     * Encodes payload to PCM using ggwave. Protocol maps to ggwave protocol ID via [AcousticProtocols].
     *
     * @param data payload bytes (device hash + length + iv + ciphertext).
     * @param protocol SonicVault protocol; maps to ggwave.
     * @param useAudibleFast when true and protocol is Audible, use AUDIBLE_FAST (matches ggwave-js demo).
     * @param useDss When true and protocol is Ultrasonic, enables Direct Sequence Spread for ~20% better BER.
     *              Use false for Message/Solana Pay (web receiver compatibility).
     * @param applyFingerprintRandomization when true and protocol is Ultrasonic, randomizes spectral
     *        envelope (17–22 kHz) to reduce device fingerprinting. Default OFF until validated.
     * @return 16-bit mono PCM, or null on failure.
     */
    fun encode(
        data: ByteArray,
        protocol: Protocol = Protocol.ULTRASONIC,
        useAudibleFast: Boolean = false,
        useDss: Boolean = true,
        applyFingerprintRandomization: Boolean = false
    ): ShortArray? {
        SonicVaultLogger.i("[GgwaveEncoder] encode len=${data.size} protocol=$protocol useAudibleFast=$useAudibleFast useDss=$useDss")
        SonicVaultLogger.d("[GgwaveEncoder] pre-init sampleRate=$SAMPLE_RATE")
        if (data.isEmpty()) {
            SonicVaultLogger.e("[GgwaveEncoder] encode failed: empty payload")
            return null
        }
        val protocolId = AcousticProtocols.toGgwaveProtocol(protocol, useAudibleFast)
        val maxBytes = AcousticProtocols.MAX_PAYLOAD_BYTES[protocolId] ?: 140
        if (data.size > maxBytes) {
            SonicVaultLogger.d("[GgwaveEncoder] payload ${data.size}B exceeds single-frame max $maxBytes; native will chunk")
        }
        val dss = useDss && protocol == Protocol.ULTRASONIC
        val instance = GgwaveNative.init(SAMPLE_RATE, dss)
        return try {
            SonicVaultLogger.d("[GgwaveEncoder] init instance=$instance protocol=${AcousticProtocols.protocolName(protocolId)}")
            if (instance < 0) {
                SonicVaultLogger.e("[GgwaveEncoder] encode failed: init failed")
                return null
            }
            SonicVaultLogger.d("[GgwaveEncoder] protocolId=$protocolId calling native encode")
            var samples = GgwaveNative.encode(instance, data, protocolId)
            if (samples != null) {
                val durationMs = (samples.size * 1000L) / SAMPLE_RATE
                SonicVaultLogger.i("[GgwaveEncoder] encoded ${samples.size} samples (~${durationMs}ms)")
                if (applyFingerprintRandomization && protocol == Protocol.ULTRASONIC) {
                    samples = FingerprintRandomizer().randomize(samples)
                }
            } else {
                SonicVaultLogger.e("[GgwaveEncoder] encode failed: native returned null")
            }
            samples
        } finally {
            GgwaveNative.free(instance)
        }
    }

    /**
     * Decodes PCM to payload. Auto-detects ggwave protocol.
     *
     * @param samples 16-bit mono PCM @ 48 kHz.
     * @param sampleRate Sample rate (default 48 kHz).
     * @return Extracted payload, or null if decode failed.
     */
    fun decode(samples: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray? {
        SonicVaultLogger.i("[GgwaveDecoder] decode samples=${samples.size} sampleRate=$sampleRate")
        SonicVaultLogger.d("[GgwaveDecoder] pre-init sampleRate=$sampleRate")
        if (samples.isEmpty()) {
            SonicVaultLogger.d("[GgwaveDecoder] no payload in recording (empty samples)")
            return null
        }
        // Try DSS first (Dead Drop, Sonic Safe, backup acoustic)
        var instance = GgwaveNative.init(sampleRate, useDss = true)
        if (instance >= 0) {
            try {
                val payload = GgwaveNative.decode(instance, samples, sampleRate)
                if (payload != null) {
                    SonicVaultLogger.i("[GgwaveDecoder] decoded ${payload.size} bytes (DSS)")
                    return payload
                }
            } finally {
                GgwaveNative.free(instance)
            }
        }
        // Fallback: non-DSS (Message, Solana Pay, ggwave.ggerganov.com)
        instance = GgwaveNative.init(sampleRate, useDss = false)
        return try {
            if (instance < 0) {
                SonicVaultLogger.e("[GgwaveDecoder] decode failed: init failed")
                return null
            }
            val payload = GgwaveNative.decode(instance, samples, sampleRate)
            if (payload != null) {
                SonicVaultLogger.i("[GgwaveDecoder] decoded ${payload.size} bytes (no DSS)")
            } else {
                SonicVaultLogger.d("[GgwaveDecoder] no payload in recording (decode returned null)")
            }
            payload
        } finally {
            GgwaveNative.free(instance)
        }
    }
}
