package com.sonicvault.app.ui.screen.recovery

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.sonicvault.app.data.recovery.AcousticRestoreReceiver
import com.sonicvault.app.data.recovery.RestoreVerifier
import com.sonicvault.app.domain.usecase.RecoverFromSoundUseCase
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for acoustic vault restore ceremony.
 *
 * States: idle | listening | decrypting | teleprompter(words, index) | done | error
 */
class RestoreCeremonyViewModel(
    private val recoverFromSoundUseCase: RecoverFromSoundUseCase
) : ViewModel() {

    sealed class State {
        data object Idle : State()
        data object Listening : State()
        data class ReceivedPayload(val bytes: ByteArray) : State()
        data object Decrypting : State()
        data class Teleprompter(val words: List<String>, val currentIndex: Int) : State()
        /** Derived address shown before Seed Vault import. */
        data class ShowDerivedAddress(val derivedAddress: String) : State()
        data object Done : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var receiveJob: Job? = null

    /** Start listening for chunked backup payload. */
    fun startListening(context: Context) {
        receiveJob?.cancel()
        _state.value = State.Listening

        receiveJob = AcousticRestoreReceiver.receiveFlow(context)
            .onEach { payload ->
                _state.value = State.ReceivedPayload(payload)
            }
            .catch { e ->
                SonicVaultLogger.e("[RestoreCeremony] receive error", e)
                _state.value = State.Error("Could not receive backup. Move devices closer.")
            }
            .launchIn(viewModelScope)
    }

    /** Process received payload and decrypt. Call when ReceivedPayload. */
    fun processPayload(payloadBytes: ByteArray, activity: FragmentActivity) {
        viewModelScope.launch {
            _state.value = State.Decrypting
            val result = recoverFromSoundUseCase(payloadBytes, activity)
            result.fold(
                onSuccess = { seed ->
                    val words = seed.trim().split(" ").filter { it.isNotBlank() }
                    if (words.isNotEmpty()) {
                        _state.value = State.Teleprompter(words, 0)
                    } else {
                        _state.value = State.Error("Invalid seed phrase")
                    }
                },
                onFailure = {
                    SonicVaultLogger.e("[RestoreCeremony] decrypt failed", it)
                    _state.value = State.Error("Wrong password or corrupted backup. Try again or use a different backup.")
                }
            )
        }
    }

    fun nextWord() {
        val s = _state.value as? State.Teleprompter ?: return
        if (s.currentIndex < s.words.size - 1) {
            _state.value = State.Teleprompter(s.words, s.currentIndex + 1)
        } else {
            _state.value = State.Done
        }
    }

    /** Back for typo correction. */
    fun prevWord() {
        val s = _state.value as? State.Teleprompter ?: return
        if (s.currentIndex > 0) {
            _state.value = State.Teleprompter(s.words, s.currentIndex - 1)
        }
    }

    /** User finished teleprompter: derive address and show before Seed Vault import. */
    fun allDone() {
        val s = _state.value as? State.Teleprompter ?: return
        val mnemonic = s.words.joinToString(" ")
        val derived = RestoreVerifier.deriveAddressForDisplay(mnemonic)
        _state.value = if (derived != null) {
            State.ShowDerivedAddress(derived)
        } else {
            State.Done
        }
    }

    /** User confirmed derived address; transition to Done (caller launches Seed Vault). */
    fun proceedToSeedVault() {
        _state.value = State.Done
    }

    fun restart() {
        receiveJob?.cancel()
        receiveJob = null
        _state.value = State.Idle
    }

    fun stopListening() {
        receiveJob?.cancel()
        receiveJob = null
    }
}
