package com.sonicvault.app.data.receive

import com.sonicvault.app.logging.SonicVaultLogger

/**
 * Strict framing validation gate for received bytes.
 *
 * Enforces minimum payload size and version byte before any downstream handling.
 * Prevents injection via unknown protocol versions and buffer overflow via oversized payloads.
 *
 * SonicVault payload format: [version:1][binding:32][iv:12][ciphertext+tag][...] — min 94 bytes.
 * Version 0x01 = v1, 0x02 = v2. Other formats (Dead Drop DDRP/SVDD, Solana Pay URI) use
 * format-specific validation elsewhere.
 */
object FrameValidator {

    /** Minimum size for SonicVault payload (v1: 1+32+12+16+1+32 = 94). */
    private const val MIN_PAYLOAD_SIZE = 94

    private val VALID_VERSIONS = setOf(0x01.toByte(), 0x02.toByte())

    /**
     * Validates raw bytes that may be SonicVault payload format.
     *
     * @param raw bytes received from ggwave (possibly after RS decode)
     * @return [FrameValidationResult.Accepted] if size >= 94 and version is 0x01 or 0x02
     */
    fun validate(raw: ByteArray): FrameValidationResult {
        if (raw.size < MIN_PAYLOAD_SIZE) {
            SonicVaultLogger.d("[FrameValidator] DISCARD: payload too short (${raw.size} bytes, min $MIN_PAYLOAD_SIZE)")
            return FrameValidationResult.Rejected(RejectionReason.TOO_SHORT)
        }
        val version = raw[0]
        if (version !in VALID_VERSIONS) {
            SonicVaultLogger.d("[FrameValidator] DISCARD: unknown version byte 0x${version.toString(16).padStart(2, '0')}")
            return FrameValidationResult.Rejected(RejectionReason.INVALID_VERSION)
        }
        return FrameValidationResult.Accepted(raw)
    }

    sealed class FrameValidationResult {
        data class Accepted(val payload: ByteArray) : FrameValidationResult()
        data class Rejected(val reason: RejectionReason) : FrameValidationResult()
    }

    enum class RejectionReason {
        TOO_SHORT,
        INVALID_VERSION
    }
}
