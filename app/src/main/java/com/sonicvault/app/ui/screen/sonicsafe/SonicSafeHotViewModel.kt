package com.sonicvault.app.ui.screen.sonicsafe

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.sonicvault.app.data.nonce.NonceAccountEntity
import com.sonicvault.app.data.nonce.NoncePoolManager
import com.sonicvault.app.data.solana.DurableNonceTxBuilder
import com.sonicvault.app.data.solana.SolanaRpcClient
import com.sonicvault.app.data.solana.SolanaTransactionBuilder
import com.sonicvault.app.data.preferences.UserPreferences
import com.sonicvault.app.data.sound.AcousticChunker
import com.sonicvault.app.data.sound.AcousticChunkReceiver
import com.sonicvault.app.data.sound.AcousticTransmitter
import com.sonicvault.app.logging.SonicVaultLogger
import io.github.novacrypto.base58.Base58
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
 * ViewModel for SonicSafe hot broadcaster flow.
 *
 * Hot device: build TX → transmit chunked → listen for signed TX → broadcast.
 * Uses NoncePoolManager when pool has nonces (durable nonce); falls back to blockhash.
 * States: idle | building | transmitting | listening | success | error
 */
class SonicSafeHotViewModel(
    private val noncePoolManager: NoncePoolManager,
    private val userPreferences: UserPreferences,
    private val rpcClient: SolanaRpcClient = SolanaRpcClient()
) : ViewModel() {

    sealed class State {
        data object Idle : State()
        data object Building : State()
        data object Transmitting : State()
        data object Listening : State()
        data class Success(val signature: String) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var listenJob: Job? = null

    /** Nonce checked out for current ceremony; must call markConsumed on any outcome. */
    private var checkedOutNonce: NonceAccountEntity? = null

    /**
     * Build TX, transmit chunked, then listen for signed TX and broadcast.
     *
     * Uses durable nonce when pool has nonces (MWA path only); manual from uses blockhash.
     *
     * @param sender pre-registered ActivityResultSender (created in MainActivity.onCreate)
     * @param appContext application context for acoustic listener
     * @param recipient base58 pubkey
     * @param lamports amount in lamports
     * @param fromAddress optional manual sender (base58); when provided, skips MWA and uses blockhash
     */
    fun sendForSigning(
        sender: ActivityResultSender,
        appContext: Context,
        recipient: String,
        lamports: Long,
        fromAddress: String? = null
    ) {
        _state.value = State.Building
        checkedOutNonce = null

        viewModelScope.launch {
            try {
                val buildResult = if (fromAddress?.isNotBlank() == true) {
                    buildTxWithManualFrom(fromAddress, recipient, lamports)
                } else {
                    buildTxViaMwa(sender, recipient, lamports)
                }
                val unsignedBytes = buildResult?.bytes
                if (unsignedBytes == null) {
                    markConsumedIfNeeded(null)
                    val msg = if (fromAddress?.isNotBlank() == true) {
                        "Could not build transaction. Check network and address format."
                    } else {
                        "Connect a Solana wallet to send, or add a sender address for cold-sign mode."
                    }
                    _state.value = State.Error(msg)
                    return@launch
                }

                if (unsignedBytes.size > AcousticChunker.MAX_ACOUSTIC_TX_BYTES) {
                    markConsumedIfNeeded(null)
                    _state.value = State.Error("Transaction too large for acoustic relay (~600 bytes max)")
                    return@launch
                }

                _state.value = State.Transmitting
                withContext(Dispatchers.IO) {
                    AcousticTransmitter.transmitChunked(unsignedBytes, sessionId = 1, applyFingerprintRandomization = userPreferences.useAntiFingerprint)
                }

                _state.value = State.Listening
                listenForSignedAndBroadcast(appContext)
            } catch (e: Exception) {
                SonicVaultLogger.e("[SonicSafeHotVM] sendForSigning failed", e)
                markConsumedIfNeeded(null)
                _state.value = State.Error(
                    "Transaction failed. Check network and try again."
                )
            }
        }
    }

    private suspend fun markConsumedIfNeeded(txSig: String?) {
        val nonce = checkedOutNonce
        if (nonce != null) {
            noncePoolManager.markConsumed(nonce, txSig)
            checkedOutNonce = null
        }
    }

    /** Build TX using manual from address (no MWA). Always uses blockhash fallback. */
    private suspend fun buildTxWithManualFrom(
        fromPubkey: String,
        recipient: String,
        lamports: Long
    ): BuildResult? {
        val blockhashResult = withContext(Dispatchers.IO) {
            rpcClient.getLatestBlockhash()
        } ?: return null
        val tx = DurableNonceTxBuilder.buildTransfer(
            fromPubkey = fromPubkey,
            toPubkey = recipient,
            lamports = lamports,
            blockhash = blockhashResult.blockhash
        ) ?: return null
        val bytes = SolanaTransactionBuilder.serializeForSigning(tx) ?: return null
        return BuildResult(bytes, null)
    }

    private suspend fun buildTxViaMwa(
        sender: ActivityResultSender,
        recipient: String,
        lamports: Long
    ): BuildResult? {
        val walletAdapter = MobileWalletAdapter(
            connectionIdentity = ConnectionIdentity(
                identityUri = Uri.parse("https://sonicvault.app"),
                iconUri = Uri.parse("/icon.png"),
                identityName = "SonicVault"
            )
        )

        var resultBytes: ByteArray? = null
        var resultNonce: NonceAccountEntity? = null

        val result = walletAdapter.transact(sender, null) { authResult ->
            val account = authResult.accounts.firstOrNull() ?: return@transact null
            val fromPubkey = Base58.base58Encode(account.publicKey)

            val nonce = withContext(Dispatchers.IO) { noncePoolManager.checkoutNonce() }
            if (nonce != null) {
                val currentNonce = withContext(Dispatchers.IO) { rpcClient.getNonce(nonce.publicKey) }
                    ?: run {
                        noncePoolManager.markConsumed(nonce)
                        return@transact null
                    }
                val tx = SolanaTransactionBuilder.buildDurableNonceTx(
                    nonceAccountPubkey = nonce.publicKey,
                    nonceAuthorityPubkey = fromPubkey,
                    feePayerPubkey = fromPubkey,
                    toPubkey = recipient,
                    lamports = lamports,
                    nonceValue = currentNonce
                ) ?: run {
                    noncePoolManager.markConsumed(nonce)
                    return@transact null
                }
                // Hot must sign NonceAdvance (nonce authority). MWA signTransactions adds hot's sig.
                val unsignedBytes = SolanaTransactionBuilder.serializeForSigning(tx)
                    ?: run {
                        noncePoolManager.markConsumed(nonce)
                        return@transact null
                    }
                val signResult = signTransactions(arrayOf(unsignedBytes))
                resultBytes = signResult.signedPayloads.firstOrNull()
                resultNonce = nonce
            } else {
                val blockhashResult = withContext(Dispatchers.IO) {
                    rpcClient.getLatestBlockhash()
                } ?: return@transact null
                val tx = DurableNonceTxBuilder.buildTransfer(
                    fromPubkey = fromPubkey,
                    toPubkey = recipient,
                    lamports = lamports,
                    blockhash = blockhashResult.blockhash
                ) ?: return@transact null
                resultBytes = SolanaTransactionBuilder.serializeForSigning(tx)
            }
            null
        }

        return when (result) {
            is TransactionResult.Success -> {
                checkedOutNonce = resultNonce
                resultBytes?.let { BuildResult(it, resultNonce) }
            }
            is TransactionResult.NoWalletFound -> null
            is TransactionResult.Failure -> null
        }
    }

    private fun listenForSignedAndBroadcast(context: Context) {
        listenJob?.cancel()
        val signedFlow = AcousticChunkReceiver.receiveFlow(context, sessionIdFilter = 2)
            .onEach { signedBytes ->
                SonicVaultLogger.i("[SonicSafeHotVM] received signed TX ${signedBytes.size} bytes")
                val base64 = Base64.getEncoder().encodeToString(signedBytes)
                val sig = rpcClient.sendTransaction(base64)
                markConsumedIfNeeded(sig)
                if (sig != null) {
                    _state.value = State.Success(sig)
                } else {
                    _state.value = State.Error("Transaction could not be sent. Check network and try again.")
                }
            }
            .catch { e ->
                SonicVaultLogger.e("[SonicSafeHotVM] listen error", e)
                markConsumedIfNeeded(null)
                _state.value = State.Error(e.message ?: "Listen failed")
            }

        listenJob = signedFlow.launchIn(viewModelScope)
    }

    private data class BuildResult(val bytes: ByteArray, val nonce: NonceAccountEntity?)

    fun reset() {
        listenJob?.cancel()
        listenJob = null
        _state.value = State.Idle
    }
}
