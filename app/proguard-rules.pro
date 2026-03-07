# SonicVault ProGuard rules for release build.
# Targeted keeps; R8 minifies and obfuscates for production.

# SECURITY PATCH [SVA-005]: Strip debug, verbose, AND info log calls in release builds.
# SonicVaultLogger.d() is already gated by BuildConfig.DEBUG, and .i() is defense-in-depth.
# Stripping Log.i removes any accidental info-level leaks (attestation results, payload sizes,
# protocol names, etc.) that could aid an attacker with ADB access to a release build.
# Only Log.w and Log.e remain in release for diagnosing real failures.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

# Application entry points
-keep class com.sonicvault.app.SonicVaultApplication { *; }
-keep class com.sonicvault.app.TestSonicVaultApplication { *; }
-keep class com.sonicvault.app.TestRunner { *; }

# ViewModel factory uses reflection to instantiate ViewModels
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep ViewModel subclasses (used by ViewModelProvider.Factory)
-keep class * extends androidx.lifecycle.ViewModel

# JNI / native methods (ggwave)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Native secure wipe (secure_crypto_jni) — explicit keep for defense-in-depth
-keep class com.sonicvault.app.data.crypto.NativeSeedHandler { *; }

# Play Integrity API
-keep class com.google.android.play.core.integrity.** { *; }

# Solana Seed Vault SDK
-keep class com.solanamobile.** { *; }

# Argon2
-keep class de.mkammerer.argon2.** { *; }

# BiometricPrompt (AndroidX Biometric)
-keep class androidx.biometric.** { *; }

# Keystore / crypto (required for AES-GCM, MessageDigest, etc.)
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# Kotlin serialization (if used)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# FlacExporter / JitPack (may use reflection)
-keep class com.github.amplexus.** { *; }

# Suppress missing class warnings (javax.sound not on Android; FLAC lib references it)
-dontwarn javax.sound.sampled.AudioFileFormat$Type
-dontwarn javax.sound.sampled.AudioFormat$Encoding
-dontwarn javax.sound.sampled.AudioFormat
-dontwarn javax.sound.sampled.AudioInputStream
-dontwarn javax.sound.sampled.AudioSystem
-dontwarn javax.sound.sampled.UnsupportedAudioFileException
-dontwarn javax.sound.sampled.spi.AudioFileWriter
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**

# jaudiotagger (WAV ID3 tagging) — uses reflection for tag parsing
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# Konfetti (confetti animation)
-keep class nl.dionsegijn.konfetti.** { *; }

# TrueTime (NTP verification)
-keep class com.instacart.truetime.** { *; }

# ECDH / crypto classes used by Dead Drop and GeoTimeLock
-keep class java.security.KeyPairGenerator { *; }
-keep class javax.crypto.KeyAgreement { *; }

# Google Play Services Location
-keep class com.google.android.gms.location.** { *; }
