package com.sonicvault.app.ui.screen.recovery

import com.sonicvault.app.BuildConfig
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonicvault.app.data.voice.VoiceBiometricAuth
import com.sonicvault.app.domain.model.ExtractedPayload
import com.sonicvault.app.domain.model.RecoveryState
import com.sonicvault.app.domain.usecase.RecoverSeedUseCase
import com.sonicvault.app.util.Bip39Validator
import com.sonicvault.app.util.FeatureFlags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.fragment.app.FragmentActivity

class RecoveryViewModel(
    private val voiceBiometricAuth: VoiceBiometricAuth,
    private val bip39Validator: Bip39Validator,
    private val recoverSeedUseCase: RecoverSeedUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<RecoveryState>(RecoveryState.Idle)
    val state: StateFlow<RecoveryState> = _state.asStateFlow()

    fun pickFile(uri: Uri, activity: FragmentActivity) {
        viewModelScope.launch {
            _state.value = RecoveryState.Reading
            _state.value = RecoveryState.Extracting
            val extractResult = recoverSeedUseCase.extractPayload(uri)
            extractResult.fold(
                onSuccess = { extracted ->
                    when {
                        extracted.unlockTimestamp != null && System.currentTimeMillis() / 1000 < extracted.unlockTimestamp ->
                            _state.value = RecoveryState.TimelockNotReached(extracted.unlockTimestamp)
                        extracted.realPayload.isPasswordMode -> _state.value = RecoveryState.AwaitingUnlock(extracted, passwordOnly = true)
                        extracted.hasDuress -> _state.value = RecoveryState.AwaitingUnlock(extracted, passwordOnly = false)
                        voiceBiometricAuth.hasEnrolledVoice() && !BuildConfig.DEBUG && FeatureFlags.VOICE_BIOMETRIC_ENABLED -> {
                            val challengeWord = voiceBiometricAuth.generateChallengeWord(bip39Validator.wordList)
                            _state.value = RecoveryState.AwaitingVoiceVerification(extracted, challengeWord)
                        }
                        else -> {
                            _state.value = RecoveryState.Decrypting
                            val decryptResult = recoverSeedUseCase.recoverWithBiometric(extracted, activity)
                            decryptResult.fold(
                                onSuccess = { _state.value = RecoveryState.ShowSeed(it.seed, it.checksumVerified, it.isPrivateKey) },
                                onFailure = { _state.value = RecoveryState.Error(it.message ?: "Recovery failed.") }
                            )
                        }
                    }
                },
                onFailure = { _state.value = RecoveryState.Error(it.message ?: "Recovery failed.") }
            )
        }
    }

    fun unlockWithBiometric(extracted: ExtractedPayload, activity: FragmentActivity) {
        viewModelScope.launch {
            _state.value = RecoveryState.Decrypting
            val result = recoverSeedUseCase.recoverWithBiometric(extracted, activity)
            result.fold(
                onSuccess = { _state.value = RecoveryState.ShowSeed(it.seed, it.checksumVerified, it.isPrivateKey) },
                onFailure = { _state.value = RecoveryState.Error(it.message ?: "Authentication failed.") }
            )
        }
    }

    /**
     * Verifies voice via challenge word, then decrypts with biometric.
     * Called when user is in AwaitingVoiceVerification state.
     */
    fun unlockWithVoiceVerification(extracted: ExtractedPayload, challengeWord: String, activity: FragmentActivity) {
        viewModelScope.launch {
            _state.value = RecoveryState.VerifyingVoice(extracted, challengeWord)
            val result = voiceBiometricAuth.verifyWithChallenge(challengeWord, durationSeconds = 3)
            result.fold(
                onSuccess = { matched ->
                    if (matched) {
                        val decryptResult = recoverSeedUseCase.recoverWithBiometric(extracted, activity)
                        decryptResult.fold(
                            onSuccess = { _state.value = RecoveryState.ShowSeed(it.seed, it.checksumVerified, it.isPrivateKey) },
                            onFailure = { _state.value = RecoveryState.Error(it.message ?: "Recovery failed.") }
                        )
                    } else {
                        _state.value = RecoveryState.Error("Voice did not match. Try again.")
                    }
                },
                onFailure = { _state.value = RecoveryState.Error(it.message ?: "Voice verification failed.") }
            )
        }
    }

    fun unlockWithPassword(extracted: ExtractedPayload, password: String) {
        viewModelScope.launch {
            _state.value = RecoveryState.Decrypting
            val result = recoverSeedUseCase.recoverWithPassword(extracted, password)
            result.fold(
                onSuccess = { _state.value = RecoveryState.ShowSeed(it.seed, it.checksumVerified, it.isPrivateKey) },
                onFailure = { _state.value = RecoveryState.Error(it.message ?: "Invalid password.") }
            )
        }
    }

    fun reset() {
        _state.value = RecoveryState.Idle
    }

    /**
     * Clear recovered seed from state when ViewModel is destroyed.
     * Prevents the seed phrase from lingering in heap after the user navigates away.
     */
    override fun onCleared() {
        _state.value = RecoveryState.Idle
        super.onCleared()
    }
}
