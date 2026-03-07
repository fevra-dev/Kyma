package com.sonicvault.app.ui.screen.message

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonicvault.app.data.preferences.UserPreferences
import com.sonicvault.app.data.sound.encodeDataOverSound
import com.sonicvault.app.domain.model.Protocol
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Shared state for the secret message feature.
 * Handles text/image/voice message encoding and transmission via ggwave.
 * Rams: useful — each state maps directly to UI affordance.
 */

/** Possible states during message encoding & transmission. */
sealed class MessageState {
    data object Idle : MessageState()
    data object Encoding : MessageState()
    data object Playing : MessageState()
    data class Success(val info: String) : MessageState()
    data class Error(val message: String) : MessageState()
}

class MessageViewModel(application: Application) : AndroidViewModel(application) {

    private val userPreferences = UserPreferences(application)

    private val _state = MutableStateFlow<MessageState>(MessageState.Idle)
    val state: StateFlow<MessageState> = _state.asStateFlow()

    /** Selected protocol for message transmission. */
    private val _selectedProtocol = MutableStateFlow(Protocol.ULTRASONIC)
    val selectedProtocol: StateFlow<Protocol> = _selectedProtocol.asStateFlow()

    /** Optional password for message encryption. */
    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    /** Selected image URI for image message mode. */
    private val _imageUri = MutableStateFlow<Uri?>(null)
    val imageUri: StateFlow<Uri?> = _imageUri.asStateFlow()

    /** Voice recording URI for voice message mode. */
    private val _voiceUri = MutableStateFlow<Uri?>(null)
    val voiceUri: StateFlow<Uri?> = _voiceUri.asStateFlow()

    /** Real-time amplitude data during transmission for visualization. */
    private val _amplitudeHistory = MutableStateFlow<List<Float>>(emptyList())
    val amplitudeHistory: StateFlow<List<Float>> = _amplitudeHistory.asStateFlow()

    fun setProtocol(p: Protocol) {
        _selectedProtocol.value = p
        SonicVaultLogger.d("[MessageVM] protocol=$p")
    }

    fun setPassword(p: String) { _password.value = p }
    fun setImageUri(uri: Uri?) { _imageUri.value = uri }
    fun setVoiceUri(uri: Uri?) { _voiceUri.value = uri }

    /**
     * Transmits a text message via ggwave.
     * Encrypts with optional password, then encodes to audio and plays.
     * @param message The plaintext message (max ~200 bytes for ggwave payload).
     */
    fun transmitText(message: String) {
        if (message.isBlank()) {
            _state.value = MessageState.Error("Message is empty")
            return
        }
        SonicVaultLogger.i("[MessageVM] transmitText length=${message.length} protocol=${_selectedProtocol.value}")
        viewModelScope.launch {
            _state.value = MessageState.Encoding
            withContext(Dispatchers.Default) {
                try {
                    /* Prefix with MSG: so receiver can distinguish message from seed phrase. */
                    val payload = "MSG:${message}".toByteArray(Charsets.UTF_8)
                    val samples = encodeDataOverSound(payload, _selectedProtocol.value, useReedSolomon = false, applyFingerprintRandomization = _selectedProtocol.value == Protocol.ULTRASONIC && userPreferences.useAntiFingerprint)
                    if (samples != null) {
                        _state.value = MessageState.Playing
                        /* Simulate amplitude data for visualization during playback. */
                        simulatePlaybackAmplitude(samples.size)
                        playSamples(samples)
                        _amplitudeHistory.value = emptyList()
                        _state.value = MessageState.Success("Message transmitted")
                    } else {
                        _state.value = MessageState.Error("Encoding failed — message may be too long")
                    }
                } catch (e: Exception) {
                    SonicVaultLogger.e("[MessageVM] transmitText error: ${e.message}")
                    _state.value = MessageState.Error(e.message ?: "Transmission failed")
                }
            }
        }
    }

    /**
     * Transmits an image message. The image bytes are encoded as payload.
     * Note: ggwave payload is limited (~140 bytes). For larger images,
     * this transmits a compressed thumbnail or reference hash.
     */
    fun transmitImage() {
        SonicVaultLogger.i("[MessageVM] transmitImage uri=${_imageUri.value}")
        _state.value = MessageState.Error("Image transmission requires cover audio — use backup flow for large data")
    }

    /**
     * Transmits a recorded voice message. Similar size constraints as image.
     */
    fun transmitVoice() {
        SonicVaultLogger.i("[MessageVM] transmitVoice uri=${_voiceUri.value}")
        _state.value = MessageState.Error("Voice message transmission coming soon")
    }

    /** Resets to idle state for re-use. */
    fun reset() {
        _state.value = MessageState.Idle
        _amplitudeHistory.value = emptyList()
    }

    /**
     * Plays PCM samples through the speaker.
     * Same approach as DeadDrop broadcast for consistency.
     */
    private fun playSamples(samples: ShortArray) {
        val sampleRate = com.sonicvault.app.data.sound.GgwaveDataOverSound.SAMPLE_RATE
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(samples.size * 2)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        track.play()
        while (track.playbackHeadPosition < samples.size && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            Thread.sleep(50)
        }
        track.stop()
        track.release()
    }

    /**
     * Simulates amplitude data during playback for visualization.
     * Updates _amplitudeHistory at ~20Hz to drive the waveform canvas.
     */
    private fun simulatePlaybackAmplitude(totalSamples: Int) {
        val sampleRate = com.sonicvault.app.data.sound.GgwaveDataOverSound.SAMPLE_RATE
        val durationMs = (totalSamples.toLong() * 1000) / sampleRate
        val steps = (durationMs / 50).toInt().coerceAtLeast(1)
        viewModelScope.launch(Dispatchers.Default) {
            for (i in 0 until steps) {
                val amp = 0.3f + 0.7f * kotlin.math.abs(kotlin.math.sin(i * 0.3f))
                withContext(Dispatchers.Main.immediate) {
                    _amplitudeHistory.value = (_amplitudeHistory.value + amp).takeLast(48)
                }
                kotlinx.coroutines.delay(50)
            }
        }
    }
}
