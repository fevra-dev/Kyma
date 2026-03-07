package com.sonicvault.app.data.crypto

/**
 * Result of biometric authentication for encrypt/decrypt operations.
 *
 * Distinguishes user cancellation from authentication failure so the UI
 * can show actionable messages (e.g. "Try again" vs "Use password").
 */
sealed class BiometricAuthResult<out T> {
    data class Success<T>(val data: T) : BiometricAuthResult<T>()
    data object Cancelled : BiometricAuthResult<Nothing>()
    data class Failed(val message: String) : BiometricAuthResult<Nothing>()
}
