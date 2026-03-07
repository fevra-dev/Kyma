package com.sonicvault.app.ui.screen.recovery

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonicvault.app.data.preferences.UserPreferences
import com.sonicvault.app.data.repository.BackupRepository
import com.sonicvault.app.data.sound.AcousticTransmitter
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for acoustic backup transmit: pick file → extract payload → transmit.
 *
 * State machine: Idle | Extracting | Transmitting(chunk, total) | Done | Error
 */
class RestoreBroadcastViewModel(
    private val backupRepository: BackupRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    sealed class State {
        data object Idle : State()
        data object Extracting : State()
        data class Transmitting(val chunk: Int, val total: Int) : State()
        data object Done : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Session ID for restore payload (AcousticRestoreReceiver accepts any session). */
    private val RESTORE_SESSION_ID = 3

    /**
     * Pick backup file, extract raw payload, transmit acoustically.
     */
    fun pickAndTransmit(uri: Uri) {
        viewModelScope.launch {
            _state.value = State.Extracting
            val result = backupRepository.getRawPayloadBytesFromBackup(uri)
            result.fold(
                onSuccess = { payload ->
                    SonicVaultLogger.i("[RestoreBroadcast] extracted ${payload.size} bytes, transmitting")
                    _state.value = State.Transmitting(0, 0)
                    try {
                        AcousticTransmitter.transmitChunked(
                            payload = payload,
                            sessionId = RESTORE_SESSION_ID,
                            applyFingerprintRandomization = userPreferences.useAntiFingerprint,
                            onChunkEncoded = { chunk, total ->
                                _state.value = State.Transmitting(chunk, total)
                            }
                        )
                        _state.value = State.Done
                    } catch (e: Exception) {
                        SonicVaultLogger.e("[RestoreBroadcast] transmit failed", e)
                        _state.value = State.Error(e.message ?: "Transmit failed")
                    }
                },
                onFailure = {
                    SonicVaultLogger.e("[RestoreBroadcast] extract failed", it)
                    _state.value = State.Error(it.message ?: "Failed to extract backup")
                }
            )
        }
    }

    fun reset() {
        _state.value = State.Idle
    }
}
