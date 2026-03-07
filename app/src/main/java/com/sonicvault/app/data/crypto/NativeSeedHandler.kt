package com.sonicvault.app.data.crypto

/**
 * JNI bridge to native secure memory wiping routines.
 *
 * JVM cannot guarantee that array.fill(0) won't be optimized away by the JIT
 * or that GC won't retain copies of sensitive data. Native code uses volatile
 * pointer writes (equivalent to C11 memset_s / POSIX explicit_bzero) to ensure
 * seed phrases, passwords, and decrypted plaintext are truly zeroed.
 *
 * Usage: Call these instead of Kotlin fill() in finally blocks for sensitive data.
 */
object NativeSeedHandler {

    init {
        System.loadLibrary("secure_crypto_jni")
    }

    /**
     * Wipes a ByteArray in native memory with volatile writes.
     * Guaranteed not optimized away by the compiler.
     * @param array Sensitive byte array (seed phrase bytes, encryption keys, plaintext).
     */
    @JvmStatic
    external fun secureWipe(array: ByteArray)

    /**
     * Wipes a ShortArray in native memory (PCM audio samples with steganographic data).
     * @param array Audio samples that may contain embedded seed data.
     */
    @JvmStatic
    external fun secureWipeShort(array: ShortArray)

    /**
     * Wipes a CharArray in native memory (passwords, passphrases).
     * @param array Password or passphrase characters.
     */
    @JvmStatic
    external fun secureWipeChar(array: CharArray)
}
