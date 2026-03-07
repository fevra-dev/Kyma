package com.sonicvault.app.data.geolock

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.sonicvault.app.logging.SonicVaultLogger

/**
 * Detects GPS spoofing to protect geolock integrity.
 * ARCHIVED: See archive/geolock_timelock/README.md
 */
object GpsSpoofingDetector {

    data class SpoofCheckResult(
        val isSuspicious: Boolean,
        val confidence: Int,
        val signals: List<String>
    )

    fun check(context: Context, location: Location): SpoofCheckResult {
        val signals = mutableListOf<String>()
        var suspicionScore = 0
        if (location.isFromMockProvider) {
            signals.add("mock_provider")
            suspicionScore += 50
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (location.isMock) {
                signals.add("mock_location_api31")
                suspicionScore += 50
            }
        }
        if (location.hasAccuracy() && location.accuracy < 1f) {
            signals.add("impossible_accuracy=${location.accuracy}m")
            suspicionScore += 20
        }
        if (location.hasAltitude()) {
            if (location.altitude < -500 || location.altitude > 9000) {
                signals.add("impossible_altitude=${location.altitude}m")
                suspicionScore += 15
            }
        }
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                signals.add("gps_disabled")
                suspicionScore += 10
            }
        } catch (e: Exception) {
            SonicVaultLogger.d("[GpsSpoofDetector] location manager check failed: ${e.message}")
        }
        val confidence = (100 - suspicionScore).coerceIn(0, 100)
        val isSuspicious = suspicionScore >= 30
        if (signals.isNotEmpty()) {
            SonicVaultLogger.w("[GpsSpoofDetector] signals=${signals.joinToString(",")} confidence=$confidence")
        } else {
            SonicVaultLogger.d("[GpsSpoofDetector] no spoofing signals, confidence=$confidence")
        }
        return SpoofCheckResult(isSuspicious, confidence, signals)
    }
}
