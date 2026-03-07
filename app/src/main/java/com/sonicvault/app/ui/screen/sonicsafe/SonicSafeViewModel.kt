package com.sonicvault.app.ui.screen.sonicsafe

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import android.content.Context
import com.sonicvault.app.data.solana.SolanaRpcClient
import com.sonicvault.app.data.solana.SolanaTransactionBuilder
import com.sonicvault.app.data.preferences.UserPreferences
import com.sonicvault.app.data.sound.AcousticChunkReceiver
import com.sonicvault.app.data.sound.AcousticTransmitter
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Base64

/**
 * ViewModel for SonicSafe cold signing flow.
 *
 * States: idle | received(txBytes) | signing | success | error
 * Cold device: listens for chunked TX, signs via MWA, transmits signed TX (chunked).
 */
class SonicSafeViewModel(
    private val userPreferences: UserPreferences,
    private val rpcClient: SolanaRpcClient = SolanaRpcClient()
) : ViewModel() {

    sealed class State {
        data object Idle : State()
        data class Received(val txBytes: ByteArray) : State()
        data object Signing : State()
        data class Success(val signature: String) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var receiveJob: Job? = null

    /** Start listening for chunked TX from hot device. */
    fun startListening(context: Context) {
        receiveJob?.cancel()
        _state.value = State.Idle

        receiveJob = AcousticChunkReceiver.receiveFlow(context)
            .onEach { payload ->
                SonicVaultLogger.i("[SonicSafeVM] received TX ${payload.size} bytes")
                _state.value = State.Received(payload)
            }
            .catch { e ->
                SonicVaultLogger.e("[SonicSafeVM] receive error", e)
                _state.value = State.Error("Could not receive transaction. Move devices closer.")
            }
            .launchIn(viewModelScope)
    }

    /** Stop listening. */
    fun stopListening() {
        receiveJob?.cancel()
        receiveJob = null
    }

    /** Pre-load TX bytes without starting listen (e.g. from unified receiver). */
    fun setReceivedTx(txBytes: ByteArray) {
        receiveJob?.cancel()
        receiveJob = null
        _state.value = State.Received(txBytes)
    }

    /**
     * User approved: MWA signTransactions → transmit signed TX chunked.
     *
     * @param sender pre-registered ActivityResultSender (created in MainActivity.onCreate)
     */
    fun signAndTransmit(sender: ActivityResultSender) {
        val txBytes = (_state.value as? State.Received)?.txBytes ?: return
        _state.value = State.Signing

        viewModelScope.launch {
            try {
                val signedBytes = signViaMwa(sender, txBytes)
                if (signedBytes != null) {
                    withContext(Dispatchers.IO) {
                        AcousticTransmitter.transmitChunked(signedBytes, sessionId = 2, applyFingerprintRandomization = userPreferences.useAntiFingerprint)
                    }
                    _state.value = State.Success(Base64.getEncoder().encodeToString(signedBytes).take(24) + "…")
                } else {
                    _state.value = State.Error("Sign failed")
                }
            } catch (e: Exception) {
                SonicVaultLogger.e("[SonicSafeVM] signAndTransmit failed", e)
                _state.value = State.Error("Signing failed. Check wallet connection and try again.")
            }
        }
    }

    private suspend fun signViaMwa(sender: ActivityResultSender, unsignedBytes: ByteArray): ByteArray? {
        val walletAdapter = MobileWalletAdapter(
            connectionIdentity = ConnectionIdentity(
                identityUri = Uri.parse("https://sonicvault.app"),
                iconUri = Uri.parse("/icon.png"),
                identityName = "SonicVault"
            )
        )

        val result = walletAdapter.transact(sender, null) { authResult ->
            signTransactions(arrayOf(unsignedBytes)).signedPayloads.firstOrNull()
        }

        return when (result) {
            is TransactionResult.Success -> result.payload
            is TransactionResult.NoWalletFound -> null
            is TransactionResult.Failure -> null
        }
    }

    fun reset() {
        _state.value = State.Idle
    }

    suspend fun broadcastSignedTransaction(signedBase64: String): String? {
        return rpcClient.sendTransaction(signedBase64)
    }
}
