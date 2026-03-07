package com.sonicvault.app.util

import com.sonicvault.app.data.crypto.NativeSeedHandler

/**
 * Secure wipe of sensitive data using native volatile memset (NDK).
 *
 * Delegates to NativeSeedHandler JNI which uses volatile pointer writes
 * (equivalent to C11 memset_s / POSIX explicit_bzero) — guaranteed not
 * optimized away by the compiler or JIT, unlike JVM fill() calls.
 *
 * Falls back to JVM fill() if native library fails to load.
 * Use in finally blocks to minimize exposure window. OWASP MASVS compliant.
 */
fun ByteArray.wipe() {
    try {
        NativeSeedHandler.secureWipe(this)
    } catch (_: UnsatisfiedLinkError) {
        /* Fallback: JVM wipe if native lib not loaded (e.g. unit tests). */
        fill(0xFF.toByte())
        fill(0x00)
    }
}

/**
 * Secure wipe of sensitive CharArray data (passwords, passphrases).
 * Delegates to native volatile memset for guaranteed wiping.
 */
fun CharArray.wipe() {
    try {
        NativeSeedHandler.secureWipeChar(this)
    } catch (_: UnsatisfiedLinkError) {
        fill('\u0000')
    }
}

/**
 * Secure wipe of PCM audio samples that may contain steganographic data.
 * Delegates to native volatile memset for guaranteed wiping.
 */
fun ShortArray.wipe() {
    try {
        NativeSeedHandler.secureWipeShort(this)
    } catch (_: UnsatisfiedLinkError) {
        fill(0)
    }
}
