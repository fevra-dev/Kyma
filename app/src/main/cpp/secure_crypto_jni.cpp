/**
 * secure_crypto_jni.cpp — Native JNI bridge for guaranteed memory wiping.
 *
 * JVM cannot guarantee that ByteArray.fill(0) won't be optimized away or that
 * GC won't retain copies. This native layer uses volatile writes / explicit_bzero
 * to ensure sensitive data (seed phrases, passwords, decrypted plaintext) is
 * actually zeroed in memory.
 *
 * Functions exposed to Kotlin via NativeSeedHandler.kt:
 *   - secureWipe(ByteArray)       → wipes byte array contents in-place
 *   - secureWipeShort(ShortArray)  → wipes short array (PCM audio samples)
 *   - secureWipeChar(CharArray)    → wipes char array (passwords)
 */
#include <jni.h>
#include <cstring>
#include <cstddef>

#define LOG_TAG "secure_crypto_jni"

/**
 * Volatile memset pattern — guaranteed not optimized away by compiler.
 * Uses volatile pointer to prevent dead-store elimination.
 * Equivalent to C11 memset_s() / POSIX explicit_bzero() semantics.
 */
static void secure_wipe(void* ptr, size_t len) {
    volatile unsigned char* p = (volatile unsigned char*)ptr;
    while (len--) {
        *p++ = 0;
    }
}

extern "C" {

/**
 * Wipes a Java ByteArray in-place using native volatile memset.
 * Called from Kotlin: NativeSeedHandler.secureWipe(byteArray)
 */
JNIEXPORT void JNICALL
Java_com_sonicvault_app_data_crypto_NativeSeedHandler_secureWipe(
    JNIEnv *env, jclass clazz, jbyteArray array) {
    if (array == nullptr) return;

    jsize len = env->GetArrayLength(array);
    if (len <= 0) return;

    /* Get direct pointer to array elements (no copy if possible). */
    jbyte* elements = env->GetByteArrayElements(array, nullptr);
    if (elements == nullptr) return;

    /* Wipe with volatile writes — not optimizable by compiler. */
    secure_wipe(elements, (size_t)len);

    /* Commit changes back to Java array and free native buffer. */
    /* Mode 0: copy back and free the buffer. */
    env->ReleaseByteArrayElements(array, elements, 0);
}

/**
 * Wipes a Java ShortArray in-place (PCM audio samples containing stego data).
 * Called from Kotlin: NativeSeedHandler.secureWipeShort(shortArray)
 */
JNIEXPORT void JNICALL
Java_com_sonicvault_app_data_crypto_NativeSeedHandler_secureWipeShort(
    JNIEnv *env, jclass clazz, jshortArray array) {
    if (array == nullptr) return;

    jsize len = env->GetArrayLength(array);
    if (len <= 0) return;

    jshort* elements = env->GetShortArrayElements(array, nullptr);
    if (elements == nullptr) return;

    secure_wipe(elements, (size_t)len * sizeof(jshort));

    env->ReleaseShortArrayElements(array, elements, 0);
}

/**
 * Wipes a Java CharArray in-place (passwords, passphrases).
 * Called from Kotlin: NativeSeedHandler.secureWipeChar(charArray)
 */
JNIEXPORT void JNICALL
Java_com_sonicvault_app_data_crypto_NativeSeedHandler_secureWipeChar(
    JNIEnv *env, jclass clazz, jcharArray array) {
    if (array == nullptr) return;

    jsize len = env->GetArrayLength(array);
    if (len <= 0) return;

    jchar* elements = env->GetCharArrayElements(array, nullptr);
    if (elements == nullptr) return;

    secure_wipe(elements, (size_t)len * sizeof(jchar));

    env->ReleaseCharArrayElements(array, elements, 0);
}

} /* extern "C" */
