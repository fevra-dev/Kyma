package com.sonicvault.app.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists onboarding completion state.
 * Uses SharedPreferences for simplicity; no sensitive data.
 */
class OnboardingPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    var hasSeenOnboarding: Boolean
        get() = prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, value).apply()

    companion object {
        private const val PREFS_NAME = "sonicvault_onboarding"
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
    }
}

/**
 * User preferences for app behavior (protocol selection, layout, etc.).
 * Protocol choice is direct — no transmission mode indirection layer.
 */
class UserPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /** Last protocol used for transmit. Persisted across sessions. */
    var lastProtocolTransmit: String
        get() = prefs.getString(KEY_LAST_PROTOCOL_TRANSMIT, null) ?: "ULTRASONIC"
        set(value) = prefs.edit().putString(KEY_LAST_PROTOCOL_TRANSMIT, value).apply()

    /** Last protocol used for receive. Persisted across sessions. */
    var lastProtocolReceive: String
        get() = prefs.getString(KEY_LAST_PROTOCOL_RECEIVE, null) ?: "ULTRASONIC"
        set(value) = prefs.edit().putString(KEY_LAST_PROTOCOL_RECEIVE, value).apply()

    /** Compact layout: tighter spacing for small screens. Rams: thorough. */
    var compactMode: Boolean
        get() = prefs.getBoolean(KEY_COMPACT_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPACT_MODE, value).apply()

    /**
     * Anti-fingerprint: randomize ultrasonic spectral envelope per session to reduce
     * device identification from spectrograms. Default ON after Phase 3 validation.
     */
    var useAntiFingerprint: Boolean
        get() = prefs.getBoolean(KEY_USE_ANTI_FINGERPRINT, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_ANTI_FINGERPRINT, value).apply()

    /**
     * Last successful backup timestamp (System.currentTimeMillis).
     * Used for "Last backup: X ago" stickiness signal on home screen.
     */
    var lastBackupTimestamp: Long
        get() = prefs.getLong(KEY_LAST_BACKUP_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP_TIMESTAMP, value).apply()

    /**
     * Total number of successful backups. Incremented on each backup.
     */
    var backupCount: Int
        get() = prefs.getInt(KEY_BACKUP_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_BACKUP_COUNT, value).apply()

    companion object {
        private const val PREFS_NAME = "sonicvault_user"
        private const val KEY_LAST_PROTOCOL_TRANSMIT = "last_protocol_transmit"
        private const val KEY_LAST_PROTOCOL_RECEIVE = "last_protocol_receive"
        private const val KEY_COMPACT_MODE = "compact_mode"
        private const val KEY_USE_ANTI_FINGERPRINT = "use_anti_fingerprint"
        private const val KEY_LAST_BACKUP_TIMESTAMP = "last_backup_timestamp"
        private const val KEY_BACKUP_COUNT = "backup_count"
    }
}
