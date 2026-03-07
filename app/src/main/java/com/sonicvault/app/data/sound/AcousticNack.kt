package com.sonicvault.app.data.sound

/**
 * NACK packet for SonicSafe: cold signals decode/CRC failure, hot retransmits.
 *
 * 10-byte format (enables targeted retransmit of failed chunk):
 *   [MAGIC 2B][SESSION_ID 2B][REASON 1B][CHUNK_INDEX 1B][PADDING 4B]
 * MAGIC: 0xACFF
 *
 * Hot device receives NACK, retransmits only the failed chunk (chunkIndex).
 * Backward compatible: parse() accepts both 4-byte (legacy) and 10-byte formats.
 */
object AcousticNack {

    private const val NACK_MAGIC = 0xACFF
    const val NACK_SIZE_10 = 10

    /** CRC validation failed on chunk data. */
    const val REASON_CRC_FAIL: Byte = 0x01

    /** ggwave decode failed (e.g. RS decode error). */
    const val REASON_DECODE_FAIL: Byte = 0x02

    /**
     * Builds 10-byte NACK packet for targeted retransmit.
     *
     * @param sessionId 16-bit session identifier
     * @param reason REASON_CRC_FAIL or REASON_DECODE_FAIL
     * @param chunkIndex 0-based index of the failed chunk
     */
    fun build(sessionId: Int, reason: Byte, chunkIndex: Int): ByteArray {
        return byteArrayOf(
            (NACK_MAGIC shr 8).toByte(),
            (NACK_MAGIC and 0xFF).toByte(),
            (sessionId shr 8).toByte(),
            (sessionId and 0xFF).toByte(),
            reason,
            chunkIndex.toByte(),
            0, 0, 0, 0  // padding
        )
    }

    /**
     * Legacy 4-byte NACK (session only). Use build(sessionId, reason, chunkIndex) for 10-byte.
     */
    fun buildLegacy(sessionId: Int): ByteArray {
        return byteArrayOf(
            (NACK_MAGIC shr 8).toByte(),
            (NACK_MAGIC and 0xFF).toByte(),
            (sessionId shr 8).toByte(),
            (sessionId and 0xFF).toByte()
        )
    }

    /**
     * Returns true if [bytes] is a valid NACK (4-byte or 10-byte).
     */
    fun isNack(bytes: ByteArray): Boolean =
        bytes.size >= 4 && ((bytes[0].toInt() and 0xFF) shl 8 or (bytes[1].toInt() and 0xFF)) == NACK_MAGIC

    /**
     * Parses NACK packet. Returns sessionId or null if not a valid NACK.
     */
    fun parse(bytes: ByteArray): Int? {
        if (bytes.size < 4) return null
        val magic = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        if (magic != NACK_MAGIC) return null
        return ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
    }

    /**
     * Extracts chunk index from 10-byte NACK. Returns 0 for 4-byte legacy NACK.
     */
    fun chunkIndex(bytes: ByteArray): Int =
        if (bytes.size >= 6) bytes[5].toInt() and 0xFF else 0

    /**
     * Extracts reason from 10-byte NACK. Returns REASON_DECODE_FAIL for 4-byte legacy.
     */
    fun reason(bytes: ByteArray): Byte =
        if (bytes.size >= 5) bytes[4] else REASON_DECODE_FAIL
}
