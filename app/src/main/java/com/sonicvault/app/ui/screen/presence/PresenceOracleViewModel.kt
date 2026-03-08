package com.sonicvault.app.ui.screen.presence

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.sonicvault.app.data.solana.AcousticCertificate
import com.sonicvault.app.data.sound.AcousticChunkReceiver
import com.sonicvault.app.data.sound.AcousticTransmitter
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Arrays

/**
 * ViewModel for Acoustic Presence Oracle: listen for 40B payload (event_id + event_pubkey),
 * assemble 208-byte dual-signature certificate.
 *
 * States: idle | listening | received(eventId) | assembling | certReady(cert) | error
 * Demo: event_sig from hardcoded demo keypair; claimer_sig from MWA signMessages.
 */
class PresenceOracleViewModel : ViewModel() {

    sealed class State {
        data object Idle : State()
        data object Listening : State()
        data class Received(val eventId: Long, val eventPubkeyHex: String) : State()
        data object Assembling : State()
        data class CertReady(val cert: AcousticCertificate) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var receiveJob: Job? = null
    private var timeoutJob: Job? = null

    /** Last received payload: 8B event_id + 32B event_pubkey. */
    private var lastEventId: Long = 0L
    private var lastEventPubkey: ByteArray? = null
    private var lastDecodeTimestampMs: Long = 0L

    /** Fixed demo event keypair for hackathon (simulates organizer pre-sign). */
    private val demoEventPrivateKey: Ed25519PrivateKeyParameters by lazy {
        val seed = "SonicVault:PresenceOracle:DemoEventKey:v1".toByteArray(Charsets.UTF_8)
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(seed).copyOf(32)
        Ed25519PrivateKeyParameters(keyBytes)
    }

    /** Start listening for 40-byte acoustic payload (8B event_id + 32B event_pubkey). 30s timeout. */
    fun startListening(context: Context) {
        receiveJob?.cancel()
        timeoutJob?.cancel()
        _state.value = State.Listening

        receiveJob = AcousticChunkReceiver.receiveFlow(
            context,
            sessionIdFilter = null,
            rawPayloadSizes = setOf(40)
        )
            .onEach { payload ->
                timeoutJob?.cancel()
                if (payload.size >= 40) {
                    val eventId = ByteBuffer.wrap(payload, 0, 8).order(ByteOrder.BIG_ENDIAN).long
                    val eventPubkey = payload.copyOfRange(8, 40)
                    lastEventId = eventId
                    lastEventPubkey = eventPubkey
                    lastDecodeTimestampMs = System.currentTimeMillis()
                    val hex = eventPubkey.take(8).joinToString("") { "%02x".format(it) } + "…"
                    SonicVaultLogger.i("[PresenceOracleVM] received event_id=$eventId, event_pubkey=${hex}")
                    _state.value = State.Received(eventId, hex)
                }
            }
            .catch { e ->
                SonicVaultLogger.e("[PresenceOracleVM] receive error", e)
                _state.value = State.Error("Could not receive. Move devices closer.")
            }
            .launchIn(viewModelScope)

        timeoutJob = viewModelScope.launch {
            delay(30_000L)
            if (_state.value is State.Listening) {
                receiveJob?.cancel()
                receiveJob = null
                _state.value = State.Error("No event received. Ensure the transmitter is broadcasting.")
            }
        }
    }

    fun stopListening() {
        timeoutJob?.cancel()
        timeoutJob = null
        receiveJob?.cancel()
        receiveJob = null
        _state.value = State.Idle
    }

    /**
     * Assemble certificate: event_sig from demo key, claimer_sig from MWA signMessages.
     * Signs bytes 0–79 (eventId + claimerPubkey + decodeTimestampMs + eventPubkey).
     */
    fun assembleCertificate(sender: ActivityResultSender) {
        val eventPubkey = lastEventPubkey
        if (eventPubkey == null || lastEventId == 0L) return
        _state.value = State.Assembling

        viewModelScope.launch {
            try {
                val walletAdapter = MobileWalletAdapter(
                    connectionIdentity = ConnectionIdentity(
                        identityUri = Uri.parse("https://sonicvault.app"),
                        iconUri = Uri.parse("/icon.png"),
                        identityName = "SonicVault"
                    )
                )

                val result = walletAdapter.transact(sender, null) { authResult ->
                    val account = authResult.accounts.firstOrNull() ?: return@transact null
                    val claimerPubkey = account.publicKey
                    if (claimerPubkey.size != 32) return@transact null

                    val eventIdBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(lastEventId).array()
                    val preamble = ByteBuffer.allocate(80).order(ByteOrder.BIG_ENDIAN).apply {
                        put(eventIdBytes)
                        put(claimerPubkey)
                        putLong(lastDecodeTimestampMs)
                        put(eventPubkey)
                    }.array()

                    // Demo event key signs preamble (simulates organizer)
                    val eventSig = ByteArray(64)
                    try {
                        val signer = Ed25519Signer()
                        signer.init(true, demoEventPrivateKey)
                        signer.update(preamble, 0, preamble.size)
                        val sig = signer.generateSignature()
                        if (sig.size == 64) sig.copyInto(eventSig)
                    } catch (e: Exception) {
                        SonicVaultLogger.e("[PresenceOracleVM] demo event sign failed", e)
                        return@transact null
                    }

                    // Claimer signs via MWA signMessages
                    val signResult = signMessagesDetached(arrayOf(preamble), arrayOf(claimerPubkey))
                    val claimerSig = signResult?.messages?.firstOrNull()?.signatures?.firstOrNull()
                        ?: return@transact null
                    if (claimerSig.size != 64) return@transact null

                    val cert = AcousticCertificate(
                        eventId = eventIdBytes,
                        claimerPubkey = claimerPubkey,
                        decodeTimestampMs = lastDecodeTimestampMs,
                        eventPubkey = eventPubkey,
                        eventSig = eventSig,
                        claimerSig = claimerSig
                    )
                    cert
                }

                when (result) {
                    is TransactionResult.Success -> {
                        val cert = result.payload as? AcousticCertificate
                        if (cert != null) {
                            SonicVaultLogger.i("[PresenceOracleVM] certificate assembled, ${cert.serialize().size} bytes")
                            _state.value = State.CertReady(cert)
                        } else {
                            _state.value = State.Error("Could not assemble certificate.")
                        }
                    }
                    is TransactionResult.NoWalletFound -> _state.value = State.Error("No wallet found. Connect Seed Vault.")
                    is TransactionResult.Failure -> _state.value = State.Error(result.e.message ?: "Sign failed")
                }
            } catch (e: Exception) {
                SonicVaultLogger.e("[PresenceOracleVM] assembleCertificate failed", e)
                _state.value = State.Error("Certificate assembly failed.")
            }
        }
    }

    /** Broadcast 40-byte payload (8B event_id + 32B event_pubkey) for other Seekers to receive. */
    fun broadcastPresenceEvent(context: Context, eventId: Long, eventPubkey: ByteArray) {
        if (eventPubkey.size != 32) return
        viewModelScope.launch {
            try {
                val payload = ByteBuffer.allocate(40).order(ByteOrder.BIG_ENDIAN)
                    .putLong(eventId)
                    .put(eventPubkey)
                    .array()
                withContext(Dispatchers.IO) {
                    AcousticTransmitter.transmitSingle(payload)
                }
                SonicVaultLogger.i("[PresenceOracleVM] broadcast event_id=$eventId")
            } catch (e: Exception) {
                SonicVaultLogger.e("[PresenceOracleVM] broadcast failed", e)
                _state.value = State.Error("Broadcast failed")
            }
        }
    }

    fun reset() {
        lastEventPubkey?.let { Arrays.fill(it, 0) }
        lastEventPubkey = null
        lastEventId = 0L
        _state.value = State.Idle
    }
}
