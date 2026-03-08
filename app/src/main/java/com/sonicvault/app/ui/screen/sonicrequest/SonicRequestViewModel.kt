package com.sonicvault.app.ui.screen.sonicrequest

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import android.content.Context
import com.sonicvault.app.data.solana.AcousticPaymentReceiver
import com.sonicvault.app.data.solana.SkrConstants
import com.sonicvault.app.data.solana.SolanaPayUri
import com.sonicvault.app.data.solana.SolanaRpcClient
import com.sonicvault.app.data.solana.SolanaTransactionBuilder
import com.sonicvault.app.data.sound.AcousticAck
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
import kotlin.math.pow
import java.util.Base64

/**
 * ViewModel for SonicRequest: acoustic Solana Pay receive → sign → broadcast.
 *
 * States: idle | received(uri) | signing | confirming | success | error
 * Integrates MWA signTransactions (deprecated but supported) + our RPC submit.
 */
class SonicRequestViewModel(
    private val rpcClient: SolanaRpcClient = SolanaRpcClient()
) : ViewModel() {

    sealed class State {
        data object Idle : State()
        data class Received(val uri: SolanaPayUri) : State()
        data object Signing : State()
        data object Confirming : State()
        data class Success(val signature: String, val explorerUrl: String) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var receiveJob: Job? = null

    /** Start listening for acoustic Solana Pay URIs. Stops any previous listener. */
    fun startListening(context: Context) {
        receiveJob?.cancel()
        _state.value = State.Idle

        receiveJob = AcousticPaymentReceiver.receiveFlow(context, maxWindows = 0)
            .onEach { uri ->
                SonicVaultLogger.i("[SonicRequestVM] received URI: ${uri.recipient.take(8)}...")
                _state.value = State.Received(uri)
            }
            .catch { e ->
                SonicVaultLogger.e("[SonicRequestVM] receive error", e)
                _state.value = State.Error("Could not receive payment request. Move devices closer.")
            }
            .launchIn(viewModelScope)
    }

    /** Stop listening. */
    fun stopListening() {
        receiveJob?.cancel()
        receiveJob = null
    }

    /** Pre-load URI without starting listen (e.g. from unified receiver). */
    fun setReceivedUri(uri: SolanaPayUri) {
        receiveJob?.cancel()
        receiveJob = null
        _state.value = State.Received(uri)
    }

    /** User declined the payment request. */
    fun decline() {
        _state.value = State.Idle
    }

    /**
     * User approved: MWA transact → signTransactions → RPC submit → AcousticAck.
     *
     * @param sender pre-registered ActivityResultSender (created in MainActivity.onCreate)
     */
    fun approve(sender: com.solana.mobilewalletadapter.clientlib.ActivityResultSender) {
        val uri = (_state.value as? State.Received)?.uri ?: return
        _state.value = State.Signing

        viewModelScope.launch {
            try {
                val result = signAndSubmit(sender, uri)
                when (result) {
                    is SignResult.Success -> {
                        _state.value = State.Confirming
                        withContext(Dispatchers.IO) {
                            AcousticAck.play()
                        }
                        _state.value = State.Success(
                            result.signature,
                            com.sonicvault.app.data.solana.SolanaRpcClient.explorerUrl(result.signature)
                        )
                    }
                    is SignResult.Error -> {
                        _state.value = State.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                SonicVaultLogger.e("[SonicRequestVM] approve failed", e)
                _state.value = State.Error("Payment could not be completed. Check wallet and try again.")
            }
        }
    }

    private sealed class SignResult {
        data class Success(val signature: String) : SignResult()
        data class Error(val message: String) : SignResult()
    }

    private suspend fun signAndSubmit(sender: com.solana.mobilewalletadapter.clientlib.ActivityResultSender, uri: SolanaPayUri): SignResult {
        val walletAdapter = MobileWalletAdapter(
            connectionIdentity = ConnectionIdentity(
                identityUri = Uri.parse("https://sonicvault.app"),
                iconUri = Uri.parse("/icon.png"),
                identityName = "SonicVault"
            )
        )

        val result = walletAdapter.transact(sender, null) { authResult ->
            val account = authResult.accounts.firstOrNull() ?: return@transact null
            val fromPubkey = Base58.base58Encode(account.publicKey)

            val blockhashResult = withContext(Dispatchers.IO) {
                rpcClient.getLatestBlockhash()
            } ?: return@transact null

            val refs = uri.reference?.filter { it.isNotBlank() }.orEmpty()
            val tx = when {
                uri.splToken == SkrConstants.SKR_MINT -> {
                    val amount = uri.amount ?: 0.0
                    val amountBaseUnits = (amount * 10.0.pow(SkrConstants.SKR_DECIMALS)).toLong()
                    val skrTx = SolanaTransactionBuilder.buildSkrTransfer(
                        fromPubkey = fromPubkey,
                        toPubkey = uri.recipient,
                        amountBaseUnits = amountBaseUnits,
                        blockhash = blockhashResult.blockhash,
                        references = refs
                    ) ?: return@transact null
                    val createAtaIx = SolanaTransactionBuilder.buildCreateAtaIfNeeded(
                        payer = fromPubkey,
                        owner = uri.recipient,
                        mint = SkrConstants.SKR_MINT
                    )
                    if (createAtaIx != null) {
                        skrTx.instructions.add(0, createAtaIx)
                    }
                    skrTx
                }
                refs.isNotEmpty() -> SolanaTransactionBuilder.buildSolTransferWithReferences(
                    fromPubkey = fromPubkey,
                    toPubkey = uri.recipient,
                    lamports = ((uri.amount ?: 0.0) * SolanaTransactionBuilder.LAMPORTS_PER_SOL).toLong(),
                    blockhash = blockhashResult.blockhash,
                    references = refs
                )
                else -> SolanaTransactionBuilder.buildSolTransfer(
                    fromPubkey = fromPubkey,
                    toPubkey = uri.recipient,
                    lamports = ((uri.amount ?: 0.0) * SolanaTransactionBuilder.LAMPORTS_PER_SOL).toLong(),
                    blockhash = blockhashResult.blockhash
                )
            } ?: return@transact null

            val unsignedBytes = SolanaTransactionBuilder.serializeForSigning(tx) ?: return@transact null
            val signResult = signTransactions(arrayOf(unsignedBytes))
            signResult.signedPayloads.firstOrNull()
        }

        return when (result) {
            is TransactionResult.Success -> {
                val signed = result.payload?.let { Base64.getEncoder().encodeToString(it) } ?: return SignResult.Error("No signed payload")
                val sig = withContext(Dispatchers.IO) {
                    rpcClient.sendTransactionWithRetry(signed)
                }
                if (sig != null) {
                    withContext(Dispatchers.IO) {
                        rpcClient.waitForConfirmation(sig, maxAttempts = 5, intervalMs = 2000L)
                    }
                    SignResult.Success(sig)
                } else {
                    SignResult.Error("RPC send failed")
                }
            }
            is TransactionResult.NoWalletFound -> SignResult.Error("No wallet found")
            is TransactionResult.Failure -> SignResult.Error(result.e.message ?: "Sign failed")
        }
    }

    /** Reset to idle (e.g. after showing success or error). */
    fun reset() {
        _state.value = State.Idle
    }
}
