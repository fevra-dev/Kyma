plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.22-1.0.16"
}

// Exclude transitive bcprov-jdk15to18 to avoid duplicate class/resource conflicts with bcprov-jdk18on
configurations.all {
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
}

// Read Helius API key from gradle.properties or local.properties (gitignored)
val heliusApiKey: String = project.findProperty("HELIUS_API_KEY")?.toString() ?: ""

android {
    namespace = "com.sonicvault.app"
    compileSdk = 34
    ndkVersion = "29.0.14206865"  // match installed NDK (SDK Tools)
    defaultConfig {
        applicationId = "com.sonicvault.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "com.sonicvault.app.TestRunner"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        buildConfigField("String", "HELIUS_API_KEY", "\"${heliusApiKey}\"")
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            // Resolve duplicate BouncyCastle resources when both bcprov variants are pulled transitively
            pickFirsts += "org/bouncycastle/x509/CertPathReviewerMessages_de.properties"
            pickFirsts += "org/bouncycastle/x509/CertPathReviewerMessages.properties"
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.media3:media3-common:1.2.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // Argon2id for optional password-based encryption (memory-hard KDF)
    implementation("de.mkammerer:argon2-jvm:2.11")
    // FLAC encoder: pure Java, lossless 50–70% smaller than WAV; Maven Central (avoids JitPack timeout)
    implementation("com.github.axet:java-flac-encoder:0.3.8")
    // Solana Mobile Seed Vault SDK (optional; for future signMessage-based encrypt when API extends)
    implementation("com.solanamobile:seedvault-wallet-sdk:0.4.0")
    // Mobile Wallet Adapter client for Seed Vault transaction signing (SonicRequest)
    implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.0.0")
    // Play Integrity API: device attestation before decrypt (rooted, emulator, tampered)
    implementation("com.google.android.play:integrity:1.3.0")
    // ZXing for QR code export (encrypted seed backup)
    implementation("com.google.zxing:core:3.5.2")
    // OkHttp with certificate pinning for future network calls (NTP fallback, potential server-side verification)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // ProcessLifecycleOwner for auto-lock inactivity detection
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    // PrintHelper for QR code printing
    implementation("androidx.print:print:1.0.0")
    // WAV ID3 tagging for music player disguise
    implementation("com.github.AdrienPoupa:jaudiotagger:2.2.3")
    // Confetti animation for success celebration
    implementation("nl.dionsegijn:konfetti-compose:2.0.4")
    // NTP time verification for GeoTimeLock (via JitPack)
    implementation("com.github.instacart:truetime-android:4.0.0.alpha")
    // Google Play Services Location for GPS (GeoTimeLock)
    implementation("com.google.android.gms:play-services-location:21.1.0")
    // JUnit for unit tests
    testImplementation("junit:junit:4.13.2")
    // SolanaKT: mnemonic → Solana address derivation for vanity search (BIP44 m/44'/501'/0'/0')
    implementation("com.github.metaplex-foundation:SolanaKT:2.1.1")
    // Base58 encoding for Solana pubkeys (MWA authResult.accounts[].publicKey is byte[])
    implementation("io.github.novacrypto:Base58:2022.01.17")
    // Bouncy Castle: HKDF for SE-bound encryption (Phase 2)
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
    // Tink: X25519 ECDH for Tier 3 forward secrecy handshake
    implementation("com.google.crypto.tink:tink-android:1.11.0")
    // Room: nonce pool persistence for SonicSafe durable nonce
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
