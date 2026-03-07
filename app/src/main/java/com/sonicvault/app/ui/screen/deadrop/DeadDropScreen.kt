package com.sonicvault.app.ui.screen.deadrop

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonicvault.app.ui.theme.TouchTargetMin
import com.sonicvault.app.ui.component.ConnectionState
import com.sonicvault.app.ui.component.SoundHandshakeIndicator
import com.sonicvault.app.ui.component.StatusBar
import com.sonicvault.app.ui.component.SuccessCelebration
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing
import com.sonicvault.app.SonicVaultApplication
import com.sonicvault.app.ui.screen.sonicsafe.ColdSignerScreen
import com.sonicvault.app.ui.screen.sonicrequest.SonicRequestSheet
import com.sonicvault.app.ui.screen.sonicrequest.SonicRequestViewModel
import com.sonicvault.app.ui.screen.sonicsafe.SendSolFormContent
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
                    sas = SasGenerator.generate(result.sessionKey, "SONICVAULT-ECDH-SAS")
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
                                    val sas = SasGenerator.generate(result.sessionKey, "SONICVAULT-ECDH-SAS")
                                    _state.value = DeadDropState.Received(msg, sas)
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
                    val sas = SasGenerator.generate(result.sessionKey, "SONICVAULT-ECDH-SAS")
                    _state.value = DeadDropState.Received(msg, sas)
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

/** Protocol row: Audible / Ultrasonic. Matches reference: bordered surface, filled selected tab. */
@Composable
private fun DeadDropProtocolRow(
    protocol: Protocol,
    onProtocolChange: (Protocol) -> Unit,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Protocol.entries.forEach { p ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = TouchTargetMin)
                        .background(
                            if (protocol == p) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
                        )
                        .then(
                            if (enabled) Modifier.clickable { onProtocolChange(p) }
                            else Modifier.graphicsLayer { alpha = 0.5f }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        p.label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (protocol == p) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * @param initialMode "transmit" | "receive" | null.
 *   When "transmit" → preselects Broadcast; when "receive" → preselects Listen.
 *   When null (e.g. from Settings) → user chooses. Title is "SOUND TRANSFER" when initialMode set, else "DEAD DROP".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadDropScreen(
    onBack: () -> Unit,
    initialMode: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as SonicVaultApplication
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
    val protocol by viewModel.protocol.collectAsState()
    
    val defaultMode = when (initialMode) {
        "transmit" -> "broadcast"
        "receive" -> "listen"
        else -> "broadcast"
    }
    var mode by remember(initialMode) { mutableStateOf(defaultMode) }
    
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
    

    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    /* Request RECORD_AUDIO when user taps RECEIVE; start listen only after grant. */
    val requestRecordAudio = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startListening(context.applicationContext)
    }

    /* Intercept system back so both TopAppBar and hardware/gesture back stop the job. */
    BackHandler { viewModel.stop(); onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SOUND TRANSFER", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.stop(); onBack() },
                        modifier = Modifier.sizeIn(minWidth = TouchTargetMin, minHeight = TouchTargetMin)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.md.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)
        ) {
            /* Ma: breathing room between top bar and mode selector. */
            Spacer(modifier = Modifier.height(Spacing.sm.dp))
            /* Mode selector: sharp black/white — matches reference mockup. Disabled during transmit/receive. */
            val modeSelectorEnabled = state !is DeadDropState.Broadcasting &&
                state !is DeadDropState.AwaitingEcdhResponse &&
                state !is DeadDropState.Listening &&
                hotState !is SonicSafeHotViewModel.State.Transmitting
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .background(
                                if (mode == "broadcast") MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.surface
                            )
                            .then(
                                if (modeSelectorEnabled) Modifier.clickable { mode = "broadcast" }
                                else Modifier.graphicsLayer { alpha = 0.5f }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "TRANSMIT",
                            style = LabelUppercaseStyle,
                            color = if (mode == "broadcast") MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .background(
                                if (mode == "listen") MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.surface
                            )
                            .then(
                                if (modeSelectorEnabled) Modifier.clickable { mode = "listen" }
                                else Modifier.graphicsLayer { alpha = 0.5f }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "RECEIVE",
                            style = LabelUppercaseStyle,
                            color = if (mode == "listen") MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            /* Handshake indicator — dot with rings. */
            val connectionState = when (state) {
                is DeadDropState.Idle -> ConnectionState.IDLE
                is DeadDropState.Broadcasting -> ConnectionState.BROADCASTING
                is DeadDropState.Listening -> ConnectionState.LISTENING
                is DeadDropState.AwaitingEcdhResponse -> ConnectionState.BROADCASTING
                is DeadDropState.BroadcastComplete -> ConnectionState.COMPLETE
                is DeadDropState.Received -> ConnectionState.COMPLETE
                is DeadDropState.ReceivedPaymentRequest -> ConnectionState.COMPLETE
                is DeadDropState.ReceivedTxToSign -> ConnectionState.COMPLETE
                is DeadDropState.Error -> ConnectionState.FAILED
            }
            SoundHandshakeIndicator(connectionState = connectionState)

            when (val s = state) {
                is DeadDropState.Idle -> {
                    if (mode == "broadcast") {
                        /* SEND SOL: single transmit view. Rams: one purpose, no mode switching. */
                        Text(
                            "Receiver: tap RECEIVE first. Then tap SEND. Keep phones close.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs.dp))
                        SendSolFormContent(
                            viewModel = hotViewModel,
                            compact = true,
                            primaryButtonLabel = "SEND",
                            contentAboveButton = {
                                val protocolEnabled = state !is DeadDropState.Broadcasting &&
                                    state !is DeadDropState.AwaitingEcdhResponse &&
                                    hotState !is SonicSafeHotViewModel.State.Transmitting
                                DeadDropProtocolRow(
                                    protocol = protocol,
                                    onProtocolChange = { viewModel.setProtocol(it) },
                                    enabled = protocolEnabled
                                )
                            }
                        )
                    } else {
                        /* Receive mode: clean, focused. Handshake + fountain default ON. */
                        Spacer(modifier = Modifier.height(Spacing.md.dp))
                        Text(
                            "Tap RECEIVE first, then have sender tap SEND. Keep phones close.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(Spacing.md.dp))
                        DeadDropProtocolRow(
                            protocol = protocol,
                            onProtocolChange = { viewModel.setProtocol(it) },
                            enabled = state !is DeadDropState.Listening
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm.dp))
                        Button(
                            onClick = {
                                when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                    PackageManager.PERMISSION_GRANTED -> viewModel.startListening(context.applicationContext)
                                    else -> requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            shape = RectangleShape
                        ) {
                            Text("RECEIVE")
                        }
                    }
                }

                is DeadDropState.Broadcasting -> {
                    StatusBar(status = "Broadcasting…", isActive = true, shimmer = true)
                }

                is DeadDropState.AwaitingEcdhResponse -> {
                    StatusBar(status = "Waiting for receiver…", isActive = true, shimmer = true)
                }

                is DeadDropState.BroadcastComplete -> {
                    SuccessCelebration(trigger = true)
                    StatusBar(status = "Transfer complete. Verify code with receiver.")
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(modifier = Modifier.padding(Spacing.md.dp)) {
                            Text("VERIFY CODE", style = LabelUppercaseStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(Spacing.xs.dp))
                            Text(s.sas, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
                        Text("DONE")
                    }
                }

                is DeadDropState.Listening -> {
                    /* Rams: animation IS the UI — centered rings + shimmer label convey state. */
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    StatusBar(status = "Listening…", isActive = true, shimmer = true)
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Button(
                        onClick = { viewModel.stop() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = RectangleShape
                    ) {
                        Text("STOP")
                    }
                }

                is DeadDropState.Received -> {
                    SuccessCelebration(trigger = true)
                    StatusBar(status = "Received broadcast")
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
                        Text("DONE")
                    }
                }

                is DeadDropState.ReceivedPaymentRequest -> {
                    LaunchedEffect(s) {
                        sonicRequestViewModel.setReceivedUri(s.uri)
                    }
                    SuccessCelebration(trigger = true)
                    StatusBar(status = "Payment request received")
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
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
                    }
                    SuccessCelebration(trigger = true)
                    StatusBar(status = "Transaction received")
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    ColdSignerScreen(
                        viewModel = coldViewModel,
                        onBack = { viewModel.reset() },
                        embedded = true
                    )
                    Text(
                        text = "Listen for messages or payments instead",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            coldViewModel.reset()
                            viewModel.reset()
                        }
                    )
                }

                is DeadDropState.Error -> {
                    StatusBar(status = s.message)
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
                        Text("TRY AGAIN")
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm.dp))
        }
    }
}
