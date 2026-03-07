package com.sonicvault.app.ui.screen.noncepool

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.sonicvault.app.data.nonce.NoncePoolManager
import com.sonicvault.app.data.solana.SolanaRpcClient
import com.sonicvault.app.data.solana.SolanaTransactionBuilder
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Base64

/**
 * ViewModel for Nonce Pool Setup screen.
 *
 * States: Idle | Discovering | Creating | Success(message) | Error(message)
 */
class NoncePoolSetupViewModel(
    private val noncePoolManager: NoncePoolManager,
    private val rpcClient: SolanaRpcClient = SolanaRpcClient()
) : ViewModel() {

    sealed class State {
        data object Idle : State()
        data object Discovering : State()
        data object Creating : State()
        data class Success(val message: String) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val costEstimate: String get() = noncePoolManager.costEstimate(1)

    /**
     * Discovers nonce accounts for [authorityPubkey] and imports them into the pool.
     */
    fun discoverAndImport(authorityPubkey: String) {
        val trimmed = authorityPubkey.trim()
        if (trimmed.isBlank()) {
            _state.value = State.Error("Enter authority pubkey")
            return
        }
        _state.value = State.Discovering

        viewModelScope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    noncePoolManager.importDiscoveredNonces(trimmed)
                }
                if (imported.isEmpty()) {
                    _state.value = State.Error("No nonce accounts found for this authority")
                } else {
                    _state.value = State.Success("Imported ${imported.size} nonce account(s)")
                    SonicVaultLogger.i("[NoncePoolSetup] imported ${imported.size} nonces")
                }
            } catch (e: Exception) {
                SonicVaultLogger.e("[NoncePoolSetup] discoverAndImport failed", e)
                _state.value = State.Error("Could not find nonce accounts. Check authority key and network.")
            }
        }
    }

    /**
     * Creates a new nonce account via MWA.
     *
     * @param sender pre-registered ActivityResultSender (created in MainActivity.onCreate)
     */
    fun createNonceAccount(sender: ActivityResultSender) {
        _state.value = State.Creating

        viewModelScope.launch {
            try {
                val blockhashResult = withContext(Dispatchers.IO) {
                    rpcClient.getLatestBlockhash()
                } ?: run {
                    _state.value = State.Error("Failed to get blockhash. Check your connection and try again.")
                    return@launch
                }

                val rentLamports = withContext(Dispatchers.IO) {
                    rpcClient.getMinimumBalanceForRentExemption(80)
                }
                if (rentLamports <= 0) {
                    _state.value = State.Error("Failed to get rent exemption")
                    return@launch
                }

                val walletAdapter = MobileWalletAdapter(
                    connectionIdentity = ConnectionIdentity(
                        identityUri = Uri.parse("https://sonicvault.app"),
                        iconUri = Uri.parse("/icon.png"),
                        identityName = "SonicVault"
                    )
                )

                var createdNoncePubkey: String? = null
                var authorityPubkeyForDiscover: String? = null
                val result = walletAdapter.transact(sender, null) { authResult ->
                    val account = authResult.accounts.firstOrNull() ?: return@transact null
                    val payerPubkey = io.github.novacrypto.base58.Base58.base58Encode(account.publicKey)
                    authorityPubkeyForDiscover = payerPubkey

                    val buildResult = SolanaTransactionBuilder.buildCreateNonceAccountTx(
                        payerPubkey = payerPubkey,
                        authorityPubkey = payerPubkey,
                        rentLamports = rentLamports,
                        blockhash = blockhashResult.blockhash
                    ) ?: return@transact null

                    createdNoncePubkey = buildResult.nonceAccount.publicKey.toBase58()
                    val tx = buildResult.tx
                    val nonceAccount = buildResult.nonceAccount

                    val message = SolanaTransactionBuilder.serializeMessage(tx) ?: return@transact null
                    val nonceSig = nonceAccount.sign(message)
                    tx.addSignature(nonceAccount.publicKey, nonceSig)

                    val partiallySignedBytes = SolanaTransactionBuilder.serializeForSigning(tx)
                        ?: return@transact null

                    val signResult = signTransactions(arrayOf(partiallySignedBytes))
                    signResult?.signedPayloads?.firstOrNull()
                }

                when (result) {
                    is TransactionResult.Success -> {
                        val signedPayload = result.payload ?: run {
                            _state.value = State.Error("No signed payload")
                            return@launch
                        }
                        val signedBase64 = Base64.getEncoder().encodeToString(signedPayload)
                        val txSig = withContext(Dispatchers.IO) {
                            rpcClient.sendTransaction(signedBase64)
                        }
                        if (txSig == null) {
                            _state.value = State.Error("Transaction failed to submit. Check balance and try again.")
                            return@launch
                        }
                        val noncePubkey = createdNoncePubkey
                        if (noncePubkey != null) {
                            // Wait for tx confirmation before insert (new accounts may not be indexed immediately)
                            kotlinx.coroutines.delay(3000)
                            var inserted = withContext(Dispatchers.IO) {
                                noncePoolManager.insertNonceAfterCreate(noncePubkey)
                            }
                            if (!inserted && authorityPubkeyForDiscover != null) {
                                // Auto-discover: tx confirmed but insert failed (e.g. getNonce timing)
                                val imported = withContext(Dispatchers.IO) {
                                    noncePoolManager.importDiscoveredNonces(authorityPubkeyForDiscover!!)
                                }
                                inserted = noncePubkey in imported
                            }
                            if (inserted) {
                                _state.value = State.Success("Nonce account created and added to pool")
                            } else {
                                _state.value = State.Success("Created. Use Discover & Import with your wallet address (authority) to add to pool.")
                            }
                        } else {
                            _state.value = State.Success("Nonce account created")
                        }
                    }
                    is TransactionResult.NoWalletFound -> {
                        _state.value = State.Error("No wallet found. Install a Solana wallet app (e.g. Phantom).")
                    }
                    is TransactionResult.Failure -> {
                        _state.value = State.Error(result.e.message ?: "Create failed")
                    }
                }
            } catch (e: Exception) {
                SonicVaultLogger.e("[NoncePoolSetup] createNonceAccount failed", e)
                _state.value = State.Error(e.message ?: "Create failed")
            }
        }
    }

    fun reset() {
        _state.value = State.Idle
    }
}
