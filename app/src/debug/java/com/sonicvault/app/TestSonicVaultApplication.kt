package com.sonicvault.app

import android.app.Application
import com.sonicvault.app.data.attestation.DeviceAttestationProvider
import com.sonicvault.app.data.binding.AndroidDeviceBindingProvider
import com.sonicvault.app.data.crypto.FakeSeedVaultCrypto
import com.sonicvault.app.data.crypto.SeedVaultCrypto
import com.sonicvault.app.data.repository.BackupRepository
import com.sonicvault.app.data.repository.BackupRepositoryImpl
import com.sonicvault.app.data.stego.AudioDecoderImpl
import com.sonicvault.app.data.stego.HybridSteganographyImpl
import com.sonicvault.app.data.stego.LSBSteganographyImpl
import com.sonicvault.app.data.stego.LsbMatchingStegoCodec
import com.sonicvault.app.data.stego.PhaseSteganographyImpl
import com.sonicvault.app.data.stego.WavAudioHandlerImpl
import com.sonicvault.app.domain.usecase.CreateBackupUseCase
import com.sonicvault.app.domain.usecase.RecoverSeedUseCase
import com.sonicvault.app.util.Bip39Validator

/**
 * Test Application (debug only): provides use cases backed by FakeSeedVaultCrypto
 * so instrumented tests can run backup → recover without biometric.
 */
class TestSonicVaultApplication : Application() {

    private val crypto: SeedVaultCrypto = FakeSeedVaultCrypto()
    private val wavHandler = WavAudioHandlerImpl(this)
    private val bip39Validator = Bip39Validator(this)
    private val attestation: DeviceAttestationProvider = FakeAttestationProvider()

    private val repository: BackupRepository by lazy {
        BackupRepositoryImpl(
            appContext = applicationContext,
            crypto = crypto,
            wavHandler = wavHandler,
            audioDecoder = AudioDecoderImpl(this, wavHandler),
            stego = LSBSteganographyImpl(),
            hybridStego = HybridSteganographyImpl(LSBSteganographyImpl(), LsbMatchingStegoCodec(), PhaseSteganographyImpl()),
            deviceBinding = AndroidDeviceBindingProvider(this),
            bip39Validator = bip39Validator,
            attestation = attestation
        )
    }

    val createBackupUseCase: CreateBackupUseCase by lazy {
        CreateBackupUseCase(repository, bip39Validator)
    }

    val recoverSeedUseCase: RecoverSeedUseCase by lazy {
        RecoverSeedUseCase(repository, bip39Validator)
    }
}
