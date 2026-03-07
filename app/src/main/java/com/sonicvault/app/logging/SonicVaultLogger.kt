package com.sonicvault.app.logging

import android.util.Log
import com.sonicvault.app.BuildConfig

/**
 * Winston-style logging facade for Kyma.
 *
 * SECURITY PATCH [SVA-004]: Logging safety tiers:
 * - d(): Debug only (BuildConfig.DEBUG); stripped by ProGuard in release.
 * - i(): Info — release builds; NEVER pass sensitive data (seeds, passwords, keys, tokens).
 * - w(): Warning — release builds; for expected failure paths. Redact any user-identifiable data.
 * - e(): Error — release builds; for unexpected exceptions. Stack traces may contain param values
 *         so callers must scrub sensitive args before passing to error messages.
 *
 * Callers MUST NOT log: seed phrases, passwords, encryption keys, plaintext payloads,
 * voice embeddings, clipboard contents, or full file paths containing user-identifiable info.
 *
 * ProGuard strips Log.d(), Log.v(), and Log.i() in release builds.
 */
object SonicVaultLogger {
    private const val TAG = "SonicSeed"

    /** Debug-only; gated by BuildConfig.DEBUG and stripped by ProGuard. */
    fun d(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    /**
     * Info level; visible in release logcat.
     * SECURITY: ProGuard strips Log.i() in release. This is defense-in-depth;
     * callers must still never pass sensitive data.
     */
    fun i(msg: String) {
        Log.i(TAG, msg)
    }

    /** Warning level; for expected failure paths. Redact user-identifiable data. */
    fun w(msg: String) {
        Log.w(TAG, msg)
    }

    fun w(msg: String, t: Throwable) {
        Log.w(TAG, msg, t)
    }

    /**
     * Error level; for unexpected exceptions.
     * SECURITY: Exception messages may contain sensitive data from parameters.
     * Callers should sanitize exception messages before passing.
     */
    fun e(msg: String) {
        Log.e(TAG, msg)
    }

    fun e(msg: String, t: Throwable) {
        Log.e(TAG, msg, t)
    }
}
