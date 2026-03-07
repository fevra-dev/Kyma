package com.sonicvault.app.ui.screen.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonicvault.app.data.voice.VoiceBiometricAuth
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class VoiceEnrollState {
    data object Idle : VoiceEnrollState()
    data object Recording : VoiceEnrollState()
    data object Success : VoiceEnrollState()
    /** Verification test in progress (recording + comparing). */
    data object Testing : VoiceEnrollState()
    /** Voice matched enrolled voiceprint. */
    data object TestPassed : VoiceEnrollState()
    /** Voice did not match enrolled voiceprint. */
    data object TestFailed : VoiceEnrollState()
    /** Liveness challenge: user must speak the displayed word. */
    data class LivenessChallenge(val word: String) : VoiceEnrollState()
    data class Error(val message: String) : VoiceEnrollState()
}

/**
 * ViewModel for voice biometric enrollment and liveness-verified testing.
 * Enrollment records 4s of audio, extracts a speaker embedding, and stores it.
 * Test Voice uses challenge-response: displays a random BIP39 word, user speaks it.
 * This prevents replay attacks (replaying the enrollment recording won't work
 * because the challenge word differs each time).
 */
class VoiceEnrollViewModel(
    private val voiceBiometricAuth: VoiceBiometricAuth,
    private val bip39WordList: List<String> = emptyList()
) : ViewModel() {

    private val _state = MutableStateFlow<VoiceEnrollState>(VoiceEnrollState.Idle)
    val state: StateFlow<VoiceEnrollState> = _state.asStateFlow()

    /** Rolling amplitude history for real-time waveform visualization during recording. */
    private val _amplitudeHistory = MutableStateFlow<List<Float>>(emptyList())
    val amplitudeHistory: StateFlow<List<Float>> = _amplitudeHistory.asStateFlow()

    /** Current liveness challenge word (null if no active challenge). */
    private val _challengeWord = MutableStateFlow<String?>(null)
    val challengeWord: StateFlow<String?> = _challengeWord.asStateFlow()

    fun hasEnrolledVoice(): Boolean = voiceBiometricAuth.hasEnrolledVoice()

    /** Amplitude callback: accumulates RMS values for waveform display. */
    private val amplitudeCallback: (Float) -> Unit = { amp ->
        _amplitudeHistory.value = (_amplitudeHistory.value + amp).takeLast(48)
    }

    /** Records 4s and creates/replaces the voice enrollment embedding. */
    fun enroll() {
        viewModelScope.launch {
            _amplitudeHistory.value = emptyList()
            _state.value = VoiceEnrollState.Recording
            val result = voiceBiometricAuth.enroll(4, onAmplitude = amplitudeCallback)
            result.fold(
                onSuccess = { _state.value = VoiceEnrollState.Success },
                onFailure = { e ->
                    val msg = e.message ?: "Enrollment failed"
                    _state.value = VoiceEnrollState.Error(
                        if (msg.contains("initialize") || msg.contains("AudioRecord") || msg.contains("RECORD_AUDIO"))
                            "Microphone unavailable. Use a real device; emulators often lack mic support."
                        else msg
                    )
                }
            )
        }
    }

    /**
     * Initiates liveness challenge: generates a random BIP39 word and displays it.
     * User must speak this word before verification proceeds. This ensures temporal
     * uniqueness — a replay of the enrollment recording won't contain the challenge word.
     */
    fun startLivenessChallenge() {
        if (!voiceBiometricAuth.hasEnrolledVoice()) {
            _state.value = VoiceEnrollState.Error("No voice enrolled yet")
            return
        }
        val word = if (bip39WordList.isNotEmpty()) {
            voiceBiometricAuth.generateChallengeWord(bip39WordList)
        } else {
            /** Fallback: short list of common BIP39 words if full list not provided. */
            listOf("ability", "ocean", "garden", "planet", "coffee", "mountain", "river",
                "sunset", "crystal", "shadow", "forest", "diamond").random()
        }
        _challengeWord.value = word
        _state.value = VoiceEnrollState.LivenessChallenge(word)
        SonicVaultLogger.d("[VoiceEnroll] liveness challenge issued word=$word")
    }

    /**
     * Executes voice verification after user has seen the challenge word.
     * Uses verifyWithChallenge() which logs the challenge for audit trail.
     */
    fun verifyAfterChallenge() {
        val word = _challengeWord.value ?: run {
            _state.value = VoiceEnrollState.Error("No challenge word set")
            return
        }
        viewModelScope.launch {
            _amplitudeHistory.value = emptyList()
            _state.value = VoiceEnrollState.Testing
            val result = voiceBiometricAuth.verifyWithChallenge(word, 3, onAmplitude = amplitudeCallback)
            result.fold(
                onSuccess = { matched ->
                    _state.value = if (matched) VoiceEnrollState.TestPassed
                                   else VoiceEnrollState.TestFailed
                    _challengeWord.value = null
                },
                onFailure = { e ->
                    val msg = e.message ?: "Verification failed"
                    _state.value = VoiceEnrollState.Error(
                        if (msg.contains("initialize") || msg.contains("AudioRecord") || msg.contains("RECORD_AUDIO"))
                            "Microphone unavailable. Use a real device."
                        else msg
                    )
                    _challengeWord.value = null
                }
            )
        }
    }

    /**
     * Tests the enrolled voiceprint with liveness challenge.
     * Provides immediate feedback so users know if their voice enrollment works.
     */
    fun testVoice() {
        startLivenessChallenge()
    }

    fun clearEnrollment() {
        voiceBiometricAuth.clearEnrollment()
        _challengeWord.value = null
        _state.value = VoiceEnrollState.Idle
    }

    fun reset() {
        _challengeWord.value = null
        _state.value = VoiceEnrollState.Idle
    }
}
