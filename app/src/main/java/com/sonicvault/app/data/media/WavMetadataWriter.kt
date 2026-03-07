package com.sonicvault.app.data.media

import com.sonicvault.app.logging.SonicVaultLogger

/**
 * Writes WAV metadata (INFO chunks) for plausible deniability.
 *
 * WAV files support LIST/INFO chunks for metadata. This writer appends
 * INAM (title), IART (artist), IGNR (genre), ICMT (comment) to the
 * WAV byte stream. Music apps that read LIST INFO will display these tags.
 *
 * Note: jaudiotagger provides richer ID3v2-in-WAV support and is preferred
 * when available. This is a zero-dependency fallback for basic tagging.
 */
object WavMetadataWriter {

    /** Predefined genres for the backup flow genre selector. */
    val GENRES = listOf(
        "Ambient",
        "Electronic",
        "Field Recording",
        "Classical",
        "Lo-Fi",
        "Nature Sounds",
        "Meditation",
        "White Noise"
    )

    /** Default track titles for each genre, adding plausible deniability. */
    val DEFAULT_TITLES = mapOf(
        "Ambient" to listOf("Rain on Glass", "Distant Thunder", "Morning Fog", "Ocean Drift"),
        "Electronic" to listOf("Pulse 01", "Synth Layer", "Digital Grain", "Circuit Loop"),
        "Field Recording" to listOf("Park Bench", "Market Sound", "Train Station", "Wind Through Trees"),
        "Classical" to listOf("Nocturne Study", "Adagio in C", "String Sketch", "Piano Fragment"),
        "Lo-Fi" to listOf("Tape Hiss", "Vinyl Crackle", "Study Loop", "Rainy Café"),
        "Nature Sounds" to listOf("Forest Creek", "Birdsong Dawn", "Waves at Dusk", "Mountain Wind"),
        "Meditation" to listOf("Breathing Space", "Still Water", "Inner Calm", "Deep Rest"),
        "White Noise" to listOf("Static", "Fan Sound", "Brown Noise", "Pink Noise")
    )

    /**
     * Builds a WAV LIST/INFO chunk with the given metadata.
     *
     * @return INFO chunk bytes to append before the WAV data chunk,
     *         or empty array if metadata is blank
     */
    fun buildInfoChunk(
        title: String = "",
        artist: String = "",
        genre: String = "",
        comment: String = ""
    ): ByteArray {
        val fields = mutableListOf<Pair<String, String>>()
        if (title.isNotBlank()) fields.add("INAM" to title)
        if (artist.isNotBlank()) fields.add("IART" to artist)
        if (genre.isNotBlank()) fields.add("IGNR" to genre)
        if (comment.isNotBlank()) fields.add("ICMT" to comment)

        if (fields.isEmpty()) return ByteArray(0)

        val chunks = fields.map { (tag, value) ->
            val bytes = value.toByteArray(Charsets.US_ASCII)
            // Each sub-chunk: tag(4) + size(4) + data (null-padded to even)
            val paddedSize = if (bytes.size % 2 == 0) bytes.size + 1 else bytes.size + 2
            val chunk = ByteArray(8 + paddedSize)
            tag.toByteArray(Charsets.US_ASCII).copyInto(chunk, 0)
            writeLE32(chunk, 4, paddedSize)
            bytes.copyInto(chunk, 8)
            // Null terminator is already in the zero-initialized array
            chunk
        }

        val totalData = chunks.sumOf { it.size }
        // LIST chunk: "LIST" + size(4) + "INFO" + sub-chunks
        val result = ByteArray(12 + totalData)
        "LIST".toByteArray(Charsets.US_ASCII).copyInto(result, 0)
        writeLE32(result, 4, 4 + totalData)
        "INFO".toByteArray(Charsets.US_ASCII).copyInto(result, 8)

        var offset = 12
        for (chunk in chunks) {
            chunk.copyInto(result, offset)
            offset += chunk.size
        }

        SonicVaultLogger.i("[WavMetadata] built INFO chunk: ${result.size} bytes, ${fields.size} fields")
        return result
    }

    /**
     * Picks a plausible title for the given genre.
     * Uses a deterministic hash of the seed to select consistently.
     */
    fun pickTitle(genre: String, seedHash: Int = 0): String {
        val titles = DEFAULT_TITLES[genre] ?: DEFAULT_TITLES["Ambient"]!!
        return titles[Math.abs(seedHash) % titles.size]
    }

    /** Writes a 32-bit little-endian integer into [dst] at [offset]. */
    private fun writeLE32(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = (value and 0xFF).toByte()
        dst[offset + 1] = ((value shr 8) and 0xFF).toByte()
        dst[offset + 2] = ((value shr 16) and 0xFF).toByte()
        dst[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
}
