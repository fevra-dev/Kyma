package com.sonicvault.app.ui.screen.shamir

import android.net.Uri
import androidx.fragment.app.FragmentActivity
import com.sonicvault.app.data.repository.CreateBackupResult
import com.sonicvault.app.data.stego.AudioRecorder
import com.sonicvault.app.domain.usecase.SplitSeedUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Split flow state: config → splitting → success/error */
sealed class SplitSeedState {
    data object Idle : SplitSeedState()
    data object Splitting : SplitSeedState()
    data class Success(val results: List<CreateBackupResult>) : SplitSeedState()
    data class Error(val message: String) : SplitSeedState()
}

class SplitSeedViewModel(
    private val splitSeedUseCase: SplitSeedUseCase,
    private val audioRecorder: AudioRecorder
) : ViewModel() {

    private val _state = MutableStateFlow<SplitSeedState>(SplitSeedState.Idle)
    val state: StateFlow<SplitSeedState> = _state.asStateFlow()

    private val _coverUri = MutableStateFlow<Uri?>(null)
    val coverUri: StateFlow<Uri?> = _coverUri.asStateFlow()

    /** True while recording cover audio in-app. */
    val isRecording = MutableStateFlow(false)

    /** Rolling buffer of mic amplitudes (0f..1f) for waveform visualization. */
    private val _amplitudeHistory = MutableStateFlow<List<Float>>(emptyList())
    val amplitudeHistory: StateFlow<List<Float>> = _amplitudeHistory.asStateFlow()

    /** Elapsed seconds during user-controlled recording. STOP button shown after 5s. */
    private val _recordingElapsedSeconds = MutableStateFlow(0)
    val recordingElapsedSeconds: StateFlow<Int> = _recordingElapsedSeconds.asStateFlow()

    private val stopRequested = AtomicBoolean(false)

    private var _seedPhrase = ""
    fun setSeedPhrase(phrase: String) { _seedPhrase = phrase }

    private var _threshold = 2
    fun setThreshold(t: Int) { _threshold = t.coerceIn(1, 16) }

    private var _totalShares = 3
    fun setTotalShares(n: Int) { _totalShares = n.coerceIn(1, 16) }

    fun setCoverUri(uri: Uri?) { _coverUri.value = uri }

    /** Records microphone until user presses STOP (after 5s minimum). */
    fun recordCover() {
        stopRequested.set(false)
        viewModelScope.launch {
            isRecording.value = true
            _amplitudeHistory.value = emptyList()
            _recordingElapsedSeconds.value = 0
            val result = audioRecorder.recordToWavUntilStopped(
                minDurationSeconds = 5,
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
            result.fold(
                onSuccess = { uri -> _coverUri.value = uri },
                onFailure = { }
            )
        }
    }

    fun stopRecording() {
        stopRequested.set(true)
    }

    fun startSplit(activity: FragmentActivity) {
        if (_seedPhrase.isBlank() || _coverUri.value == null) {
            _state.value = SplitSeedState.Error("Enter seed phrase and pick a cover audio file.")
            return
        }
        if (_threshold > _totalShares) {
            _state.value = SplitSeedState.Error("Threshold must not exceed total shares.")
            return
        }
        viewModelScope.launch {
            _state.value = SplitSeedState.Splitting
            val result = splitSeedUseCase(
                seedPhrase = _seedPhrase,
                threshold = _threshold,
                totalShares = _totalShares,
                coverAudioUri = _coverUri.value!!,
                activity = activity
            )
            result.fold(
                onSuccess = { _state.value = SplitSeedState.Success(it) },
                onFailure = { _state.value = SplitSeedState.Error(it.message ?: "Split failed.") }
            )
        }
    }

    fun reset() {
        _state.value = SplitSeedState.Idle
        _seedPhrase = ""
        _coverUri.value = null
    }
}
