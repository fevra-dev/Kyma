package com.sonicvault.app.ui.screen.soundseed

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import com.sonicvault.app.data.sound.AudioRecordSourceHelper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonicvault.app.data.entropy.AudioEntropyExtractor
import com.sonicvault.app.data.entropy.LocationSeedMixer
import com.sonicvault.app.data.solana.SolanaAddressDeriver
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.wipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * ViewModel for the Sound as Seed screen.
 *
 * Records ambient audio, estimates entropy quality in real time, and
 * generates a BIP39 mnemonic from the audio-derived entropy.
 */

sealed class SoundSeedState {
    data object Idle : SoundSeedState()
    data class Recording(val elapsedSeconds: Int, val quality: Int) : SoundSeedState()
    data object Processing : SoundSeedState()
    data class Success(val mnemonic: String) : SoundSeedState()
    data class Error(val message: String) : SoundSeedState()
}

class SoundSeedViewModel(
    private val wordList: List<String>
) : ViewModel() {

    private val _state = MutableStateFlow<SoundSeedState>(SoundSeedState.Idle)
    val state: StateFlow<SoundSeedState> = _state.asStateFlow()

    private val _entropyQuality = MutableStateFlow(0)
    val entropyQuality: StateFlow<Int> = _entropyQuality.asStateFlow()

    /** 12 or 24 words. */
    private val _wordCount = MutableStateFlow(12)
    val wordCount: StateFlow<Int> = _wordCount.asStateFlow()

    /** Whether to mix GPS location into entropy. */
    private val _useLocation = MutableStateFlow(false)
    val useLocation: StateFlow<Boolean> = _useLocation.asStateFlow()

    /** Whether to search for a vanity Solana address. */
    private val _vanityEnabled = MutableStateFlow(false)
    val vanityEnabled: StateFlow<Boolean> = _vanityEnabled.asStateFlow()

    /** Vanity prefix (e.g. "solana") — address must start with this. Case-sensitive. */
    private val _vanityPrefix = MutableStateFlow("")
    val vanityPrefix: StateFlow<String> = _vanityPrefix.asStateFlow()

    private var recordJob: Job? = null

    /** Max vanity search attempts to avoid unbounded CPU. Reduced for responsive UX; user can retry. */
    private val maxVanityAttempts = 50_000
    /** Preferred sample rate; fallback chain if device doesn't support 44100. */
    private val sampleRatesToTry = intArrayOf(44100, 48000, 22050, 16000, 8000)
    private val recordDurationSeconds = 10

    fun setWordCount(count: Int) {
        _wordCount.value = count
    }

    fun setUseLocation(use: Boolean) {
        _useLocation.value = use
    }

    fun setVanityEnabled(enabled: Boolean) {
        _vanityEnabled.value = enabled
    }

    fun setVanityPrefix(prefix: String) {
        _vanityPrefix.value = prefix
    }

    /**
     * Starts ambient audio recording for entropy collection.
     * Updates quality estimate in real time every second.
     *
     * @param context Application or Activity context for AudioRecord source selection
     * @param latitude optional GPS latitude when useLocation is true
     * @param longitude optional GPS longitude when useLocation is true
     */
    fun startRecording(context: Context, latitude: Double? = null, longitude: Double? = null) {
        if (recordJob?.isActive == true) return

        recordJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                SonicVaultLogger.i("[SoundSeed] starting ${recordDurationSeconds}s recording")
                _state.value = SoundSeedState.Recording(0, 0)

                // Find a supported sample rate (getMinBufferSize returns negative if unsupported)
                val (sampleRate, bufferSize) = sampleRatesToTry.asSequence()
                    .mapNotNull { rate ->
                        val buf = AudioRecord.getMinBufferSize(
                            rate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                        )
                        if (buf > 0) rate to buf else null
                    }
                    .firstOrNull()
                    ?: run {
                        _state.value = SoundSeedState.Error("No supported audio format found on this device.")
                        return@launch
                    }

                SonicVaultLogger.i("[SoundSeed] using sampleRate=$sampleRate bufferSize=$bufferSize")

                val audioSource = AudioRecordSourceHelper.getAudioSourceForUltrasonic(context)
                var recorder: AudioRecord? = null
                try {
                    recorder = AudioRecord(
                        audioSource,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize * 2
                    )
                } catch (e: SecurityException) {
                    _state.value = SoundSeedState.Error("Microphone permission required.")
                    return@launch
                } catch (e: IllegalArgumentException) {
                    SonicVaultLogger.w("[SoundSeed] primary source failed, trying MIC", e)
                    try {
                        recorder = AudioRecord(
                            android.media.MediaRecorder.AudioSource.MIC,
                            sampleRate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize * 2
                        )
                    } catch (e2: Exception) {
                        _state.value = SoundSeedState.Error("Failed to initialize microphone. Try a different device.")
                        return@launch
                    }
                }

                var rec = recorder!!
                if (rec.state != AudioRecord.STATE_INITIALIZED) {
                    _state.value = SoundSeedState.Error("Failed to initialize microphone.")
                    rec.release()
                    return@launch
                }

                val totalSamples = sampleRate * recordDurationSeconds
                val allSamples = ShortArray(totalSamples)
                var samplesCollected = 0
                var consecutiveEmptyReads = 0
                val maxEmptyReads = 50
                val recordTimeoutMs = 60_000L
                val startTime = System.currentTimeMillis()

                try {
                    rec.startRecording()

                    while (samplesCollected < totalSamples && isActive) {
                        if (System.currentTimeMillis() - startTime > recordTimeoutMs) {
                            SonicVaultLogger.w("[SoundSeed] recording timeout after 60s")
                            break
                        }
                        val readBuf = ShortArray(bufferSize)
                        val read = rec.read(readBuf, 0, readBuf.size)
                        if (read > 0) {
                            consecutiveEmptyReads = 0
                            val toCopy = minOf(read, totalSamples - samplesCollected)
                            readBuf.copyInto(allSamples, samplesCollected, 0, toCopy)
                            samplesCollected += toCopy

                            // Update quality estimate every ~0.5s
                            val elapsedSec = samplesCollected / sampleRate
                            if (samplesCollected % (sampleRate / 2) < bufferSize) {
                                val qualitySoFar = AudioEntropyExtractor.estimateQuality(
                                    allSamples.copyOfRange(0, samplesCollected)
                                )
                                _entropyQuality.value = qualitySoFar
                                _state.value = SoundSeedState.Recording(elapsedSec, qualitySoFar)
                            }
                        } else {
                            consecutiveEmptyReads++
                            if (consecutiveEmptyReads >= maxEmptyReads && samplesCollected < totalSamples / 2) {
                                SonicVaultLogger.w("[SoundSeed] audio source returned no data, trying MIC fallback")
                                rec.stop()
                                rec.release()
                                rec = try {
                                    AudioRecord(
                                        android.media.MediaRecorder.AudioSource.MIC,
                                        sampleRate,
                                        AudioFormat.CHANNEL_IN_MONO,
                                        AudioFormat.ENCODING_PCM_16BIT,
                                        bufferSize * 2
                                    )
                                } catch (e2: SecurityException) {
                                    _state.value = SoundSeedState.Error("Microphone permission required.")
                                    return@launch
                                }
                                if (rec.state != AudioRecord.STATE_INITIALIZED) break
                                rec.startRecording()
                                consecutiveEmptyReads = 0
                            } else if (consecutiveEmptyReads >= maxEmptyReads) {
                                break
                            }
                        }
                    }

                    rec.stop()
                    SonicVaultLogger.i("[SoundSeed] recorded $samplesCollected samples")

                } finally {
                    rec.release()
                }

                // Process entropy (run on Default dispatcher to avoid blocking; extraction is CPU-heavy)
                _state.value = SoundSeedState.Processing
                val entropyBits = if (_wordCount.value == 24) 256 else 128

                var entropy = withContext(Dispatchers.Default) {
                    AudioEntropyExtractor.extractEntropy(allSamples, entropyBits)
                }
                if (entropy == null) {
                    _state.value = SoundSeedState.Error("Not enough audio data collected.")
                    return@launch
                }

                // Optional location mixing
                if (_useLocation.value && latitude != null && longitude != null) {
                    entropy = LocationSeedMixer.mixLocation(entropy, latitude, longitude)
                }

                val prefix = _vanityPrefix.value.trim()
                val doVanity = _vanityEnabled.value && prefix.isNotEmpty()

                val mnemonic = if (doVanity) {
                    // Search for mnemonic whose Solana address starts with prefix
                    searchVanityMnemonic(entropy, wordList, prefix) { isActive }
                } else {
                    entropyToMnemonic(entropy, wordList)
                }
                entropy.wipe()

                if (mnemonic != null) {
                    _state.value = SoundSeedState.Success(mnemonic)
                    SonicVaultLogger.i("[SoundSeed] mnemonic generated successfully")
                } else {
                    _state.value = SoundSeedState.Error(
                        when {
                            wordList.isEmpty() || wordList.size != 2048 ->
                                "Word list not loaded. Please restart the app."
                            doVanity -> "No vanity address found after $maxVanityAttempts attempts. Try a shorter prefix."
                            else -> "Failed to generate mnemonic."
                        }
                    )
                }
            } catch (e: Exception) {
                SonicVaultLogger.e("[SoundSeed] error during recording or processing", e)
                _state.value = SoundSeedState.Error("Recording failed. Try again in a quiet environment.")
            }
        }
    }

    /**
     * Searches for a mnemonic whose derived Solana address starts with [prefix].
     * Uses base entropy + counter to generate candidate mnemonics via SHA256.
     * @param isActive callback to check if search should continue (e.g. coroutine isActive)
     */
    private fun searchVanityMnemonic(
        baseEntropy: ByteArray,
        wordList: List<String>,
        prefix: String,
        isActive: () -> Boolean
    ): String? {
        val entropySize = if (_wordCount.value == 24) 32 else 16
        val digest = MessageDigest.getInstance("SHA-256")

        for (counter in 0 until maxVanityAttempts) {
            if (!isActive()) return null

            // Derive candidate entropy: SHA256(baseEntropy || counter)
            val counterBytes = ByteArray(4) {
                (counter shr (it * 8)).toByte()
            }
            val input = baseEntropy + counterBytes
            val hash = digest.digest(input)
            val candidateEntropy = hash.copyOfRange(0, entropySize)

            val mnemonic = entropyToMnemonic(candidateEntropy, wordList)
            candidateEntropy.wipe()

            if (mnemonic != null) {
                val address = SolanaAddressDeriver.deriveAddress(mnemonic)
                if (address != null && SolanaAddressDeriver.matchesPrefix(address, prefix)) {
                    SonicVaultLogger.i("[SoundSeed] vanity match at attempt $counter: ${address.take(20)}...")
                    return mnemonic
                }
            }
        }
        return null
    }

    fun reset() {
        recordJob?.cancel()
        _state.value = SoundSeedState.Idle
        _entropyQuality.value = 0
    }

    /** Call when RECORD_AUDIO permission is denied by user. */
    fun setPermissionDenied() {
        _state.value = SoundSeedState.Error("Microphone permission required to record audio.")
    }

    override fun onCleared() {
        super.onCleared()
        recordJob?.cancel()
    }

    companion object {
        /**
         * Converts raw entropy bytes to a BIP39 mnemonic string.
         *
         * BIP39 spec: entropy || checksum -> 11-bit groups -> word indices.
         * Checksum = first (entropy_bits / 32) bits of SHA-256(entropy).
         */
        fun entropyToMnemonic(entropy: ByteArray, wordList: List<String>): String? {
            if (entropy.isEmpty()) return null
            if (wordList.isEmpty() || wordList.size != 2048) return null
            if (entropy.size != 16 && entropy.size != 32) return null

            val checksumBits = entropy.size / 4 // 4 for 128-bit, 8 for 256-bit
            val checksum = MessageDigest.getInstance("SHA-256").digest(entropy)

            // Build bit string: entropy bits + checksum bits
            val totalBits = entropy.size * 8 + checksumBits
            val bits = BooleanArray(totalBits)

            for (i in entropy.indices) {
                for (b in 7 downTo 0) {
                    bits[i * 8 + (7 - b)] = ((entropy[i].toInt() shr b) and 1) == 1
                }
            }

            for (i in 0 until checksumBits) {
                val byteIdx = i / 8
                val bitIdx = 7 - (i % 8)
                bits[entropy.size * 8 + i] = ((checksum[byteIdx].toInt() shr bitIdx) and 1) == 1
            }

            // Split into 11-bit groups
            val wordCount = totalBits / 11
            val words = mutableListOf<String>()
            for (w in 0 until wordCount) {
                var index = 0
                for (b in 0 until 11) {
                    if (bits[w * 11 + b]) {
                        index = index or (1 shl (10 - b))
                    }
                }
                words.add(wordList[index])
            }

            return words.joinToString(" ")
        }
    }
}
