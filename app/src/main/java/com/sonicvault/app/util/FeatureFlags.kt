package com.sonicvault.app.util

/**
 * Feature flags for optional or experimental features.
 * Used to archive features without removing code.
 */
object FeatureFlags {
    /**
     * Voice biometric enrollment and verification.
     * When false, recovery skips voice challenge and goes straight to fingerprint/biometric decrypt.
     * Settings hides the Voice Unlock entry.
     * Archived for hackathon; set to true to re-enable.
     */
    const val VOICE_BIOMETRIC_ENABLED = false
}
