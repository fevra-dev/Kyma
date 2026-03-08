package com.sonicvault.app.data.solana

import com.sonicvault.app.logging.SonicVaultLogger
import io.github.novacrypto.base58.Base58
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Acoustic Presence Oracle certificate — 208 bytes.
 *
 * Layout (Area 16A spec):
 * - eventId: 8 bytes (BIG_ENDIAN long)
 * - claimerPubkey: 32 bytes
 * - decodeTimestampMs: 8 bytes (BIG_ENDIAN long)
 * - eventPubkey: 32 bytes
 * - eventSig: 64 bytes (organizer signs bytes 0–79)
 * - claimerSig: 64 bytes (claimer signs bytes 0–79)
 */
data class AcousticCertificate(
    val eventId: ByteArray,
    val claimerPubkey: ByteArray,
    val decodeTimestampMs: Long,
    val eventPubkey: ByteArray,
    val eventSig: ByteArray,
    val claimerSig: ByteArray
) {
    init {
        require(eventId.size == 8) { "eventId must be 8 bytes" }
        require(claimerPubkey.size == 32) { "claimerPubkey must be 32 bytes" }
        require(eventPubkey.size == 32) { "eventPubkey must be 32 bytes" }
        require(eventSig.size == 64) { "eventSig must be 64 bytes" }
        require(claimerSig.size == 64) { "claimerSig must be 64 bytes" }
    }

    /** Serialize to 208-byte ByteArray. */
    fun serialize(): ByteArray {
        val buf = ByteBuffer.allocate(208).order(ByteOrder.BIG_ENDIAN)
        buf.put(eventId)
        buf.put(claimerPubkey)
        buf.putLong(decodeTimestampMs)
        buf.put(eventPubkey)
        buf.put(eventSig)
        buf.put(claimerSig)
        return buf.array()
    }

    /** Bytes 0–79: preamble signed by both event key and claimer. */
    fun preambleBytes(): ByteArray {
        val buf = ByteBuffer.allocate(80).order(ByteOrder.BIG_ENDIAN)
        buf.put(eventId)
        buf.put(claimerPubkey)
        buf.putLong(decodeTimestampMs)
        buf.put(eventPubkey)
        return buf.array()
    }

    /** Display map for UI (base58 pubkeys, truncated sigs). */
    fun toDisplayMap(): Map<String, String> = mapOf(
        "event_id" to eventId.take(8).joinToString("") { "%02x".format(it) },
        "claimer_pubkey" to Base58.base58Encode(claimerPubkey).take(12) + "…",
        "decode_timestamp_ms" to decodeTimestampMs.toString(),
        "event_pubkey" to Base58.base58Encode(eventPubkey).take(12) + "…",
        "event_sig" to eventSig.take(16).joinToString("") { "%02x".format(it) } + "…",
        "claimer_sig" to claimerSig.take(16).joinToString("") { "%02x".format(it) } + "…"
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AcousticCertificate
        if (!eventId.contentEquals(other.eventId)) return false
        if (!claimerPubkey.contentEquals(other.claimerPubkey)) return false
        if (decodeTimestampMs != other.decodeTimestampMs) return false
        if (!eventPubkey.contentEquals(other.eventPubkey)) return false
        if (!eventSig.contentEquals(other.eventSig)) return false
        if (!claimerSig.contentEquals(other.claimerSig)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = eventId.contentHashCode()
        result = 31 * result + claimerPubkey.contentHashCode()
        result = 31 * result + decodeTimestampMs.hashCode()
        result = 31 * result + eventPubkey.contentHashCode()
        result = 31 * result + eventSig.contentHashCode()
        result = 31 * result + claimerSig.contentHashCode()
        return result
    }

    companion object {
        private const val SIZE = 208

        /** Parse 208-byte payload into AcousticCertificate, or null if invalid. */
        fun parse(bytes: ByteArray): AcousticCertificate? {
            if (bytes.size != SIZE) {
                SonicVaultLogger.w("[AcousticCertificate] invalid size ${bytes.size}, expected $SIZE")
                return null
            }
            return try {
                val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                val eventId = ByteArray(8).also { buf.get(it) }
                val claimerPubkey = ByteArray(32).also { buf.get(it) }
                val decodeTimestampMs = buf.long
                val eventPubkey = ByteArray(32).also { buf.get(it) }
                val eventSig = ByteArray(64).also { buf.get(it) }
                val claimerSig = ByteArray(64).also { buf.get(it) }
                AcousticCertificate(
                    eventId = eventId,
                    claimerPubkey = claimerPubkey,
                    decodeTimestampMs = decodeTimestampMs,
                    eventPubkey = eventPubkey,
                    eventSig = eventSig,
                    claimerSig = claimerSig
                )
            } catch (e: Exception) {
                SonicVaultLogger.e("[AcousticCertificate] parse failed", e)
                null
            }
        }
    }
}
