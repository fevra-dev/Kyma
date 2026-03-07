package com.sonicvault.app.data.ultrasonic

import com.sonicvault.app.logging.SonicVaultLogger

/**
 * Stub for ultrasonic attack monitoring (Tier 2 / v1.5).
 *
 * During SonicVault sessions, run a background frequency scanner. If unexpected
 * ultrasonic signal is detected from a non-SonicVault source, show a warning:
 * "Unusual ultrasonic activity detected nearby — verify your environment."
 *
 * REAL IMPLEMENTATION: Vendor fhstp/SoniControl (LGPL) or integrate its detection
 * module. SoniControl is an ultrasonic privacy firewall — detects hostile ultrasonic
 * (e.g. DolphinAttack-class) activity.
 *
 * @see <a href="https://github.com/fhstp/SoniControl">SoniControl on GitHub</a>
 */
object UltrasonicGuard {

    /**
     * Start monitoring for unexpected ultrasonic during a session.
     * Stub: no-op. Real impl would start SoniControl scanner.
     *
     * @param onWarning Callback when hostile ultrasonic detected (unused in stub).
     */
    @Suppress("UNUSED_PARAMETER")
    fun startMonitoring(onWarning: () -> Unit) {
        SonicVaultLogger.d("[UltrasonicGuard] stub: monitoring not active (vendor SoniControl for real impl)")
        // Real: SoniControl.start() → onDetection → onWarning()
    }

    /**
     * Stop monitoring when session ends.
     */
    fun stopMonitoring() {
        SonicVaultLogger.d("[UltrasonicGuard] stub: stopMonitoring no-op")
    }
}
