package com.sonicvault.app.data.sound

import com.sonicvault.app.logging.SonicVaultLogger
import java.nio.ByteBuffer

/**
 * Chunk protocol for acoustic transmission of payloads > 140 bytes.
 *
 * Format: [MAGIC 2B][SESSION_ID 2B][SEQ 1B][TOTAL 1B][CRC16 2B][DATA ≤120B]
 * MAGIC: 0xAC5A
 * CRC16: CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF) — standard for chunk integrity.
 * Transmit each chunk 2x; 300ms gap between chunks.
 * Receiver: collect by SESSION_ID, verify CRC16, reassemble.
 *
 * Note: Keep current layout (SESSION_ID before SEQ/TOTAL) for compatibility with
 * AcousticChunkReceiver and AcousticTransmitter. Generated format (TOTAL before INDEX)
 * would require coordinated updates across the SonicSafe flow.
 */
object AcousticChunker {

    const val MAGIC = 0xAC5A
    private const val MAGIC_BYTES = 2
    private const val SESSION_ID_BYTES = 2
    private const val SEQ_BYTES = 1
    private const val TOTAL_BYTES = 1
    private const val CRC_BYTES = 2
    private const val HEADER_SIZE = MAGIC_BYTES + SESSION_ID_BYTES + SEQ_BYTES + TOTAL_BYTES + CRC_BYTES
    const val MAX_DATA_PER_CHUNK = 120
    const val CHUNK_SIZE = HEADER_SIZE + MAX_DATA_PER_CHUNK

    /** Gap between chunk transmissions in ms — tightened for ULTRASONIC_FASTEST (3 frames/symbol). */
    const val GAP_BETWEEN_CHUNKS_MS = 200L

    /** Max payload size for acoustic relay. Larger TX show "Transaction too large" instead of transmitting. */
    const val MAX_ACOUSTIC_TX_BYTES = 600

    /**
     * Splits payload into chunks. Each chunk has header + up to 120 bytes data.
     *
     * @param payload raw bytes to transmit
     * @param sessionId 16-bit session identifier
     * @return List of chunk byte arrays
     */
    fun chunk(payload: ByteArray, sessionId: Int): List<ByteArray> {
        val total = (payload.size + MAX_DATA_PER_CHUNK - 1) / MAX_DATA_PER_CHUNK
        val chunks = mutableListOf<ByteArray>()
        for (seq in 0 until total) {
            val start = seq * MAX_DATA_PER_CHUNK
            val end = minOf(start + MAX_DATA_PER_CHUNK, payload.size)
            val data = payload.copyOfRange(start, end)
            chunks.add(buildChunk(sessionId, seq.toByte(), total.toByte(), data))
        }
        SonicVaultLogger.d("[AcousticChunker] chunked ${payload.size}B into ${chunks.size} chunks")
        return chunks
    }

    private fun buildChunk(sessionId: Int, seq: Byte, total: Byte, data: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_SIZE + data.size)
        buf.putShort(MAGIC.toShort())
        buf.putShort(sessionId.toShort())
        buf.put(seq)
        buf.put(total)
        val crc = crc16(data)
        buf.putShort(crc.toShort())
        buf.put(data)
        return buf.array()
    }

    /**
     * Parses chunk header only (for NACK when CRC fails).
     * Call when ggwave decoded bytes have MAGIC but CRC validation fails.
     *
     * @return Triple(sessionId, seq, total) or null if header too short
     */
    fun parseChunkHeader(bytes: ByteArray): Triple<Int, Int, Int>? {
        if (bytes.size < HEADER_SIZE) return null
        val buf = ByteBuffer.wrap(bytes)
        val magic = buf.short.toInt() and 0xFFFF
        if (magic != MAGIC) return null
        val sessionId = buf.short.toInt() and 0xFFFF
        val seq = buf.get().toInt() and 0xFF
        val total = buf.get().toInt() and 0xFF
        return Triple(sessionId, seq, total)
    }

    /**
     * Parses a chunk and validates structure.
     *
     * @return ChunkData or null if invalid
     */
    fun parseChunk(bytes: ByteArray): ChunkData? {
        if (bytes.size < HEADER_SIZE) return null
        val buf = ByteBuffer.wrap(bytes)
        val magic = buf.short.toInt() and 0xFFFF
        if (magic != MAGIC) return null
        val sessionId = buf.short.toInt() and 0xFFFF
        val seq = buf.get()
        val total = buf.get()
        val crc = buf.short.toInt() and 0xFFFF
        val data = ByteArray(bytes.size - HEADER_SIZE)
        buf.get(data)
        if (crc16(data) != crc) {
            SonicVaultLogger.w("[AcousticChunker] CRC mismatch session=$sessionId seq=$seq")
            return null
        }
        return ChunkData(sessionId, seq.toInt() and 0xFF, total.toInt() and 0xFF, data)
    }

    /**
     * Reassembles chunks into payload. Chunks must be in order.
     */
    fun reassemble(chunks: List<ChunkData>): ByteArray? {
        if (chunks.isEmpty()) return null
        val sorted = chunks.sortedBy { it.seq }
        val total = chunks.first().total
        if (sorted.size != total) return null
        for (i in sorted.indices) {
            if (sorted[i].seq != i) return null
        }
        return sorted.flatMap { it.data.toList() }.toByteArray()
    }

    /**
     * CRC-16/CCITT-FALSE: polynomial 0x1021, initial value 0xFFFF, no reflection.
     * Standard for chunk integrity; previous CRC32 & 0xFFFF was non-standard.
     */
    private fun crc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (byte in data) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc
    }

    data class ChunkData(val sessionId: Int, val seq: Int, val total: Int, val data: ByteArray)
}
