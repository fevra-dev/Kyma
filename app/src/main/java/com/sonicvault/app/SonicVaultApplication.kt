package com.sonicvault.app

import android.app.Application
import com.sonicvault.app.data.nonce.AppDatabase
import com.sonicvault.app.data.nonce.NoncePoolManager
import com.sonicvault.app.data.preferences.UserPreferences
import com.sonicvault.app.data.solana.SolanaRpcClient
import com.sonicvault.app.data.stego.AudioRecorder
import com.sonicvault.app.data.stego.FlacExporter
import com.sonicvault.app.data.voice.VoiceBiometricAuth
import com.sonicvault.app.data.repository.BackupRepository
import com.sonicvault.app.domain.usecase.CreateBackupUseCase
import com.sonicvault.app.domain.usecase.GetPayloadForSoundUseCase
import com.sonicvault.app.domain.usecase.RecombineSeedUseCase
import com.sonicvault.app.domain.usecase.RecoverFromSoundUseCase
import com.sonicvault.app.domain.usecase.RecoverSeedUseCase
import com.sonicvault.app.domain.usecase.SplitSeedUseCase
import com.sonicvault.app.di.AppModule
import com.sonicvault.app.util.AutoLockManager
import com.sonicvault.app.util.Bip39Validator
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point; provides use cases and audio recorder for ViewModels.
 * Initializes security managers (AutoLock, root detection) on startup.
 * Runs NoncePoolManager.reconcileOnStartup for crash recovery of IN_FLIGHT nonces.
 */
class SonicVaultApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        /** Initialize auto-lock: monitors app lifecycle for inactivity timeout. */
        AutoLockManager.init()
        SonicVaultLogger.d("[App] AutoLockManager initialized")

        /** Reconcile nonce pool: recover IN_FLIGHT nonces after crash. */
        applicationScope.launch(Dispatchers.IO) {
            noncePoolManager.reconcileOnStartup()
        }
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val noncePoolManager: NoncePoolManager by lazy {
        NoncePoolManager(
            dao = AppDatabase.getInstance(this).noncePoolDao(),
            rpc = SolanaRpcClient(),
            backgroundScope = ioScope
        )
    }

    val userPreferences: UserPreferences by lazy { UserPreferences(this) }

    val backupRepository: BackupRepository by lazy {
        AppModule.provideBackupRepository(this)
    }

    val createBackupUseCase: CreateBackupUseCase by lazy {
        AppModule.provideCreateBackupUseCase(this)
    }

    val recoverSeedUseCase: RecoverSeedUseCase by lazy {
        AppModule.provideRecoverSeedUseCase(this)
    }

    val audioRecorder: AudioRecorder by lazy {
        AppModule.provideAudioRecorder(this)
    }

    val flacExporter: FlacExporter by lazy {
        AppModule.provideFlacExporter(this)
    }

    val getPayloadForSoundUseCase: GetPayloadForSoundUseCase by lazy {
        AppModule.provideGetPayloadForSoundUseCase(this)
    }

    val recoverFromSoundUseCase: RecoverFromSoundUseCase by lazy {
        AppModule.provideRecoverFromSoundUseCase(this)
    }

    val voiceBiometricAuth: VoiceBiometricAuth by lazy {
        AppModule.provideVoiceBiometricAuth(this)
    }

    val splitSeedUseCase: SplitSeedUseCase by lazy {
        AppModule.provideSplitSeedUseCase(this)
    }

    val recombineSeedUseCase: RecombineSeedUseCase by lazy {
        AppModule.provideRecombineSeedUseCase(this)
    }

    val bip39Validator: Bip39Validator by lazy {
        AppModule.provideBip39Validator(this)
    }
}
