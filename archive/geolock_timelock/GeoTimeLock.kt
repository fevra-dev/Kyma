package com.sonicvault.app.data.geolock

import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.wipe
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Combined geolock + timelock encryption.
 * ARCHIVED: See archive/geolock_timelock/README.md
 */
object GeoTimeLock {

    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val HKDF_INFO = "geotimelock-v1"

    enum class UnlockFailure {
        WRONG_LOCATION,
        TOO_EARLY,
        BOTH_FAILED
    }

    fun deriveTimeKey(unlockTimestamp: Long): ByteArray {
        val dayEpoch = unlockTimestamp / 86400
        val ikm = "time:$dayEpoch".toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    fun combineKeys(geoKey: ByteArray, timeKey: ByteArray): ByteArray {
        require(geoKey.size == 32 && timeKey.size == 32) { "Keys must be 32 bytes" }
        val xored = ByteArray(32) { i ->
            (geoKey[i].toInt() and 0xFF xor (timeKey[i].toInt() and 0xFF)).toByte()
        }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(xored, "HmacSHA256"))
        val combined = mac.doFinal(HKDF_INFO.toByteArray(Charsets.UTF_8))
        xored.wipe()
        return combined
    }

    fun encrypt(
        plaintext: ByteArray,
        latitude: Double,
        longitude: Double,
        unlockTimestamp: Long
    ): ByteArray? {
        SonicVaultLogger.i("[GeoTimeLock] encrypting ${plaintext.size} bytes")
        var geoKey: ByteArray? = null
        var timeKey: ByteArray? = null
        var combinedKey: ByteArray? = null
        try {
            geoKey = GeoKeyDerivation.deriveGeoKey(latitude, longitude)
            timeKey = deriveTimeKey(unlockTimestamp)
            combinedKey = combineKeys(geoKey, timeKey)
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(combinedKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(plaintext)
            val result = ByteArray(iv.size + ciphertext.size)
            iv.copyInto(result, 0)
            ciphertext.copyInto(result, iv.size)
            SonicVaultLogger.i("[GeoTimeLock] encrypted: ${result.size} bytes")
            return result
        } catch (e: Exception) {
            SonicVaultLogger.e("[GeoTimeLock] encryption failed", e)
            return null
        } finally {
            geoKey?.wipe()
            timeKey?.wipe()
            combinedKey?.wipe()
        }
    }

    fun decrypt(
        encryptedData: ByteArray,
        latitude: Double,
        longitude: Double,
        unlockTimestamp: Long,
        currentTime: Long
    ): Pair<ByteArray?, UnlockFailure?> {
        SonicVaultLogger.i("[GeoTimeLock] decrypting ${encryptedData.size} bytes")
        val timePassed = currentTime >= unlockTimestamp
        if (!timePassed) {
            SonicVaultLogger.i("[GeoTimeLock] time check failed: current=$currentTime unlock=$unlockTimestamp")
            return null to UnlockFailure.TOO_EARLY
        }
        var geoKey: ByteArray? = null
        var timeKey: ByteArray? = null
        var combinedKey: ByteArray? = null
        try {
            geoKey = GeoKeyDerivation.deriveGeoKey(latitude, longitude)
            timeKey = deriveTimeKey(unlockTimestamp)
            combinedKey = combineKeys(geoKey, timeKey)
            val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(combinedKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val plaintext = cipher.doFinal(ciphertext)
            SonicVaultLogger.i("[GeoTimeLock] decrypted: ${plaintext.size} bytes")
            return plaintext to null
        } catch (e: javax.crypto.AEADBadTagException) {
            SonicVaultLogger.w("[GeoTimeLock] decryption failed — wrong location or corrupted")
            return null to UnlockFailure.WRONG_LOCATION
        } catch (e: Exception) {
            SonicVaultLogger.e("[GeoTimeLock] decryption failed", e)
            return null to UnlockFailure.WRONG_LOCATION
        } finally {
            geoKey?.wipe()
            timeKey?.wipe()
            combinedKey?.wipe()
        }
    }
}
