package com.sonicvault.app.ui.screen.deadrop

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.HapticFeedbackConstantsCompat
import com.sonicvault.app.ui.theme.TouchTargetMin
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing
import com.sonicvault.app.SonicVaultApplication
import com.sonicvault.app.ui.screen.sonicsafe.ColdSignerScreen
import com.sonicvault.app.ui.screen.sonicrequest.SonicRequestSheet
import com.sonicvault.app.ui.screen.sonicrequest.SonicRequestViewModel
import com.sonicvault.app.ui.screen.sonicsafe.SonicSafeHotViewModel
import com.sonicvault.app.ui.screen.sonicsafe.SonicSafeViewModel
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sonicvault.app.data.binding.DeviceBindingProvider
import com.sonicvault.app.data.codec.ReedSolomonCodec
import com.sonicvault.app.data.crypto.SasGenerator
import com.sonicvault.app.data.deadrop.DeadDropEncryptor
import com.sonicvault.app.data.deadrop.DeadDropListenEvent
import com.sonicvault.app.data.deadrop.DeadDropReceiverService
import com.sonicvault.app.data.receive.UnifiedAcousticReceiver
import com.sonicvault.app.data.receive.UnifiedReceiveEvent
import com.sonicvault.app.data.deadrop.PayloadV2Builder
import com.sonicvault.app.data.deadrop.PayloadV2Parser
import com.sonicvault.app.data.ecdh.EcdhHandshake
import com.sonicvault.app.data.solana.SolanaPayUri
import com.sonicvault.app.data.nonce.DeadDropNonceManager
import com.sonicvault.app.data.preferences.UserPreferences
import com.sonicvault.app.data.sound.FountainCodec
import com.sonicvault.app.data.sound.FountainDecoder
import com.sonicvault.app.data.sound.FountainTransmitter
import com.sonicvault.app.data.sound.GgwaveDataOverSound
import com.sonicvault.app.domain.model.Protocol
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dead Drop broadcast: transmit encrypted seed to all nearby devices simultaneously.
 *
 * Sender mode: enter seed/message, encrypt with ECDH, broadcast via ggwave.
 * Receiver mode: listen for Dead Drop packets, decrypt, display.
 */

sealed class DeadDropState {
    data object Idle : DeadDropState()
    data object Broadcasting : DeadDropState()
    data object Listening : DeadDropState()
    /** Sender waiting for ECDH_RESP from receiver. */
    data object AwaitingEcdhResponse : DeadDropState()
    /** After ECDH broadcast: show SAS for user to verify with receiver. */
    data class BroadcastComplete(val sas: String) : DeadDropState()
    /** payload = message; sas = non-null for ECDH (MITM verification), null for passphrase. */
    data class Received(val payload: String, val sas: String? = null) : DeadDropState()
    /** Solana Pay URI received; show payment approval sheet. */
    data class ReceivedPaymentRequest(val uri: SolanaPayUri) : DeadDropState()
    /** Chunked TX received (session 1); show cold sign UI. */
    data class ReceivedTxToSign(val txBytes: ByteArray) : DeadDropState()
    data class Error(val message: String) : DeadDropState()
}

/** Transaction types for TRANSMIT mode. Rams: each has a clear, defined purpose. */
enum class TxType(val label: String, val icon: String, val unit: String) {
    SOL_PAY("Sol Pay", "◎", "◎"),
    SKR_TIP("SKR Tip", "⬡", "SKR"),
    COLD_SIGN("Cold Sign", "□", "SIG"),
    CNFT_DROP("cNFT Drop", "◈", "NFT");

    val hasAmount: Boolean get() = this == SOL_PAY || this == SKR_TIP
    val hasRecipient: Boolean get() = this == SOL_PAY || this == SKR_TIP || this == COLD_SIGN
    val hasMemo: Boolean get() = this == SOL_PAY || this == SKR_TIP
    val hasEventId: Boolean get() = this == CNFT_DROP
    val awaitingLabel: String get() = when (this) {
        COLD_SIGN -> "Awaiting TX Payload"
        CNFT_DROP -> "Event ID Required"
        else -> ""
    }
}

/** ECDH payload header: nonce(8) + deviceBinding(32) = 40 bytes. Passphrase mode: raw message. */
private const val NONCE_BYTES = 8
private const val BINDING_BYTES = 32
private const val ECDH_HEADER_BYTES = NONCE_BYTES + BINDING_BYTES

class DeadDropViewModel(
    private val nonceManager: DeadDropNonceManager?,
    private val deviceBinding: DeviceBindingProvider,
    private val ecdhHandshake: EcdhHandshake,
    private val fountainCodec: FountainCodec,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _state = MutableStateFlow<DeadDropState>(DeadDropState.Idle)
    val state: StateFlow<DeadDropState> = _state.asStateFlow()

    private val _protocol = MutableStateFlow(Protocol.ULTRASONIC)
    val protocol: StateFlow<Protocol> = _protocol.asStateFlow()

    /** Session code for passphrase-encrypted broadcast (web-compatible). When set, uses SVDD + AUDIBLE_FAST for web receiver. */
    private val _sessionCode = MutableStateFlow("")
    val sessionCode: StateFlow<String> = _sessionCode.asStateFlow()

    /** Secure handshake: X25519 ECDH before payload (app-only, no session code). ON by default. */
    private val _useSecureHandshake = MutableStateFlow(true)
    val useSecureHandshake: StateFlow<Boolean> = _useSecureHandshake.asStateFlow()

    /** Noise-resistant: fountain droplets instead of single-shot (app-only). ON by default. */
    private val _useFountainMode = MutableStateFlow(true)
    val useFountainMode: StateFlow<Boolean> = _useFountainMode.asStateFlow()

    private var job: Job? = null

    fun setProtocol(p: Protocol) {
        _protocol.value = p
    }

    fun setSessionCode(code: String) {
        _sessionCode.value = code
    }

    fun setUseSecureHandshake(enabled: Boolean) {
        _useSecureHandshake.value = enabled
    }

    fun setUseFountainMode(enabled: Boolean) {
        _useFountainMode.value = enabled
    }

    /**
     * Broadcasts payload to all nearby devices via ggwave.
     * Session code: passphrase-encrypted (web-compatible, AUDIBLE_FAST).
     * Secure handshake: X25519 ECDH + Payload v2 (app-only).
     * Fountain mode: droplet-based transmission (app-only).
     *
     * @param context Required for secure handshake (listening for ECDH_RESP)
     */
    fun broadcast(context: Context, payload: String) {
        if (job?.isActive == true) return

        job = viewModelScope.launch {
            _state.value = DeadDropState.Broadcasting
            SonicVaultLogger.i("[DeadDrop] broadcasting ${payload.length} chars")

            try {
                val messageBytes = payload.toByteArray(Charsets.UTF_8)
                val bytesToSend: ByteArray
                var sas: String? = null

                if (_useSecureHandshake.value && _sessionCode.value.isBlank()) {
                    // Secure handshake: ECDH first, then Payload v2
                    val responseChannel = Channel<ByteArray>(1)
                    val listenJob = launch {
                        DeadDropReceiverService.listen(context.applicationContext, maxWindows = 0).collect { event ->
                            if (event is DeadDropListenEvent.EcdhPacket) {
                                val pkt = ecdhHandshake.parseEcdhPacket(event.data)
                                if (pkt?.type == EcdhHandshake.PacketType.ECDH_RESP) {
                                    responseChannel.trySend(pkt.publicKey)
                                }
                            }
                        }
                    }
                    _state.value = DeadDropState.AwaitingEcdhResponse
                    var handshakeResult: EcdhHandshake.HandshakeResult? = null
                    ecdhHandshake.initiateAsInitiator(
                        onTransmitRequired = { pkt ->
                            val pcm = withContext(Dispatchers.IO) {
                                GgwaveDataOverSound.encode(pkt, _protocol.value, applyFingerprintRandomization = _protocol.value == Protocol.ULTRASONIC && userPreferences.useAntiFingerprint)
                            }
                            if (pcm != null) playPcm(pcm)
                        },
                        responseChannel = responseChannel,
                        onComplete = { result -> handshakeResult = result },
                        onTimeout = {
                            listenJob.cancel()
                            _state.value = DeadDropState.Error("No receiver responded. Start receiver first.")
                        }
                    )
                    listenJob.cancel()
                    if (handshakeResult == null) return@launch
                    val result = handshakeResult!!
                    val nonce = nonceManager?.getAndIncrementSendNonce() ?: 0L
                    val binding = deviceBinding.getDeviceBindingHash()
                    val v2Packet = PayloadV2Builder.build(
                        messageBytes, result.sessionKey, nonce, binding, result.localEphemeralPubKey
                    )
                    if (v2Packet == null) {
                        _state.value = DeadDropState.Error("Payload v2 build failed.")
                        return@launch
                    }
                    bytesToSend = if (_useFountainMode.value) {
                        java.nio.ByteBuffer.allocate(2 + v2Packet.size).order(java.nio.ByteOrder.BIG_ENDIAN)
                            .putShort(v2Packet.size.toShort()).put(v2Packet).array()
                    } else {
                        ReedSolomonCodec.encode(v2Packet)
                    }
                    sas = result.sas
                    if (_useFountainMode.value) {
                        FountainTransmitter.transmitFountain(bytesToSend, _protocol.value, applyFingerprintRandomization = _protocol.value == Protocol.ULTRASONIC && userPreferences.useAntiFingerprint)
                    } else {
                        val useFast = false
                        val applyFp = _protocol.value == Protocol.ULTRASONIC && userPreferences.useAntiFingerprint
                        var pcm = withContext(Dispatchers.IO) {
                            GgwaveDataOverSound.encode(bytesToSend, _protocol.value, useAudibleFast = useFast, applyFingerprintRandomization = applyFp)
                        }
                        if (pcm == null) {
                            val fallback = if (_protocol.value == Protocol.ULTRASONIC) Protocol.AUDIBLE else Protocol.ULTRASONIC
                            pcm = withContext(Dispatchers.IO) {
                                GgwaveDataOverSound.encode(bytesToSend, fallback, useAudibleFast = useFast, applyFingerprintRandomization = applyFp)
                            }
                        }
                        if (pcm != null) playPcm(pcm)
                    }
                    _state.value = DeadDropState.BroadcastComplete(sas)
                    SonicVaultLogger.i("[DeadDrop] secure broadcast complete")
                    return@launch
                }

                bytesToSend = if (_sessionCode.value.isNotBlank()) {
                    val encrypted = DeadDropEncryptor.encryptWithPassphrase(messageBytes, _sessionCode.value)
                    if (encrypted == null) {
                        _state.value = DeadDropState.Error("Encryption failed.")
                        return@launch
                    }
                    ReedSolomonCodec.encode(encrypted)
                } else {
                    val plaintext = if (nonceManager != null) {
                        val nonce = nonceManager.getAndIncrementSendNonce()
                        val binding = deviceBinding.getDeviceBindingHash()
                        ByteArray(ECDH_HEADER_BYTES + messageBytes.size).apply {
                            java.nio.ByteBuffer.wrap(this).order(java.nio.ByteOrder.BIG_ENDIAN).putLong(nonce)
                            binding.copyInto(this, NONCE_BYTES)
                            messageBytes.copyInto(this, ECDH_HEADER_BYTES)
                        }
                    } else {
                        messageBytes
                    }
                    val keyPair = DeadDropEncryptor.generateEphemeralKeyPair()
                    val encrypted = DeadDropEncryptor.encryptForBroadcast(plaintext, keyPair)
                    if (encrypted == null) {
                        _state.value = DeadDropState.Error("Encryption failed.")
                        return@launch
                    }
                    ReedSolomonCodec.encode(encrypted)
                }

                if (_useFountainMode.value && _sessionCode.value.isBlank()) {
                    val sized = java.nio.ByteBuffer.allocate(2 + bytesToSend.size).order(java.nio.ByteOrder.BIG_ENDIAN)
                        .putShort(bytesToSend.size.toShort()).put(bytesToSend).array()
                    FountainTransmitter.transmitFountain(sized, _protocol.value, applyFingerprintRandomization = _protocol.value == Protocol.ULTRASONIC && userPreferences.useAntiFingerprint)
                    sas = if (nonceManager != null) SasGenerator.generate(deviceBinding.getDeviceBindingHash()) else null
                } else {
                    val useFast = _sessionCode.value.isNotBlank()
                    val applyFp = _protocol.value == Protocol.ULTRASONIC && userPreferences.useAntiFingerprint
                    var pcm = withContext(Dispatchers.IO) {
                        GgwaveDataOverSound.encode(bytesToSend, _protocol.value, useAudibleFast = useFast, applyFingerprintRandomization = applyFp)
                    }
                    if (pcm == null) {
                        SonicVaultLogger.w("[DeadDrop] encode failed; retrying fallback")
                        val fallback = if (_protocol.value == Protocol.ULTRASONIC) Protocol.AUDIBLE else Protocol.ULTRASONIC
                        pcm = withContext(Dispatchers.IO) {
                            GgwaveDataOverSound.encode(bytesToSend, fallback, useAudibleFast = useFast, applyFingerprintRandomization = applyFp)
                        }
                    }
                    if (pcm == null) {
                        _state.value = DeadDropState.Error("Audio encoding failed.")
                        return@launch
                    }
                    playPcm(pcm)
                    sas = if (_sessionCode.value.isBlank() && nonceManager != null) {
                        SasGenerator.generate(deviceBinding.getDeviceBindingHash())
                    } else null
                }
                _state.value = if (sas != null) DeadDropState.BroadcastComplete(sas) else DeadDropState.Idle
                SonicVaultLogger.i("[DeadDrop] broadcast complete")
            } catch (e: Exception) {
                SonicVaultLogger.e("[DeadDrop] broadcast failed", e)
                _state.value = DeadDropState.Error("Could not send. Move devices closer and try again.")
            }
        }
    }

    /**
     * Plain broadcast: encode payload via ggwave and play immediately.
     * No ECDH, no encryption, no fountain — for Solana Pay URIs and other
     * one-way broadcasts that any receiver (including web) can decode.
     */
    fun broadcastPlain(payload: String) {
        if (job?.isActive == true) return

        job = viewModelScope.launch {
            _state.value = DeadDropState.Broadcasting
            SonicVaultLogger.i("[DeadDrop] plain broadcast ${payload.length} chars")

            try {
                val messageBytes = payload.toByteArray(Charsets.UTF_8)
                val applyFp = _protocol.value == Protocol.ULTRASONIC && userPreferences.useAntiFingerprint
                var pcm = withContext(Dispatchers.IO) {
                    GgwaveDataOverSound.encode(messageBytes, _protocol.value, applyFingerprintRandomization = applyFp)
                }
                if (pcm == null) {
                    SonicVaultLogger.w("[DeadDrop] plain encode failed; trying fallback protocol")
                    val fallback = if (_protocol.value == Protocol.ULTRASONIC) Protocol.AUDIBLE else Protocol.ULTRASONIC
                    pcm = withContext(Dispatchers.IO) {
                        GgwaveDataOverSound.encode(messageBytes, fallback, applyFingerprintRandomization = applyFp)
                    }
                }
                if (pcm == null) {
                    _state.value = DeadDropState.Error("Audio encoding failed.")
                    return@launch
                }
                playPcm(pcm)
                _state.value = DeadDropState.BroadcastComplete("PLAIN")
                SonicVaultLogger.i("[DeadDrop] plain broadcast complete")
            } catch (e: Exception) {
                SonicVaultLogger.e("[DeadDrop] plain broadcast failed", e)
                _state.value = DeadDropState.Error("Could not send. Move devices closer and try again.")
            }
        }
    }

    private suspend fun playPcm(pcm: ShortArray) = withContext(Dispatchers.IO) {
        val bufferSize = android.media.AudioTrack.getMinBufferSize(
            GgwaveDataOverSound.SAMPLE_RATE,
            android.media.AudioFormat.CHANNEL_OUT_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(pcm.size * 2)
        val audioTrack = android.media.AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(GgwaveDataOverSound.SAMPLE_RATE)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(android.media.AudioTrack.MODE_STATIC)
            .build()
        audioTrack.write(pcm, 0, pcm.size)
        audioTrack.play()
        val durationMs = (pcm.size * 1000L) / GgwaveDataOverSound.SAMPLE_RATE
        kotlinx.coroutines.delay(durationMs + 200)
        audioTrack.release()
    }

    /** Stored ECDH session key when in secure handshake responder mode (awaiting payload). */
    private var pendingHandshakeResult: EcdhHandshake.HandshakeResult? = null

    /** Fountain decoder for noise-resistant mode; total bytes from first droplet or estimate. */
    private var fountainDecoder: FountainDecoder? = null

    /**
     * Starts listening for Dead Drop broadcasts.
     *
     * @param context Application or Activity context for AudioRecord source selection
     */
    fun startListening(context: Context) {
        if (job?.isActive == true) return

        job = viewModelScope.launch {
            _state.value = DeadDropState.Listening
            var receivedAny = false
            pendingHandshakeResult = null
            fountainDecoder = null

            UnifiedAcousticReceiver.listen(context, maxWindows = 6).collect { event ->
                when (event) {
                    is UnifiedReceiveEvent.EcdhPacket -> {
                        val pkt = ecdhHandshake.parseEcdhPacket(event.data)
                        if (pkt?.type == EcdhHandshake.PacketType.ECDH_INIT && _useSecureHandshake.value) {
                            ecdhHandshake.initiateAsResponder(
                                receivedInitPacket = event.data,
                        onTransmitRequired = { respPkt ->
                            val pcm = withContext(Dispatchers.IO) {
                                GgwaveDataOverSound.encode(respPkt, _protocol.value, applyFingerprintRandomization = _protocol.value == Protocol.ULTRASONIC && userPreferences.useAntiFingerprint)
                            }
                            if (pcm != null) playPcm(pcm)
                        },
                                onComplete = { result ->
                                    pendingHandshakeResult = result
                                    SonicVaultLogger.i("[DeadDrop] ECDH complete, awaiting payload")
                                }
                            )
                        }
                    }
                    is UnifiedReceiveEvent.FountainDroplet -> {
                        val droplet = if (event.data.size >= 19) event.data.copyOfRange(1, 19) else return@collect
                        if (fountainDecoder == null) {
                            fountainDecoder = fountainCodec.createDecoder(1024, 16)
                        }
                        val decoded = fountainDecoder!!.feedDroplet(droplet)
                        if (decoded != null) {
                            fountainDecoder = null
                            val payload = if (decoded.size >= 2) {
                                val len = java.nio.ByteBuffer.wrap(decoded, 0, 2).order(java.nio.ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
                                if (len in 1..decoded.size - 2) decoded.copyOfRange(2, 2 + len) else decoded
                            } else decoded
                            processReceivedPayload(payload, receivedAny).let { receivedAny = it || receivedAny }
                        }
                    }
                    is UnifiedReceiveEvent.DeadDropPacket -> {
                        val data = event.data
                        if (PayloadV2Parser.isV2Packet(data)) {
                            val result = pendingHandshakeResult
                            if (result != null) {
                                val plaintext = PayloadV2Parser.parse(data, result.sessionKey)
                                if (plaintext != null) {
                                    pendingHandshakeResult = null
                                    val msg = String(plaintext, Charsets.UTF_8)
                                    _state.value = DeadDropState.Received(msg, result.sas)
                                    receivedAny = true
                                }
                            }
                        } else {
                            var plaintext: ByteArray? = DeadDropEncryptor.decryptBroadcast(data)
                            val isPassphrase = plaintext == null && DeadDropEncryptor.isPassphrasePacket(data) && _sessionCode.value.isNotBlank()
                            if (plaintext == null && isPassphrase) {
                                plaintext = DeadDropEncryptor.decryptWithPassphrase(data, _sessionCode.value)
                            }
                            if (plaintext != null) {
                                val (message, sas) = if (DeadDropEncryptor.isDeadDropPacket(data) &&
                                    nonceManager != null && plaintext.size >= ECDH_HEADER_BYTES
                                ) {
                                    val nonce = java.nio.ByteBuffer.wrap(plaintext, 0, NONCE_BYTES)
                                        .order(java.nio.ByteOrder.BIG_ENDIAN).long
                                    val senderBinding = plaintext.copyOfRange(NONCE_BYTES, ECDH_HEADER_BYTES)
                                    if (!nonceManager.validateAndStoreReceivedNonce(senderBinding, nonce)) {
                                        SonicVaultLogger.w("[DeadDrop] replay rejected")
                                        null to null
                                    } else {
                                        val msg = String(plaintext, ECDH_HEADER_BYTES, plaintext.size - ECDH_HEADER_BYTES, Charsets.UTF_8)
                                        val s = SasGenerator.generate(senderBinding)
                                        msg to s
                                    }
                                } else {
                                    String(plaintext, Charsets.UTF_8) to null
                                }
                                if (message != null) {
                                    _state.value = DeadDropState.Received(message, sas)
                                    receivedAny = true
                                }
                            }
                        }
                    }
                    is UnifiedReceiveEvent.SolanaPayRequest -> {
                        _state.value = DeadDropState.ReceivedPaymentRequest(event.uri)
                        receivedAny = true
                    }
                    is UnifiedReceiveEvent.ColdSignTx -> {
                        _state.value = DeadDropState.ReceivedTxToSign(event.txBytes)
                        receivedAny = true
                    }
                    is UnifiedReceiveEvent.Done -> {
                        if (!receivedAny && _state.value is DeadDropState.Listening) {
                            _state.value = DeadDropState.Error(
                                if (event.micUnavailable)
                                    "Microphone unavailable. Use a physical device for audio capture."
                                else
                                    "No broadcast received. Try again."
                            )
                        }
                    }
                }
            }
        }
    }

    private fun processReceivedPayload(decoded: ByteArray, receivedAny: Boolean): Boolean {
        val rsDecoded = ReedSolomonCodec.decode(decoded) ?: decoded
        if (PayloadV2Parser.isV2Packet(rsDecoded)) {
            val result = pendingHandshakeResult
            if (result != null) {
                val plaintext = PayloadV2Parser.parse(rsDecoded, result.sessionKey)
                if (plaintext != null) {
                    pendingHandshakeResult = null
                    val msg = String(plaintext, Charsets.UTF_8)
                    _state.value = DeadDropState.Received(msg, result.sas)
                    return true
                }
            }
        } else {
            var plaintext: ByteArray? = DeadDropEncryptor.decryptBroadcast(rsDecoded)
            val isPassphrase = plaintext == null && DeadDropEncryptor.isPassphrasePacket(rsDecoded) && _sessionCode.value.isNotBlank()
            if (plaintext == null && isPassphrase) {
                plaintext = DeadDropEncryptor.decryptWithPassphrase(rsDecoded, _sessionCode.value)
            }
            if (plaintext != null) {
                val (message, sas) = if (DeadDropEncryptor.isDeadDropPacket(rsDecoded) &&
                    nonceManager != null && plaintext.size >= ECDH_HEADER_BYTES
                ) {
                    val nonce = java.nio.ByteBuffer.wrap(plaintext, 0, NONCE_BYTES)
                        .order(java.nio.ByteOrder.BIG_ENDIAN).long
                    val senderBinding = plaintext.copyOfRange(NONCE_BYTES, ECDH_HEADER_BYTES)
                    if (!nonceManager.validateAndStoreReceivedNonce(senderBinding, nonce)) {
                        null to null
                    } else {
                        val msg = String(plaintext, ECDH_HEADER_BYTES, plaintext.size - ECDH_HEADER_BYTES, Charsets.UTF_8)
                        val s = SasGenerator.generate(senderBinding)
                        msg to s
                    }
                } else {
                    String(plaintext, Charsets.UTF_8) to null
                }
                if (message != null) {
                    _state.value = DeadDropState.Received(message, sas)
                    return true
                }
            }
        }
        return receivedAny
    }

    fun stop() {
        job?.cancel()
        _state.value = DeadDropState.Idle
    }

    fun reset() {
        stop()
    }

    override fun onCleared() {
        super.onCleared()
        job?.cancel()
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
   UI COMPOSABLES — Rams: "Less, but better." No protocol selector (always
   ULTRASONIC_FASTEST). TX/RX toggle is the top element. No decoration.
   ═══════════════════════════════════════════════════════════════════════════ */

/** Acoustic waveform: 7 bars with staggered animation. Functional, not decorative. */
@Composable
private fun AcousticVisualizer(isActive: Boolean, isSuccess: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wave")
    val delays = listOf(0, 100, 200, 300, 200, 100, 0)
    val maxPercents = listOf(0.4f, 0.6f, 0.8f, 1.0f, 0.8f, 0.6f, 0.4f)

    Row(
        modifier = modifier
            .height(72.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        maxPercents.forEachIndexed { index, maxPct ->
            if (index > 0) Spacer(modifier = Modifier.width(6.dp))

            val scale by transition.animateFloat(
                initialValue = 0.15f,
                targetValue = maxPct,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(delays[index])
                ),
                label = "bar_$index"
            )

            val barHeight = when {
                isActive -> 72.dp * scale
                isSuccess -> 72.dp
                else -> 2.dp
            }
            val barColor = when {
                isActive -> MaterialTheme.colorScheme.primary
                isSuccess -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            }

            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(barHeight)
                    .background(barColor)
            )
        }
    }
}

/** Minimal underline-only text field. Rams: no decorative borders, just function. */
@Composable
private fun UnderlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    onClear: (() -> Unit)? = null
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp)
                    .then(if (onClear != null) Modifier.padding(end = 40.dp) else Modifier),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    letterSpacing = 2.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (onClear != null) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(TouchTargetMin)
                ) {
                    Text(
                        "✕",
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = outlineColor)
    }
}

/** Key-value row for the receive readout panel. Monospace, uppercase, compact. */
@Composable
private fun ReadoutRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label.uppercase(),
            style = LabelUppercaseStyle.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Text(
            value,
            style = LabelUppercaseStyle.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
            color = valueColor
        )
    }
}

/**
 * Unified TRANSMIT / RECEIVE screen. Replaces QR codes with acoustic data transfer.
 *
 * TX mode: SOL Pay, SKR Tip, Cold Sign, cNFT Drop — each with purpose-built form.
 * RX mode: hardware scanner aesthetic — band/mod metadata, readout panel, status icon.
 *
 * No protocol selector: always ULTRASONIC_FASTEST. Rams: "as little design as possible."
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadDropScreen(
    onBack: () -> Unit,
    initialMode: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as SonicVaultApplication
    val view = LocalView.current

    /* ── ViewModels ── */
    val viewModel: DeadDropViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val nonceMgr = com.sonicvault.app.di.AppModule.provideDeadDropNonceManager(context.applicationContext)
                val binding = com.sonicvault.app.di.AppModule.provideDeviceBinding(context)
                val ecdh = com.sonicvault.app.di.AppModule.provideEcdhHandshake()
                val fountain = com.sonicvault.app.di.AppModule.provideFountainCodec()
                val userPrefs = com.sonicvault.app.di.AppModule.provideUserPreferences(context.applicationContext)
                return DeadDropViewModel(nonceMgr, binding, ecdh, fountain, userPrefs) as T
            }
        }
    )
    val state by viewModel.state.collectAsState()

    val hotViewModelFactory = remember(app) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SonicSafeHotViewModel(app.noncePoolManager, app.userPreferences) as T
        }
    }
    val hotViewModel: SonicSafeHotViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = hotViewModelFactory)
    val hotState by hotViewModel.state.collectAsState()

    val coldViewModelFactory = remember(app) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SonicSafeViewModel(app.userPreferences) as T
        }
    }
    val coldViewModel: SonicSafeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = coldViewModelFactory)
    val sonicRequestViewModel: SonicRequestViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    /* ── Local state ── */
    var mode by remember(initialMode) { mutableStateOf(if (initialMode == "receive") "RX" else "TX") }
    var txType by remember { mutableStateOf(TxType.SOL_PAY) }
    var amount by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var eventId by remember { mutableStateOf("") }

    /* ── Derived action state (unifies DeadDrop + SonicSafe VMs) ── */
    val isIdle = when {
        mode == "TX" && txType == TxType.COLD_SIGN ->
            hotState is SonicSafeHotViewModel.State.Idle && state is DeadDropState.Idle
        mode == "TX" -> state is DeadDropState.Idle
        else -> state is DeadDropState.Idle
    }
    val isActive = when {
        mode == "TX" && txType == TxType.COLD_SIGN ->
            hotState is SonicSafeHotViewModel.State.Building ||
            hotState is SonicSafeHotViewModel.State.Transmitting ||
            hotState is SonicSafeHotViewModel.State.Listening
        mode == "TX" -> state is DeadDropState.Broadcasting || state is DeadDropState.AwaitingEcdhResponse
        else -> state is DeadDropState.Listening
    }
    val isSuccess = when {
        mode == "TX" && txType == TxType.COLD_SIGN -> hotState is SonicSafeHotViewModel.State.Success
        mode == "TX" -> state is DeadDropState.BroadcastComplete
        else -> state is DeadDropState.Received ||
                state is DeadDropState.ReceivedPaymentRequest ||
                state is DeadDropState.ReceivedTxToSign
    }

    /* ── Permissions ── */
    val requestRecordAudio = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startListening(context.applicationContext)
    }

    BackHandler { viewModel.stop(); hotViewModel.reset(); onBack() }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    val outlineColor = MaterialTheme.colorScheme.outline
    val modeSelectorEnabled = isIdle

    /* ═══════════════════════════════════════════════════════════════
       LAYOUT: Toggle → Content (scrollable) → Visualizer → Button
       ═══════════════════════════════════════════════════════════════ */
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding()
    ) {

        /* ── TX / RX TOGGLE — full width, high contrast, no rounding ── */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = outlineColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp)
                    .background(
                        if (mode == "TX") MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.surface
                    )
                    .then(
                        if (modeSelectorEnabled) Modifier.clickable { mode = "TX"; viewModel.stop() }
                        else Modifier.graphicsLayer { alpha = 0.5f }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "TRANSMIT",
                    style = LabelUppercaseStyle.copy(fontSize = 14.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold),
                    color = if (mode == "TX") MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp)
                    .background(
                        if (mode == "RX") MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.surface
                    )
                    .then(
                        if (modeSelectorEnabled) Modifier.clickable { mode = "RX"; viewModel.stop() }
                        else Modifier.graphicsLayer { alpha = 0.5f }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "RECEIVE",
                    style = LabelUppercaseStyle.copy(fontSize = 14.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold),
                    color = if (mode == "RX") MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }

        /* ── SCROLLABLE CONTENT ── */
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (mode == "TX") {
                /* ═══════════════════ TRANSMIT MODE ═══════════════════ */

                /* Hero amount / placeholder */
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (txType.hasAmount) {
                        BasicTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                fontSize = 64.sp,
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            enabled = isIdle,
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (amount.isEmpty()) {
                                        Text(
                                            "0.00",
                                            style = TextStyle(
                                                fontSize = 64.sp,
                                                fontWeight = FontWeight.Light,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                                textAlign = TextAlign.Center
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                txType.icon,
                                style = TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "TOTAL ${txType.unit}",
                                style = LabelUppercaseStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    } else {
                        Text(
                            "---",
                            style = TextStyle(
                                fontSize = 64.sp,
                                fontWeight = FontWeight.Light,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 8.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            txType.awaitingLabel.uppercase(),
                            style = LabelUppercaseStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }

                /* TX Type 2×2 grid: 1px gap = outline color showing through */
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(outlineColor)
                ) {
                    HorizontalDivider(thickness = 0.dp, color = outlineColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        TxType.entries.take(2).forEach { type ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(80.dp)
                                    .background(
                                        if (txType == type) MaterialTheme.colorScheme.surfaceContainer
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable(enabled = isIdle) { txType = type },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        type.icon,
                                        style = TextStyle(fontSize = 20.sp, fontFamily = FontFamily.Monospace),
                                        color = if (txType == type) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        type.label.uppercase(),
                                        style = LabelUppercaseStyle,
                                        color = if (txType == type) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                }
                            }
                            if (type == TxType.entries.take(2).first()) {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(outlineColor)
                                )
                            }
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = outlineColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        TxType.entries.drop(2).forEachIndexed { index, type ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(80.dp)
                                    .background(
                                        if (txType == type) MaterialTheme.colorScheme.surfaceContainer
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable(enabled = isIdle) { txType = type },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        type.icon,
                                        style = TextStyle(fontSize = 20.sp, fontFamily = FontFamily.Monospace),
                                        color = if (txType == type) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        type.label.uppercase(),
                                        style = LabelUppercaseStyle,
                                        color = if (txType == type) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                }
                            }
                            if (index == 0) {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(outlineColor)
                                )
                            }
                        }
                    }
                }

                /* Dynamic form fields */
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md.dp)
                        .padding(top = Spacing.md.dp)
                ) {
                    if (txType.hasEventId) {
                        UnderlineTextField(
                            value = eventId,
                            onValueChange = { eventId = it },
                            placeholder = "EVENT DROP ID (e.g. MONOLITH_2026)",
                            enabled = isIdle
                        )
                    }
                    if (txType.hasRecipient) {
                        UnderlineTextField(
                            value = recipient,
                            onValueChange = { recipient = it },
                            placeholder = if (txType == TxType.COLD_SIGN) "UNSIGNED TX PAYLOAD" else "RECIPIENT ADDRESS",
                            enabled = isIdle,
                            onClear = if (recipient.isNotBlank() && isIdle) {
                                { recipient = "" }
                            } else null
                        )
                    }
                    if (txType.hasMemo) {
                        UnderlineTextField(
                            value = memo,
                            onValueChange = { memo = it },
                            placeholder = "MEMO (OPTIONAL)",
                            enabled = isIdle
                        )
                    }
                }

                /* State-dependent TX feedback */
                if (txType == TxType.COLD_SIGN) {
                    when (val hs = hotState) {
                        is SonicSafeHotViewModel.State.Building,
                        is SonicSafeHotViewModel.State.Transmitting,
                        is SonicSafeHotViewModel.State.Listening -> {
                            val statusText = when (hs) {
                                is SonicSafeHotViewModel.State.Building -> "Building transaction…"
                                is SonicSafeHotViewModel.State.Transmitting -> "Transmitting to cold signer…"
                                is SonicSafeHotViewModel.State.Listening -> "Waiting for signed TX…"
                                else -> ""
                            }
                            Spacer(modifier = Modifier.height(Spacing.md.dp))
                            Text(
                                statusText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md.dp)
                            )
                        }
                        is SonicSafeHotViewModel.State.Success -> {
                            LaunchedEffect(hs) { view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp)
                            ) {
                                Text("Broadcast successful", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Text("Sig: ${hs.signature.take(24)}…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(Spacing.xs.dp))
                                OutlinedButton(onClick = {
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(hs.explorerUrl)))
                                }) { Text("VIEW IN EXPLORER") }
                                OutlinedButton(onClick = { hotViewModel.reset() }) { Text("DONE") }
                            }
                        }
                        is SonicSafeHotViewModel.State.Error -> {
                            LaunchedEffect(hs) { view.performHapticFeedback(HapticFeedbackConstantsCompat.REJECT) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(hs.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                                OutlinedButton(onClick = { hotViewModel.reset() }) { Text("TRY AGAIN") }
                            }
                        }
                        else -> {}
                    }
                } else {
                    when (val s = state) {
                        is DeadDropState.BroadcastComplete -> {
                            LaunchedEffect(s) { view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Transfer complete", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                if (s.sas != "PLAIN") {
                                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RectangleShape,
                                        border = BorderStroke(1.dp, outlineColor),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                        Column(modifier = Modifier.padding(Spacing.md.dp)) {
                                            Text("VERIFY CODE", style = LabelUppercaseStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.height(Spacing.xs.dp))
                                            Text(s.sas, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                                OutlinedButton(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) { Text("DONE") }
                            }
                        }
                        is DeadDropState.Broadcasting, is DeadDropState.AwaitingEcdhResponse -> {
                            Spacer(modifier = Modifier.height(Spacing.md.dp))
                            Text(
                                if (state is DeadDropState.AwaitingEcdhResponse) "Waiting for receiver…" else "Broadcasting…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md.dp)
                            )
                        }
                        is DeadDropState.Error -> {
                            LaunchedEffect(s) { view.performHapticFeedback(HapticFeedbackConstantsCompat.REJECT) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                                OutlinedButton(onClick = { viewModel.reset() }) { Text("TRY AGAIN") }
                            }
                        }
                        else -> {}
                    }
                }

            } else {
                /* ═══════════════════ RECEIVE MODE ═══════════════════ */

                /* Band / Mod metadata */
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md.dp, vertical = Spacing.md.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "BAND: 15-19.5 KHZ",
                        style = LabelUppercaseStyle.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        "MOD: MFSK",
                        style = LabelUppercaseStyle.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }

                when (val s = state) {
                    is DeadDropState.Idle -> {
                        /* Status icon + label */
                        Spacer(modifier = Modifier.height(Spacing.xl.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .border(2.dp, outlineColor.copy(alpha = 0.3f), RectangleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Mic,
                                    contentDescription = "Microphone",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.lg.dp))
                            Text(
                                "SCANNING",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    letterSpacing = 3.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        /* Readout panel */
                        Spacer(modifier = Modifier.height(Spacing.lg.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.xl.dp),
                            shape = RectangleShape,
                            border = BorderStroke(1.dp, outlineColor),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Column(modifier = Modifier.padding(Spacing.sm.dp)) {
                                ReadoutRow("Status", "AWAITING SIGNAL")
                                HorizontalDivider(thickness = 1.dp, color = outlineColor.copy(alpha = 0.1f))
                                ReadoutRow("Protocol", "GGWAVE / RS-ECC")
                                HorizontalDivider(thickness = 1.dp, color = outlineColor.copy(alpha = 0.1f))
                                ReadoutRow("Buffer", "0%")
                            }
                        }
                    }

                    is DeadDropState.Listening -> {
                        Spacer(modifier = Modifier.height(Spacing.xl.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RectangleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Mic,
                                    contentDescription = "Listening",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.lg.dp))
                            Text(
                                "CARRIER LOCKED",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    letterSpacing = 3.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.lg.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.xl.dp),
                            shape = RectangleShape,
                            border = BorderStroke(1.dp, outlineColor),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Column(modifier = Modifier.padding(Spacing.sm.dp)) {
                                ReadoutRow("Status", "RECEIVING…", MaterialTheme.colorScheme.primary)
                                HorizontalDivider(thickness = 1.dp, color = outlineColor.copy(alpha = 0.1f))
                                ReadoutRow("Protocol", "GGWAVE / RS-ECC")
                                HorizontalDivider(thickness = 1.dp, color = outlineColor.copy(alpha = 0.1f))
                                ReadoutRow("Buffer", "–")
                            }
                        }
                    }

                    is DeadDropState.Received -> {
                        LaunchedEffect(s) { view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM) }
                        Spacer(modifier = Modifier.height(Spacing.xl.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.onSurface, RectangleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Decoded",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.lg.dp))
                            Text(
                                "PAYLOAD DECODED",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    letterSpacing = 3.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        /* Readout panel — complete */
                        Spacer(modifier = Modifier.height(Spacing.lg.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.xl.dp),
                            shape = RectangleShape,
                            border = BorderStroke(1.dp, outlineColor),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Column(modifier = Modifier.padding(Spacing.sm.dp)) {
                                ReadoutRow("Status", "COMPLETE", MaterialTheme.colorScheme.onSurface)
                                HorizontalDivider(thickness = 1.dp, color = outlineColor.copy(alpha = 0.1f))
                                ReadoutRow("Protocol", "GGWAVE / RS-ECC")
                                HorizontalDivider(thickness = 1.dp, color = outlineColor.copy(alpha = 0.1f))
                                ReadoutRow("Buffer", "100%")
                            }
                        }

                        /* Decoded payload */
                        Spacer(modifier = Modifier.height(Spacing.md.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md.dp),
                            shape = RectangleShape,
                            border = BorderStroke(1.dp, outlineColor),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(modifier = Modifier.padding(Spacing.md.dp)) {
                                Text("RECEIVED", style = LabelUppercaseStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(Spacing.xs.dp))
                                Text(s.payload, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                s.sas?.let { sas ->
                                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                                    Text("VERIFY CODE", style = LabelUppercaseStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(sas, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    is DeadDropState.ReceivedPaymentRequest -> {
                        LaunchedEffect(s) {
                            sonicRequestViewModel.setReceivedUri(s.uri)
                            view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM)
                        }
                        Spacer(modifier = Modifier.height(Spacing.xl.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.onSurface, RectangleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = "Decoded", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(modifier = Modifier.height(Spacing.lg.dp))
                            Text(
                                "PAYMENT REQUEST",
                                style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                            )
                        }

                        /* Payment approval bottom sheet */
                        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        var showPaymentSheet by remember(s) { mutableStateOf(true) }
                        if (showPaymentSheet) {
                            ModalBottomSheet(
                                onDismissRequest = {
                                    showPaymentSheet = false
                                    sonicRequestViewModel.decline()
                                    viewModel.reset()
                                },
                                sheetState = sheetState
                            ) {
                                SonicRequestSheet(
                                    viewModel = sonicRequestViewModel,
                                    sheetState = sheetState,
                                    onDismiss = {
                                        showPaymentSheet = false
                                        viewModel.reset()
                                    }
                                )
                            }
                        }
                    }

                    is DeadDropState.ReceivedTxToSign -> {
                        LaunchedEffect(s) {
                            coldViewModel.setReceivedTx(s.txBytes)
                            view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM)
                        }
                        Spacer(modifier = Modifier.height(Spacing.md.dp))
                        Text(
                            "TRANSACTION RECEIVED",
                            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm.dp))
                        ColdSignerScreen(
                            viewModel = coldViewModel,
                            onBack = { viewModel.reset() },
                            embedded = true
                        )
                    }

                    is DeadDropState.Error -> {
                        LaunchedEffect(s) { view.performHapticFeedback(HapticFeedbackConstantsCompat.REJECT) }
                        Spacer(modifier = Modifier.height(Spacing.xl.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.error, RectangleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✕", style = TextStyle(fontSize = 24.sp, color = MaterialTheme.colorScheme.error))
                            }
                            Spacer(modifier = Modifier.height(Spacing.lg.dp))
                            Text(
                                s.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = Spacing.md.dp)
                            )
                        }
                    }

                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md.dp))
        }

        /* ── ACOUSTIC VISUALIZER ── */
        AcousticVisualizer(
            isActive = isActive,
            isSuccess = isSuccess,
            modifier = Modifier.padding(bottom = Spacing.md.dp)
        )

        /* ── BOTTOM ACTION BUTTON ── */
        val canInitiate = isIdle && when {
            mode == "RX" -> true
            txType == TxType.SOL_PAY || txType == TxType.SKR_TIP -> amount.isNotBlank() && recipient.isNotBlank()
            txType == TxType.COLD_SIGN -> recipient.isNotBlank() && amount.isNotBlank()
            txType == TxType.CNFT_DROP -> eventId.isNotBlank()
            else -> false
        }
        val buttonEnabled = canInitiate || isActive || isSuccess
        val buttonLabel = when {
            isSuccess -> if (mode == "TX") "SENT" else "DECODED"
            isActive -> if (mode == "RX") "STOP" else "TRANSMITTING…"
            else -> if (mode == "TX") "INITIATE" else "START LISTENER"
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md.dp)
                .padding(bottom = Spacing.md.dp)
        ) {
            Button(
                onClick = {
                    when {
                        isSuccess -> {
                            viewModel.reset()
                            hotViewModel.reset()
                            coldViewModel.reset()
                        }
                        isActive && mode == "RX" -> viewModel.stop()
                        isActive -> {} /* TX in progress: no-op */
                        mode == "RX" -> {
                            when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                PackageManager.PERMISSION_GRANTED -> viewModel.startListening(context.applicationContext)
                                else -> requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                        mode == "TX" -> {
                            when (txType) {
                                TxType.SOL_PAY -> {
                                    val ts = System.currentTimeMillis() / 1000
                                    val fullMemo = listOfNotNull(
                                        memo.ifBlank { null }, "ts:$ts"
                                    ).joinToString(" ")
                                    val uri = SolanaPayUri(
                                        recipient = recipient.trim(),
                                        amount = amount.toDoubleOrNull(),
                                        label = "Kyma",
                                        message = null,
                                        memo = fullMemo
                                    )
                                    // Plain broadcast — any device can decode the URI
                                    viewModel.broadcastPlain(String(uri.encode(), Charsets.UTF_8))
                                }
                                TxType.SKR_TIP -> {
                                    val ts = System.currentTimeMillis() / 1000
                                    val fullMemo = listOfNotNull(
                                        memo.ifBlank { null }, "ts:$ts"
                                    ).joinToString(" ")
                                    val uri = SolanaPayUri(
                                        recipient = recipient.trim(),
                                        amount = amount.toDoubleOrNull(),
                                        label = "Kyma SKR",
                                        message = null,
                                        memo = fullMemo,
                                        splToken = "SKRepMQET9dGvadwUiEmVi1pcSHaH8eak7FXbp6FTQ5R"
                                    )
                                    viewModel.broadcastPlain(String(uri.encode(), Charsets.UTF_8))
                                }
                                TxType.COLD_SIGN -> {
                                    val mainActivity = context as? com.sonicvault.app.MainActivity
                                    if (mainActivity != null && recipient.isNotBlank()) {
                                        val lamports = ((amount.toDoubleOrNull() ?: 0.0) * 1_000_000_000).toLong()
                                        if (lamports > 0) {
                                            hotViewModel.sendForSigning(
                                                sender = mainActivity.activityResultSender,
                                                appContext = context.applicationContext,
                                                recipient = recipient.trim(),
                                                lamports = lamports
                                            )
                                        }
                                    }
                                }
                                TxType.CNFT_DROP -> {
                                    viewModel.broadcastPlain(eventId.trim())
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RectangleShape,
                enabled = buttonEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isSuccess -> MaterialTheme.colorScheme.onSurface
                        isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    contentColor = when {
                        isSuccess -> MaterialTheme.colorScheme.surface
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surface
                    },
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
            ) {
                Text(
                    buttonLabel,
                    style = LabelUppercaseStyle.copy(fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                )
            }
        }
    }
}
