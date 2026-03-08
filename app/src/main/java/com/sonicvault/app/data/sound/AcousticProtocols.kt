package com.sonicvault.app.data.sound

import com.sonicvault.app.domain.model.Protocol

/**
 * Canonical protocol configuration for ggwave.
 * Maps SonicVault [Protocol] + useAudibleFast to ggwave protocol ID.
 *
 * Protocol IDs verified against vendored ggwave.h (ggwave.h enum order):
 *   AUDIBLE_NORMAL=0, AUDIBLE_FAST=1, AUDIBLE_FASTEST=2,
 *   ULTRASOUND_NORMAL=3, ULTRASOUND_FAST=4, ULTRASOUND_FASTEST=5.
 * DSS is a separate init flag in GgwaveNative.init(useDss), not a protocol ID.
 *
 * IMPORTANT: If ggwave library is updated, re-verify these values against
 * the new ggwave.h. Constants may shift in major releases.
 */
object AcousticProtocols {

    // ─── ggwave protocol IDs (match ggwave.h enum order) ─────────────────────
    const val AUDIBLE_NORMAL = 0
    const val AUDIBLE_FAST = 1
    const val AUDIBLE_FASTEST = 2
    const val ULTRASONIC_NORMAL = 3
    const val ULTRASONIC_FAST = 4
    const val ULTRASONIC_FASTEST = 5

    // ─── Defaults ───────────────────────────────────────────────────────────
    /** Used for all SonicVault ultrasonic transmissions — 3 frames/symbol for max throughput. */
    const val DEFAULT_ULTRASONIC: Int = ULTRASONIC_FASTEST

    /** Reliable fallback for noisy environments — 9 frames/symbol, ~3x slower but robust. */
    const val RELIABLE_FALLBACK: Int = ULTRASONIC_NORMAL

    /** Fallback for devices that cannot reproduce near-ultrasonic (15+ kHz) reliably. */
    const val DEFAULT_AUDIBLE_FALLBACK: Int = AUDIBLE_NORMAL

    /** Default volume (0–100). High volume → speaker distortion above 18 kHz. */
    const val DEFAULT_VOLUME: Int = 50

    /** Maximum payload bytes per single ggwave frame. Verified empirically. */
    val MAX_PAYLOAD_BYTES: Map<Int, Int> = mapOf(
        AUDIBLE_NORMAL to 140,
        AUDIBLE_FAST to 140,
        AUDIBLE_FASTEST to 140,
        ULTRASONIC_NORMAL to 140,
        ULTRASONIC_FAST to 140,
        ULTRASONIC_FASTEST to 140
    )

    /**
     * Returns true if the given protocol ID is in the ultrasonic range.
     * Ultrasonic protocols typically use DSS when init flag is set.
     */
    fun isDssProtocol(protocolId: Int): Boolean =
        protocolId in ULTRASONIC_NORMAL..ULTRASONIC_FASTEST

    /** Human-readable name for logging. */
    fun protocolName(protocolId: Int): String = when (protocolId) {
        AUDIBLE_NORMAL -> "Audible Normal"
        AUDIBLE_FAST -> "Audible Fast"
        AUDIBLE_FASTEST -> "Audible Fastest"
        ULTRASONIC_NORMAL -> "Ultrasonic Normal"
        ULTRASONIC_FAST -> "Ultrasonic Fast"
        ULTRASONIC_FASTEST -> "Ultrasonic Fastest"
        else -> "Unknown($protocolId)"
    }

    /**
     * Maps SonicVault [Protocol] to ggwave protocol ID.
     *
     * @param protocol AUDIBLE or ULTRASONIC
     * @param useAudibleFast when true and protocol is AUDIBLE, use AUDIBLE_FAST
     */
    fun toGgwaveProtocol(protocol: Protocol, useAudibleFast: Boolean = false): Int = when (protocol) {
        Protocol.AUDIBLE -> if (useAudibleFast) AUDIBLE_FAST else AUDIBLE_NORMAL
        Protocol.ULTRASONIC -> DEFAULT_ULTRASONIC
    }
}
