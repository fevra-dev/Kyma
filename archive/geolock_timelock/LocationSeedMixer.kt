package com.sonicvault.app.data.entropy

import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.wipe
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor

/**
 * Mixes GPS coordinates into entropy for location-derived seed generation.
 * ARCHIVED: See archive/geolock_timelock/README.md
 * Used by archived SoundSeed feature.
 */
object LocationSeedMixer {

    private const val GRID_PRECISION = 0.001

    fun quantize(latitude: Double, longitude: Double): Pair<Double, Double> {
        val qLat = floor(latitude / GRID_PRECISION) * GRID_PRECISION
        val qLon = floor(longitude / GRID_PRECISION) * GRID_PRECISION
        return qLat to qLon
    }

    fun hashLocation(latitude: Double, longitude: Double): ByteArray {
        val (qLat, qLon) = quantize(latitude, longitude)
        val locationStr = "sonicvault:location:$qLat:$qLon"
        return MessageDigest.getInstance("SHA-256").digest(
            locationStr.toByteArray(Charsets.UTF_8)
        )
    }

    fun mixLocation(audioEntropy: ByteArray, latitude: Double, longitude: Double): ByteArray {
        SonicVaultLogger.i("[LocationMixer] mixing location (quantized ~100m grid)")
        var locationKey: ByteArray? = null
        try {
            locationKey = hashLocation(latitude, longitude)
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(locationKey, "HmacSHA256"))
            val mixed = mac.doFinal(audioEntropy)
            return mixed.copyOfRange(0, audioEntropy.size)
        } catch (e: Exception) {
            SonicVaultLogger.e("[LocationMixer] mixing failed, using audio entropy only", e)
            return audioEntropy
        } finally {
            locationKey?.wipe()
        }
    }
}
