package com.sonicvault.app.data.geolock

import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.wipe
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor

/**
 * GPS-based conditional encryption for geolock feature.
 * ARCHIVED: See archive/geolock_timelock/README.md
 */
object GeoKeyDerivation {

    private const val GRID_PRECISION = 0.001
    private const val HKDF_INFO = "sonicvault-geolock-v1"

    fun quantize(latitude: Double, longitude: Double): Pair<Double, Double> {
        val qLat = floor(latitude / GRID_PRECISION) * GRID_PRECISION
        val qLon = floor(longitude / GRID_PRECISION) * GRID_PRECISION
        return qLat to qLon
    }

    fun deriveGeoKey(latitude: Double, longitude: Double): ByteArray {
        val (qLat, qLon) = quantize(latitude, longitude)
        val ikm = "geo:$qLat:$qLon".toByteArray(Charsets.UTF_8)
        val prk = hmacSha256(ByteArray(32), ikm)
        val info = HKDF_INFO.toByteArray(Charsets.UTF_8)
        val okm = hmacSha256(prk, info + byteArrayOf(0x01))
        SonicVaultLogger.i("[GeoKey] derived geo-key from ${qLat},${qLon}")
        prk.wipe()
        return okm
    }

    fun locationCommitment(latitude: Double, longitude: Double): ByteArray {
        val (qLat, qLon) = quantize(latitude, longitude)
        val input = "commitment:$qLat:$qLon".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(input)
    }

    fun verifyCommitment(
        latitude: Double,
        longitude: Double,
        storedCommitment: ByteArray
    ): Boolean {
        val computed = locationCommitment(latitude, longitude)
        return computed.contentEquals(storedCommitment)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
