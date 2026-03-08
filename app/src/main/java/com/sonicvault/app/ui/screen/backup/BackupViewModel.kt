package com.sonicvault.app.ui.screen.backup

import android.net.Uri
import com.sonicvault.app.data.preferences.UserPreferences
import com.sonicvault.app.data.repository.CreateBackupResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonicvault.app.data.stego.AudioRecorder
import com.sonicvault.app.domain.model.BackupState
import com.sonicvault.app.domain.usecase.CreateBackupUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import androidx.fragment.app.FragmentActivity

class BackupViewModel(
    private val createBackupUseCase: CreateBackupUseCase,
    private val audioRecorder: AudioRecorder,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    /** True while recording cover in-app. */
    val isRecording = MutableStateFlow(false)

    /** Rolling buffer of recent mic amplitudes (0f..1f) for waveform visualization. Rams: honest feedback. */
    private val _amplitudeHistory = MutableStateFlow<List<Float>>(emptyList())
    val amplitudeHistory: StateFlow<List<Float>> = _amplitudeHistory.asStateFlow()

    /** Current cover URI (from picker or in-app recording). */
    private val _coverUri = MutableStateFlow<Uri?>(null)
    val coverUri: StateFlow<Uri?> = _coverUri.asStateFlow()

    fun setSeedPhrase(phrase: String) {
        _seedPhrase = phrase
    }

    fun setCoverUri(uri: Uri?) {
        _coverUri.value = uri
    }

    private var _seedPhrase: String = ""

    /** Optional duress password; when set, backup includes decoy seed. Not persisted. */
    private var _duressPassword: String? = null
    fun setDuressPassword(password: String?) { _duressPassword = password }

    /** Use password instead of biometric for encryption. Allows cross-device recovery. */
    private var _usePasswordMode: Boolean = false
    fun setUsePasswordMode(use: Boolean) { _usePasswordMode = use }

    /** Use Seed Vault signMessage KDF for hardware-bound encryption (Seeker only). */
    private var _useHardwareBound: Boolean = false
    fun setUseHardwareBound(use: Boolean) { _useHardwareBound = use }

    /** Password for encryption when usePasswordMode=true. */
    private var _password: String? = null
    fun setPassword(password: String?) { _password = password }

    /** Elapsed seconds during user-controlled recording. STOP button shown after 5s. */
    private val _recordingElapsedSeconds = MutableStateFlow(0)
    val recordingElapsedSeconds: StateFlow<Int> = _recordingElapsedSeconds.asStateFlow()

    /** True when user has requested stop (STOP button pressed). */
    private val stopRequested = AtomicBoolean(false)

    private var recordJob: kotlinx.coroutines.Job? = null

    /** Minimum 5s for ggwave compatibility. User stops when ready via STOP button. */
    private val minRecordingSeconds = 5

    /** Records microphone until user presses STOP (after 5s minimum). User-controlled duration. */
    fun recordCover() {
        if (recordJob?.isActive == true) return
        stopRequested.set(false)
        recordJob = viewModelScope.launch {
            isRecording.value = true
            _amplitudeHistory.value = emptyList()
            _recordingElapsedSeconds.value = 0
            val result = audioRecorder.recordToWavUntilStopped(
                minDurationSeconds = minRecordingSeconds,
                stopRequested = { stopRequested.get() },
                onAmplitude = { amp ->
                    viewModelScope.launch(Dispatchers.Main.immediate) {
                        _amplitudeHistory.value = (_amplitudeHistory.value + amp).takeLast(48)
                    }
                },
                onElapsedSeconds = { sec ->
                    viewModelScope.launch(Dispatchers.Main.immediate) {
                        _recordingElapsedSeconds.value = sec
                    }
                }
            )
            isRecording.value = false
            _amplitudeHistory.value = emptyList()
            _recordingElapsedSeconds.value = 0
            recordJob = null
            result.fold(
                onSuccess = { uri ->
                    _coverUri.value = uri
                    com.sonicvault.app.logging.SonicVaultLogger.i("[Backup] cover recorded uri=$uri")
                },
                onFailure = { e ->
                    com.sonicvault.app.logging.SonicVaultLogger.e("[Backup] recording failed", e)
                    _state.value = BackupState.Error(
                        e.message ?: "Recording failed. Check microphone permission."
                    )
                }
            )
        }
    }

    /** Stops the current recording. Only effective after 5s minimum. */
    fun stopRecording() {
        stopRequested.set(true)
    }

    fun startBackup(activity: FragmentActivity) {
        com.sonicvault.app.logging.SonicVaultLogger.d("[Backup] startBackup seedBlank=${_seedPhrase.isBlank()} coverUri=${_coverUri.value}")
        if (_seedPhrase.isBlank()) {
            _state.value = BackupState.Error("Enter a seed phrase (12 or 24 words) or Solana private key.")
            return
        }
        if (_coverUri.value == null) {
            _state.value = BackupState.Error("Select or record a cover audio file.")
            return
        }
        viewModelScope.launch {
            _state.value = BackupState.Validating
            _state.value = BackupState.Encrypting
            val result = createBackupUseCase(_seedPhrase, _coverUri.value!!, activity, _duressPassword, usePasswordMode = true, _password, null, useHybridMode = true, useHardwareBound = false)
            result.fold(
                onSuccess = { createResult: CreateBackupResult ->
                    /* Persist last backup timestamp and count for home screen stickiness signal. */
                    userPreferences.lastBackupTimestamp = System.currentTimeMillis()
                    userPreferences.backupCount = userPreferences.backupCount + 1
                    _state.value = BackupState.Success(
                        createResult.stegoUri,
                        createResult.checksum,
                        createResult.fingerprint,
                        createResult.shortId
                    )
                },
                onFailure = { e ->
                    _state.value = BackupState.Error(e.message ?: "Backup failed.")
                }
            )
        }
    }

    fun reset() {
        _state.value = BackupState.Idle
        _seedPhrase = ""
        _coverUri.value = null
        _duressPassword = null
        _usePasswordMode = false
        _useHardwareBound = false
        _password = null
    }

    /**
     * Wipe sensitive data when ViewModel is destroyed.
     * Prevents seed phrase and passwords from lingering in heap after the user navigates away.
     */
    override fun onCleared() {
        _seedPhrase = ""
        _duressPassword = null
        _password = null
        super.onCleared()
    }
}
