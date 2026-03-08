package com.sonicvault.app.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Auto-lock manager that clears sensitive app state after inactivity.
 *
 * Monitors app lifecycle via ProcessLifecycleOwner:
 * - When the app goes to background, starts an inactivity timer.
 * - When the timer expires, sets [isLocked] to true.
 * - UI layer observes [isLocked] to navigate back to home and clear sensitive state.
 * - Any user interaction (screen touch, navigation) calls [recordInteraction] to reset the timer.
 *
 * Default timeout: 5 minutes (configurable).
 */
object AutoLockManager : DefaultLifecycleObserver {

    /** Default auto-lock timeout in milliseconds (5 minutes). */
    private const val DEFAULT_TIMEOUT_MS = 5 * 60 * 1000L

    /** Minimum allowed timeout (1 minute). */
    private const val MIN_TIMEOUT_MS = 60_000L

    /** Configurable timeout — private set to prevent runtime tampering via Frida/Xposed. */
    var timeoutMs: Long = DEFAULT_TIMEOUT_MS
        private set

    /** Update timeout; enforces minimum of [MIN_TIMEOUT_MS]. */
    fun configure(timeoutMs: Long) {
        this.timeoutMs = timeoutMs.coerceAtLeast(MIN_TIMEOUT_MS)
    }

    /** Timestamp of last user interaction. */
    @Volatile
    private var lastInteractionMs: Long = System.currentTimeMillis()

    /** Timestamp when app went to background; 0 if in foreground. */
    @Volatile
    private var backgroundedAtMs: Long = 0L

    /** True when the app should be locked (sensitive state cleared). */
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    /** Whether auto-lock is enabled — private set to prevent runtime tampering. */
    var enabled: Boolean = true
        private set

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /**
     * Initialize: register with ProcessLifecycleOwner.
     * Call once from Application.onCreate().
     */
    fun init() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        SonicVaultLogger.d("[AutoLock] initialized timeout=${timeoutMs}ms")
    }

    /**
     * Record a user interaction to reset the inactivity timer.
     * Call from touch events, navigation actions, or any user-initiated action.
     */
    fun recordInteraction() {
        lastInteractionMs = System.currentTimeMillis()
    }

    /** Called when the app returns to foreground. */
    override fun onStart(owner: LifecycleOwner) {
        if (!enabled) return
        val bgTime = backgroundedAtMs
        if (bgTime > 0) {
            val elapsed = System.currentTimeMillis() - bgTime
            if (elapsed >= timeoutMs) {
                SonicVaultLogger.w("[AutoLock] locked after ${elapsed / 1000}s in background")
                _isLocked.value = true
            }
        }
        backgroundedAtMs = 0L
        lastInteractionMs = System.currentTimeMillis()
    }

    /** Called when the app goes to background. */
    override fun onStop(owner: LifecycleOwner) {
        if (!enabled) return
        backgroundedAtMs = System.currentTimeMillis()
    }

    /**
     * Check if inactivity timeout has been exceeded while in foreground.
     * Call periodically (e.g. from a coroutine in the nav host) to enforce
     * foreground inactivity lock.
     */
    fun checkForegroundTimeout() {
        if (!enabled) return
        val elapsed = System.currentTimeMillis() - lastInteractionMs
        if (elapsed >= timeoutMs && !_isLocked.value) {
            SonicVaultLogger.w("[AutoLock] locked after ${elapsed / 1000}s foreground inactivity")
            _isLocked.value = true
        }
    }

    /** Reset lock state (call after successful re-authentication). */
    fun unlock() {
        _isLocked.value = false
        lastInteractionMs = System.currentTimeMillis()
    }
}
