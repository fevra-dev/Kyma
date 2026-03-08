package com.sonicvault.app.ui.screen.cnftdrop

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.sonicvault.app.data.solana.BubblegumConstants
import com.sonicvault.app.data.solana.BubblegumTransactionBuilder
import com.sonicvault.app.data.solana.SolanaRpcClient
import com.sonicvault.app.data.solana.SolanaTransactionBuilder
import com.sonicvault.app.data.sound.AcousticChunkReceiver
import com.sonicvault.app.data.sound.AcousticTransmitter
import com.sonicvault.app.logging.SonicVaultLogger
import io.github.novacrypto.base58.Base58
import kotlinx.coroutines.delay
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
import java.nio.ByteBuffer
import java.util.Base64

/**
 * ViewModel for cNFT acoustic airdrop: listen for event_id, claim mintToCollectionV1.
 *
 * States: idle | listening | received(eventId) | minting | success | error
 * Tree and collection from BubblegumConstants (replace with real devnet addresses).
 */
class CnftDropViewModel(
    private val rpcClient: SolanaRpcClient = SolanaRpcClient()
) : ViewModel() {

    sealed class State {
        data object Idle : State()
        data object Listening : State()
        data class Received(val eventId: Long) : State()
        data object Minting : State()
        data class Success(val signature: String, val explorerUrl: String) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var receiveJob: Job? = null
    private var timeoutJob: Job? = null

    /** Parsed event_id from last received payload. */
    private var lastEventId: Long = 0L

    /** Start listening for 8-byte event_id acoustic payload. 30s timeout. */
    fun startListening(context: Context) {
        receiveJob?.cancel()
        timeoutJob?.cancel()
        _state.value = State.Listening

        receiveJob = AcousticChunkReceiver.receiveFlow(
            context,
            sessionIdFilter = null,
            rawPayloadSizes = setOf(8)
        )
            .onEach { payload ->
                timeoutJob?.cancel()
                if (payload.size >= 8) {
                    val eventId = ByteBuffer.wrap(payload, 0, 8).order(java.nio.ByteOrder.BIG_ENDIAN).long
                    lastEventId = eventId
                    SonicVaultLogger.i("[CnftDropVM] received event_id=$eventId")
                    _state.value = State.Received(eventId)
                }
            }
            .catch { e ->
                SonicVaultLogger.e("[CnftDropVM] receive error", e)
                _state.value = State.Error("Could not receive drop. Move devices closer.")
            }
            .launchIn(viewModelScope)

        timeoutJob = viewModelScope.launch {
            delay(30_000L)
            if (_state.value is State.Listening) {
                receiveJob?.cancel()
                receiveJob = null
                _state.value = State.Error("No drop received. Ensure the transmitter is broadcasting.")
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

    /** Broadcast 8-byte event_id for other Seekers to claim. */
    fun broadcastDrop(context: Context, eventId: Long) {
        viewModelScope.launch {
            try {
                val payload = ByteBuffer.allocate(8).order(java.nio.ByteOrder.BIG_ENDIAN).putLong(eventId).array()
                withContext(Dispatchers.IO) {
                    AcousticTransmitter.transmitSingle(payload)
                }
                SonicVaultLogger.i("[CnftDropVM] broadcast event_id=$eventId")
            } catch (e: Exception) {
                SonicVaultLogger.e("[CnftDropVM] broadcast failed", e)
                _state.value = State.Error("Broadcast failed")
            }
        }
    }

    /** User approved: build mintToCollectionV1, MWA sign, RPC submit. */
    fun claimDrop(sender: ActivityResultSender) {
        val eventId = lastEventId
        if (eventId == 0L) return
        _state.value = State.Minting

        viewModelScope.launch {
            try {
                val blockhashResult = withContext(Dispatchers.IO) {
                    rpcClient.getLatestBlockhash()
                } ?: run {
                    _state.value = State.Error("Could not fetch blockhash. Check network.")
                    return@launch
                }

                val walletAdapter = MobileWalletAdapter(
                    connectionIdentity = ConnectionIdentity(
                        identityUri = Uri.parse("https://sonicvault.app"),
                        iconUri = Uri.parse("/icon.png"),
                        identityName = "SonicVault"
                    )
                )

                val result = walletAdapter.transact(sender, null) { authResult ->
                    val account = authResult.accounts.firstOrNull() ?: return@transact null
                    val leafOwner = Base58.base58Encode(account.publicKey)

                    val tx = BubblegumTransactionBuilder.buildMintToCollectionV1(
                        leafOwner = leafOwner,
                        merkleTree = BubblegumConstants.KYMA_CNFT_TREE,
                        collectionMint = BubblegumConstants.KYMA_COLLECTION_MINT,
                        blockhash = blockhashResult.blockhash
                    ) ?: return@transact null

                    val unsignedBytes = SolanaTransactionBuilder.serializeForSigning(tx)
                        ?: return@transact null
                    signTransactions(arrayOf(unsignedBytes)).signedPayloads.firstOrNull()
                }

                when (result) {
                    is TransactionResult.Success -> {
                        val signed = result.payload?.let { Base64.getEncoder().encodeToString(it) }
                            ?: run {
                                _state.value = State.Error("No signed payload")
                                return@launch
                            }
                        val sig = withContext(Dispatchers.IO) {
                            rpcClient.sendTransactionWithRetry(signed)
                        }
                        if (sig != null) {
                            _state.value = State.Success(sig, SolanaRpcClient.explorerUrl(sig))
                        } else {
                            _state.value = State.Error("Transaction could not be sent. Check network and tree setup.")
                        }
                    }
                    is TransactionResult.NoWalletFound -> _state.value = State.Error("No wallet found. Connect Seed Vault.")
                    is TransactionResult.Failure -> _state.value = State.Error(result.e.message ?: "Sign failed")
                }
            } catch (e: Exception) {
                SonicVaultLogger.e("[CnftDropVM] claimDrop failed", e)
                _state.value = State.Error("Claim failed. Ensure tree and collection are set up on devnet.")
            }
        }
    }

    fun reset() {
        _state.value = State.Idle
    }
}
