package com.sonicvault.app.data.crypto

import com.sonicvault.app.logging.SonicVaultLogger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Short Authentication String (SAS) for MITM detection.
 *
 * After successful Dead Drop decode, both sender and receiver display a 6-char
 * string. User verbally confirms match before trusting the transfer.
 * Inspired by ZRTP (RFC 6189).
 *
 * Algorithm: Crockford Base32(HMAC-SHA256(keyMaterial, "SONICVAULT-SAS")[0:4])
 * → 4 bytes = 32 bits → 6 Crockford Base32 chars (last group padded).
 *
 * v1.5: keyMaterial = device binding hash (device identity confirmation).
 * v2.0: keyMaterial = X25519 session key (full per-session MITM protection).
 */
object SasGenerator {

    /** Crockford Base32: excludes I, L, O, U to avoid visual confusion when read aloud. */
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /**
     * Generate a 6-character SAS from [keyMaterial].
     *
     * @param keyMaterial Device binding hash (v1.5) or session key (v2.0).
     * @param context Domain separation. Default "SONICVAULT-SAS".
     */
    fun generate(
        keyMaterial: ByteArray,
        context: String = "SONICVAULT-SAS"
    ): String {
        val mac = hmacSha256(keyMaterial, context.toByteArray(Charsets.UTF_8))
        // 4 bytes = 32 bits → 6 Crockford Base32 chars (5 bits each, last 2 bits padded)
        val b0 = mac[0].toInt() and 0xFF
        val b1 = mac[1].toInt() and 0xFF
        val b2 = mac[2].toInt() and 0xFF
        val b3 = mac[3].toInt() and 0xFF

        val bits = (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        val chars = charArrayOf(
            ALPHABET[(bits shr 27) and 0x1F],
            ALPHABET[(bits shr 22) and 0x1F],
            ALPHABET[(bits shr 17) and 0x1F],
            ALPHABET[(bits shr 12) and 0x1F],
            ALPHABET[(bits shr 7) and 0x1F],
            ALPHABET[(bits shr 2) and 0x1F]
        )
        val sas = String(chars)
        SonicVaultLogger.i("[SasGenerator] SAS generated")
        return sas
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
