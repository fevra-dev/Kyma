package com.sonicvault.app.ui.screen.vote

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.sonicvault.app.data.solana.GovernanceConstants
import com.sonicvault.app.data.solana.SolanaRpcClient
import com.sonicvault.app.data.solana.SolanaTransactionBuilder
import com.sonicvault.app.data.sound.AcousticChunkReceiver
import com.sonicvault.app.data.sound.AcousticTransmitter
import com.sonicvault.app.logging.SonicVaultLogger
import io.github.novacrypto.base58.Base58
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * ViewModel for Guardian Voting Demo: acoustic round-trip.
 *
 * Kiosk: broadcast 33B (proposal + direction), listen for 64B signature returns.
 * Voter: listen for 33B, cast vote (SPL Memo TX), MWA sign, submit, return 64B sig acoustically.
 */
class GuardianVoteViewModel(
    private val rpcClient: SolanaRpcClient = SolanaRpcClient()
) : ViewModel() {

    sealed class State {
        data object Idle : State()
        data object Listening : State()
        data class Received(val proposalPubkey: String, val direction: GovernanceConstants.VoteDirection) : State()
        data object Signing : State()
        data class VoteSubmitted(val signature: String) : State()
        data object TransmittingReturn : State()
        data object Success : State()
        data class Error(val message: String) : State()
    }

    /** Kiosk mode: collected vote signatures. */
    data class KioskState(
        val votesCollected: Int = 0,
        val lastSignature: String? = null
    )

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _kioskState = MutableStateFlow(KioskState())
    val kioskState: StateFlow<KioskState> = _kioskState.asStateFlow()

    private var receiveJob: Job? = null
    private var timeoutJob: Job? = null

    /** Last received payload: 32B proposal + 1B direction. */
    private var lastProposalBytes: ByteArray? = null
    private var lastDirection: GovernanceConstants.VoteDirection = GovernanceConstants.VoteDirection.YES

    private val SIG_MAGIC = byteArrayOf(0x53, 0x49)

    /** Kiosk: broadcast 33B (32B proposal + 1B direction). */
    fun broadcastProposal(context: Context, proposalPubkey: String, direction: GovernanceConstants.VoteDirection) {
        val proposalBytes = try {
            Base58.base58Decode(proposalPubkey)
        } catch (_: Exception) {
            null
        }
        if (proposalBytes == null || proposalBytes.size != 32) {
            _state.value = State.Error("Invalid proposal pubkey (must be 32-byte base58)")
            return
        }
        viewModelScope.launch {
            try {
                val payload = ByteBuffer.allocate(33).order(ByteOrder.BIG_ENDIAN)
                    .put(proposalBytes)
                    .put(direction.value)
                    .array()
                withContext(Dispatchers.IO) {
                    AcousticTransmitter.transmitSingle(payload)
                }
                SonicVaultLogger.i("[GuardianVoteVM] broadcast proposal: ${proposalPubkey.take(12)}…")
                startKioskListen(context)
            } catch (e: Exception) {
                SonicVaultLogger.e("[GuardianVoteVM] broadcast failed", e)
                _state.value = State.Error("Broadcast failed")
            }
        }
    }

    /** Kiosk: listen for 64B signature returns (session 2 envelope). */
    private fun startKioskListen(context: Context) {
        receiveJob?.cancel()
        _kioskState.value = KioskState()
        receiveJob = AcousticChunkReceiver.receiveFlow(
            context,
            sessionIdFilter = 2
        )
            .onEach { payload ->
                if (payload.size == 64) {
                    val sig = Base64.getEncoder().encodeToString(payload).take(24) + "…"
                    _kioskState.value = KioskState(
                        votesCollected = _kioskState.value.votesCollected + 1,
                        lastSignature = sig
                    )
                    SonicVaultLogger.i("[GuardianVoteVM] received vote signature #${_kioskState.value.votesCollected}")
                }
            }
            .catch { e ->
                SonicVaultLogger.e("[GuardianVoteVM] kiosk receive error", e)
            }
            .launchIn(viewModelScope)
    }

    /** Voter: start listening for 33B proposal payload. 30s timeout. */
    fun startListening(context: Context) {
        receiveJob?.cancel()
        timeoutJob?.cancel()
        _state.value = State.Listening

        receiveJob = AcousticChunkReceiver.receiveFlow(
            context,
            sessionIdFilter = null,
            rawPayloadSizes = setOf(33)
        )
            .onEach { payload ->
                timeoutJob?.cancel()
                if (payload.size >= 33) {
                    val proposalBytes = payload.copyOfRange(0, 32)
                    val dirByte = payload[32].toInt() and 0xFF
                    val direction = when (dirByte) {
                        0x01 -> GovernanceConstants.VoteDirection.YES
                        0x00 -> GovernanceConstants.VoteDirection.NO
                        0x02 -> GovernanceConstants.VoteDirection.ABSTAIN
                        else -> GovernanceConstants.VoteDirection.YES
                    }
                    lastProposalBytes = proposalBytes
                    lastDirection = direction
                    val proposalPubkey = Base58.base58Encode(proposalBytes)
                    SonicVaultLogger.i("[GuardianVoteVM] received proposal: ${proposalPubkey.take(12)}… $direction")
                    _state.value = State.Received(proposalPubkey, direction)
                }
            }
            .catch { e ->
                SonicVaultLogger.e("[GuardianVoteVM] receive error", e)
                _state.value = State.Error("Could not receive. Move devices closer.")
            }
            .launchIn(viewModelScope)

        timeoutJob = viewModelScope.launch {
            delay(30_000L)
            if (_state.value is State.Listening) {
                receiveJob?.cancel()
                receiveJob = null
                _state.value = State.Error("No proposal received. Ensure the kiosk is broadcasting.")
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

    /** Voter: cast vote via SPL Memo TX, MWA sign, submit, return 64B sig acoustically. */
    fun castVote(sender: ActivityResultSender) {
        val proposalBytes = lastProposalBytes
        if (proposalBytes == null) return
        val proposalPubkey = Base58.base58Encode(proposalBytes)
        _state.value = State.Signing

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
                    val payerPubkey = Base58.base58Encode(account.publicKey)
                    val tx = SolanaTransactionBuilder.buildVoteMemoTx(
                        payerPubkey = payerPubkey,
                        proposalPubkey = proposalPubkey,
                        direction = lastDirection,
                        blockhash = blockhashResult.blockhash
                    ) ?: return@transact null
                    val unsignedBytes = SolanaTransactionBuilder.serializeForSigning(tx)
                        ?: return@transact null
                    signTransactions(arrayOf(unsignedBytes)).signedPayloads.firstOrNull()
                }

                when (result) {
                    is TransactionResult.Success -> {
                        val signed = result.payload ?: run {
                            _state.value = State.Error("No signed payload")
                            return@launch
                        }
                        val signature = signed.copyOfRange(1, 65)
                        val sig = withContext(Dispatchers.IO) {
                            rpcClient.sendTransactionWithRetry(Base64.getEncoder().encodeToString(signed))
                        }
                        if (sig != null) {
                            _state.value = State.VoteSubmitted(sig)
                            _state.value = State.TransmittingReturn
                            val envelope = ByteArray(SIG_MAGIC.size + signature.size)
                            SIG_MAGIC.copyInto(envelope, 0)
                            signature.copyInto(envelope, SIG_MAGIC.size)
                            withContext(Dispatchers.IO) {
                                AcousticTransmitter.transmitSingle(envelope)
                            }
                            _state.value = State.Success
                        } else {
                            _state.value = State.Error("Transaction could not be sent. Check network.")
                        }
                    }
                    is TransactionResult.NoWalletFound -> _state.value = State.Error("No wallet found. Connect Seed Vault.")
                    is TransactionResult.Failure -> _state.value = State.Error(result.e.message ?: "Sign failed")
                }
            } catch (e: Exception) {
                SonicVaultLogger.e("[GuardianVoteVM] castVote failed", e)
                _state.value = State.Error("Vote failed. Check network and try again.")
            }
        }
    }

    fun reset() {
        lastProposalBytes = null
        _state.value = State.Idle
    }
}
