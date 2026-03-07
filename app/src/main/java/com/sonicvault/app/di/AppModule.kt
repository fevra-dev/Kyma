package com.sonicvault.app.di

import android.content.Context
import com.sonicvault.app.BuildConfig
import com.sonicvault.app.data.attestation.DeviceAttestationProvider
import com.sonicvault.app.data.attestation.PlayIntegrityAttestationProvider
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sonicvault.app.data.binding.AndroidDeviceBindingProvider
import com.sonicvault.app.data.binding.DeviceBindingProvider
import com.sonicvault.app.data.nonce.DeadDropNonceManager
import com.sonicvault.app.data.crypto.AndroidSeedVaultCrypto
import com.sonicvault.app.data.crypto.FakeSeedVaultCrypto
import com.sonicvault.app.data.crypto.SeedVaultCrypto
import com.sonicvault.app.data.crypto.SeekerSeedVaultCrypto
import com.sonicvault.app.data.repository.BackupRepository
import com.sonicvault.app.data.repository.BackupRepositoryImpl
import com.sonicvault.app.data.stego.AudioDecoder
import com.sonicvault.app.data.stego.AudioDecoderImpl
import com.sonicvault.app.data.stego.FlacExporter
import com.sonicvault.app.data.stego.FlacExporterImpl
import com.sonicvault.app.data.stego.AudioRecorder
import com.sonicvault.app.data.stego.AudioRecorderImpl
import com.sonicvault.app.data.stego.HybridSteganography
import com.sonicvault.app.data.stego.HybridSteganographyImpl
import com.sonicvault.app.data.ecdh.EcdhHandshake
import com.sonicvault.app.data.ecdh.EcdhHandshakeImpl
import com.sonicvault.app.data.sound.FountainCodec
import com.sonicvault.app.data.sound.LtFountainCodec
import com.sonicvault.app.data.stego.LsbMatchingStegoCodec
import com.sonicvault.app.data.stego.LSBSteganography
import com.sonicvault.app.data.stego.LSBSteganographyImpl
import com.sonicvault.app.data.stego.PhaseSteganographyImpl
import com.sonicvault.app.data.stego.WavAudioHandler
import com.sonicvault.app.data.stego.WavAudioHandlerImpl
import com.sonicvault.app.data.voice.FeatureBasedVoiceEmbeddingExtractor
import com.sonicvault.app.data.voice.VoiceBiometricAuth
import com.sonicvault.app.data.voice.VoiceBiometricAuthImpl
import com.sonicvault.app.data.voice.VoiceEmbeddingExtractor
import com.sonicvault.app.data.shamir.Slip39Shamir
import com.sonicvault.app.data.shamir.Slip39ShamirImpl
import com.sonicvault.app.domain.usecase.CreateBackupUseCase
import com.sonicvault.app.domain.usecase.GetPayloadForSoundUseCase
import com.sonicvault.app.domain.usecase.RecoverFromSoundUseCase
import com.sonicvault.app.domain.usecase.RecoverSeedUseCase
import com.sonicvault.app.domain.usecase.RecombineSeedUseCase
import com.sonicvault.app.domain.usecase.SplitSeedUseCase
import com.sonicvault.app.util.Bip39Entropy
import com.sonicvault.app.util.Bip39Validator
import com.sonicvault.app.util.isSolanaSeeker
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.data.preferences.UserPreferences

/**
 * Simple factory/DI: provides implementations for crypto, stego, repository, and use cases.
 */
object AppModule {

    fun provideBip39Validator(context: Context): Bip39Validator = Bip39Validator(context)

    fun provideBip39Entropy(context: Context): Bip39Entropy = Bip39Entropy(context)

    fun provideSlip39Shamir(context: Context): Slip39Shamir =
        Slip39ShamirImpl(context, provideBip39Entropy(context))

    /**
     * Selects crypto provider: FakeSeedVaultCrypto in debug (no biometric prompt),
     * SeekerSeedVault on Solana Seeker, AndroidKeystore otherwise.
     * Logs provider on selection (never logs keys, plaintext, or seed).
     */
    fun provideSeedVaultCrypto(context: Context): SeedVaultCrypto =
        if (BuildConfig.DEBUG) {
            SonicVaultLogger.i("[SeedVault] provider=FakeSeedVaultCrypto (debug)")
            FakeSeedVaultCrypto()
        } else if (isSolanaSeeker(context)) {
            SeekerSeedVaultCrypto(context)
        } else {
            SonicVaultLogger.i("[SeedVault] provider=AndroidKeystore")
            AndroidSeedVaultCrypto(context)
        }

    fun provideWavAudioHandler(context: Context): WavAudioHandler = WavAudioHandlerImpl(context)

    fun provideFlacExporter(context: Context): FlacExporter = FlacExporterImpl(context)

    fun provideAudioDecoder(context: Context): AudioDecoder =
        AudioDecoderImpl(context, provideWavAudioHandler(context))

    fun provideAudioRecorder(context: Context): AudioRecorder = AudioRecorderImpl(context)

    fun provideVoiceEmbeddingExtractor(): VoiceEmbeddingExtractor = FeatureBasedVoiceEmbeddingExtractor()

    fun provideVoiceBiometricAuth(context: Context): VoiceBiometricAuth =
        VoiceBiometricAuthImpl(
            context = context,
            audioRecorder = provideAudioRecorder(context),
            embeddingExtractor = provideVoiceEmbeddingExtractor()
        )

    fun provideDeviceBinding(context: Context): DeviceBindingProvider = AndroidDeviceBindingProvider(context)

    /** Encrypted prefs for Dead Drop monotonic nonce (replay protection). */
    fun provideDeadDropSecurePrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "sonicvault_dead_drop_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun provideDeadDropNonceManager(context: Context): DeadDropNonceManager =
        DeadDropNonceManager(provideDeadDropSecurePrefs(context))

    /** Play Integrity API for device attestation before decrypt (rooted, emulator, tampered). */
    fun provideDeviceAttestation(): DeviceAttestationProvider = PlayIntegrityAttestationProvider()

    fun provideLSBSteganography(): LSBSteganography = LSBSteganographyImpl()

    fun provideLsbMatchingStegoCodec(): LsbMatchingStegoCodec = LsbMatchingStegoCodec()

    fun provideHybridSteganography(): HybridSteganography =
        HybridSteganographyImpl(
            provideLSBSteganography(),
            provideLsbMatchingStegoCodec(),
            PhaseSteganographyImpl()
        )

    fun provideBackupRepository(context: Context): BackupRepository =
        BackupRepositoryImpl(
            appContext = context.applicationContext,
            crypto = provideSeedVaultCrypto(context),
            wavHandler = provideWavAudioHandler(context),
            audioDecoder = provideAudioDecoder(context),
            stego = provideLSBSteganography(),
            hybridStego = provideHybridSteganography(),
            deviceBinding = provideDeviceBinding(context),
            bip39Validator = provideBip39Validator(context),
            attestation = provideDeviceAttestation()
        )

    fun provideCreateBackupUseCase(context: Context): CreateBackupUseCase =
        CreateBackupUseCase(
            repository = provideBackupRepository(context),
            bip39Validator = provideBip39Validator(context)
        )

    fun provideRecoverSeedUseCase(context: Context): RecoverSeedUseCase =
        RecoverSeedUseCase(
            repository = provideBackupRepository(context),
            bip39Validator = provideBip39Validator(context)
        )

    fun provideGetPayloadForSoundUseCase(context: Context): GetPayloadForSoundUseCase =
        GetPayloadForSoundUseCase(
            repository = provideBackupRepository(context),
            bip39Validator = provideBip39Validator(context)
        )

    fun provideRecoverFromSoundUseCase(context: Context): RecoverFromSoundUseCase =
        RecoverFromSoundUseCase(
            repository = provideBackupRepository(context),
            bip39Validator = provideBip39Validator(context)
        )

    fun provideSplitSeedUseCase(context: Context): SplitSeedUseCase =
        SplitSeedUseCase(
            shamir = provideSlip39Shamir(context),
            repository = provideBackupRepository(context),
            bip39Validator = provideBip39Validator(context)
        )

    fun provideRecombineSeedUseCase(context: Context): RecombineSeedUseCase =
        RecombineSeedUseCase(
            shamir = provideSlip39Shamir(context),
            repository = provideBackupRepository(context),
            bip39Validator = provideBip39Validator(context)
        )

    /** X25519 ECDH handshake for per-session forward secrecy (Tier 3). */
    fun provideEcdhHandshake(): EcdhHandshake = EcdhHandshakeImpl()

    /** LT Fountain codes for noise-robust droplet-based transmission (Tier 3). */
    fun provideFountainCodec(): FountainCodec = LtFountainCodec()

    fun provideUserPreferences(context: Context): UserPreferences = UserPreferences(context)
}
