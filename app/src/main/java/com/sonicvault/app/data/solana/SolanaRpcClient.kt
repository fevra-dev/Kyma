package com.sonicvault.app.data.solana

import com.sonicvault.app.BuildConfig
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Solana JSON-RPC client backed by Helius for reliable devnet/mainnet access.
 *
 * - RPC queries (blockhash, nonce, accounts) go through Helius RPC endpoint.
 * - Transaction submission uses Helius Sender for dual-routing (validators + Jito).
 * - Priority fee estimation via Helius getPriorityFeeEstimate API.
 */
class SolanaRpcClient(
    private val rpcUrl: String = buildHeliusRpcUrl(),
    private val senderUrl: String = HELIUS_SENDER_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    /** Helius Sender is mainnet-only; skip it for devnet to avoid silent rejections. */
    private val isDevnet: Boolean get() = rpcUrl.contains("devnet", ignoreCase = true)

    private val blockhashRetryCount = 3
    private val blockhashRetryDelayMs = 1500L

    /**
     * Fetches the latest blockhash from the cluster.
     * Retries up to [blockhashRetryCount] times with exponential backoff.
     *
     * @return Pair of (blockhash, lastValidBlockHeight) or null on failure
     */
    suspend fun getLatestBlockhash(): BlockhashResult? = withContext(Dispatchers.IO) {
        repeat(blockhashRetryCount) { attempt ->
            try {
                val body = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", "getLatestBlockhash")
                    put("params", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("commitment", "confirmed")
                        })
                    })
                }.toString()

                val request = Request.Builder()
                    .url(rpcUrl)
                    .post(body.toRequestBody(JSON_MEDIA))
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    SonicVaultLogger.w("[SolanaRpc] getLatestBlockhash attempt ${attempt + 1}: ${response.code}")
                    if (attempt < blockhashRetryCount - 1) delay(blockhashRetryDelayMs)
                    return@repeat
                }

                val json = JSONObject(response.body?.string() ?: "{}")
                if (json.has("error")) {
                    SonicVaultLogger.w("[SolanaRpc] getLatestBlockhash error: ${json.optJSONObject("error")}")
                    if (attempt < blockhashRetryCount - 1) delay(blockhashRetryDelayMs)
                    return@repeat
                }

                val result = json.optJSONObject("result")?.optJSONObject("value")
                    ?: run {
                        if (attempt < blockhashRetryCount - 1) delay(blockhashRetryDelayMs)
                        return@repeat
                    }

                val blockhash = result.optString("blockhash")
                val lastValidBlockHeight = result.optLong("lastValidBlockHeight", 0L)

                if (blockhash.isBlank()) {
                    if (attempt < blockhashRetryCount - 1) delay(blockhashRetryDelayMs)
                    return@repeat
                }

                SonicVaultLogger.d("[SolanaRpc] blockhash=$blockhash lastValid=$lastValidBlockHeight")
                return@withContext BlockhashResult(blockhash, lastValidBlockHeight)
            } catch (e: Exception) {
                SonicVaultLogger.e("[SolanaRpc] getLatestBlockhash attempt ${attempt + 1} failed", e)
                if (attempt < blockhashRetryCount - 1) delay(blockhashRetryDelayMs)
            }
        }
        null
    }

    /**
     * Simulates a signed transaction before submission to catch failures early.
     * Prevents fee-burning and nonce-wasting from on-chain failures.
     *
     * @param signedTxBase64 base64-encoded serialized signed transaction
     * @return [SimulationResult] indicating success or failure with error details
     */
    suspend fun simulateTransaction(signedTxBase64: String): SimulationResult = withContext(Dispatchers.IO) {
        try {
            val params = org.json.JSONArray().put(signedTxBase64)
            params.put(JSONObject().apply {
                put("encoding", "base64")
                put("commitment", "confirmed")
                put("replaceRecentBlockhash", false)
            })
            val body = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 9)
                put("method", "simulateTransaction")
                put("params", params)
            }.toString()
            val request = Request.Builder()
                .url(rpcUrl)
                .post(body.toRequestBody(JSON_MEDIA))
                .addHeader("Content-Type", "application/json")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext SimulationResult.Failed("Simulation RPC error: ${response.code}")
            }
            val json = JSONObject(response.body?.string() ?: "{}")
            if (json.has("error")) {
                val msg = json.optJSONObject("error")?.optString("message", "unknown")
                return@withContext SimulationResult.Failed("RPC error: $msg")
            }
            val result = json.optJSONObject("result")?.optJSONObject("value")
            val err = result?.optJSONObject("err")
            if (err != null) {
                val logs = result.optJSONArray("logs")
                val lastLog = if (logs != null && logs.length() > 0) logs.optString(logs.length() - 1) else null
                SonicVaultLogger.w("[SolanaRpc] simulation failed: $err")
                return@withContext SimulationResult.Failed(lastLog ?: err.toString())
            }
            SonicVaultLogger.d("[SolanaRpc] simulation passed")
            SimulationResult.Success
        } catch (e: Exception) {
            SonicVaultLogger.w("[SolanaRpc] simulateTransaction exception", e)
            SimulationResult.Failed(e.message ?: "Simulation network error")
        }
    }

    /**
     * Submits a signed transaction via Helius Sender for higher landing rates.
     * Runs simulation first to catch failures before consuming fees/nonces.
     * Falls back to standard RPC sendTransaction if Sender fails.
     *
     * @param signedTransactionBase64 base64-encoded serialized signed transaction
     * @return transaction signature (base58) or null on failure
     */
    suspend fun sendTransaction(signedTransactionBase64: String): String? = withContext(Dispatchers.IO) {
        val sim = simulateTransaction(signedTransactionBase64)
        if (!sim.success) {
            SonicVaultLogger.w("[SolanaRpc] TX rejected by simulation: ${sim.error}")
            return@withContext null
        }

        // Helius Sender is mainnet-only; skip for devnet (wrong cluster = silent rejection)
        if (!isDevnet) {
            val senderResult = trySendViaSender(signedTransactionBase64)
            if (senderResult != null) return@withContext senderResult
        }

        // Standard RPC (devnet or Sender fallback)
        trySendViaRpc(signedTransactionBase64)
    }

    /**
     * Submits a signed transaction with retries and exponential backoff.
     * Retries up to [maxRetries] times with delays 1s, 2s, 4s between attempts.
     *
     * @param signedTransactionBase64 base64-encoded serialized signed transaction
     * @param maxRetries number of retry attempts (default 3)
     * @return transaction signature (base58) or null on failure
     */
    suspend fun sendTransactionWithRetry(
        signedTransactionBase64: String,
        maxRetries: Int = 3
    ): String? = withContext(Dispatchers.IO) {
        var lastDelayMs = 0L
        repeat(maxRetries) { attempt ->
            val result = sendTransaction(signedTransactionBase64)
            if (result != null) return@withContext result
            if (attempt < maxRetries - 1) {
                lastDelayMs = when (attempt) {
                    0 -> 1000L
                    1 -> 2000L
                    else -> 4000L
                }
                delay(lastDelayMs)
            }
        }
        null
    }

    /** Helius Sender: POST base64 TX to sender endpoint. */
    private fun trySendViaSender(signedTxBase64: String): String? {
        try {
            val params = org.json.JSONArray().put(signedTxBase64)
            val options = JSONObject().apply {
                put("encoding", "base64")
                put("skipPreflight", true)
                put("maxRetries", 0)
            }
            params.put(options)

            val body = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 2)
                put("method", "sendTransaction")
                put("params", params)
            }.toString()

            val request = Request.Builder()
                .url(senderUrl)
                .post(body.toRequestBody(JSON_MEDIA))
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                SonicVaultLogger.w("[SolanaRpc] Helius Sender failed: ${response.code}, falling back to RPC")
                return null
            }

            val json = JSONObject(response.body?.string() ?: "{}")
            if (json.has("error")) {
                SonicVaultLogger.w("[SolanaRpc] Helius Sender error: ${json.optJSONObject("error")?.optString("message")}")
                return null
            }

            val signature = json.optString("result", "")
            if (signature.isBlank()) return null

            SonicVaultLogger.i("[SolanaRpc] tx sent via Helius Sender: $signature")
            return signature
        } catch (e: Exception) {
            SonicVaultLogger.w("[SolanaRpc] Helius Sender exception, falling back", e)
            return null
        }
    }

    /** Standard RPC sendTransaction fallback. */
    private fun trySendViaRpc(signedTxBase64: String): String? {
        return try {
            val params = org.json.JSONArray().put(signedTxBase64)
            val options = JSONObject().apply {
                put("encoding", "base64")
                put("skipPreflight", true)
                put("maxRetries", 3)
            }
            params.put(options)

            val body = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 2)
                put("method", "sendTransaction")
                put("params", params)
            }.toString()

            val request = Request.Builder()
                .url(rpcUrl)
                .post(body.toRequestBody(JSON_MEDIA))
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                SonicVaultLogger.w("[SolanaRpc] sendTransaction failed: ${response.code}")
                return null
            }

            val json = JSONObject(response.body?.string() ?: "{}")
            if (json.has("error")) {
                val err = json.optJSONObject("error")
                val code = err?.optInt("code", -1)
                val msg = err?.optString("message", "unknown")
                SonicVaultLogger.w("[SolanaRpc] sendTransaction error code=$code message=$msg")
                return null
            }

            val signature = json.optString("result", "")
            if (signature.isBlank()) return null

            SonicVaultLogger.i("[SolanaRpc] tx sent via RPC: $signature")
            signature
        } catch (e: Exception) {
            SonicVaultLogger.e("[SolanaRpc] sendTransaction failed", e)
            null
        }
    }

    /**
     * Estimates optimal priority fee using Helius getPriorityFeeEstimate API.
     * Returns recommended fee in microlamports, or a safe default on failure.
     *
     * @param accountKeys list of account pubkeys involved in the transaction
     * @return priority fee in microlamports
     */
    suspend fun getPriorityFeeEstimate(accountKeys: List<String> = emptyList()): Long = withContext(Dispatchers.IO) {
        try {
            val params = org.json.JSONArray().apply {
                put(JSONObject().apply {
                    if (accountKeys.isNotEmpty()) {
                        put("accountKeys", org.json.JSONArray(accountKeys))
                    }
                    put("options", JSONObject().apply {
                        put("priorityLevel", "Medium")
                    })
                })
            }

            val body = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 6)
                put("method", "getPriorityFeeEstimate")
                put("params", params)
            }.toString()

            val request = Request.Builder()
                .url(rpcUrl)
                .post(body.toRequestBody(JSON_MEDIA))
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                SonicVaultLogger.w("[SolanaRpc] getPriorityFeeEstimate failed: ${response.code}")
                return@withContext DEFAULT_PRIORITY_FEE
            }

            val json = JSONObject(response.body?.string() ?: "{}")
            if (json.has("error")) {
                SonicVaultLogger.w("[SolanaRpc] getPriorityFeeEstimate error: ${json.optJSONObject("error")}")
                return@withContext DEFAULT_PRIORITY_FEE
            }

            val fee = json.optJSONObject("result")?.optLong("priorityFeeEstimate", DEFAULT_PRIORITY_FEE)
                ?: DEFAULT_PRIORITY_FEE

            SonicVaultLogger.d("[SolanaRpc] priority fee estimate: $fee microlamports")
            fee
        } catch (e: Exception) {
            SonicVaultLogger.w("[SolanaRpc] getPriorityFeeEstimate failed, using default", e)
            DEFAULT_PRIORITY_FEE
        }
    }

    /**
     * Fetches the current nonce value from a nonce account.
     *
     * @param nonceAccountPubkey base58 pubkey of the nonce account
     * @return current nonce value (base58) or null on failure
     */
    suspend fun getNonce(nonceAccountPubkey: String): String? = withContext(Dispatchers.IO) {
        try {
            val config = JSONObject().apply {
                put("encoding", "base64")
                put("commitment", "confirmed")
            }
            val params = org.json.JSONArray().put(nonceAccountPubkey).put(config)
            val body = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 3)
                put("method", "getAccountInfo")
                put("params", params)
            }.toString()
            val request = Request.Builder()
                .url(rpcUrl)
                .post(body.toRequestBody(JSON_MEDIA))
                .addHeader("Content-Type", "application/json")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val json = JSONObject(response.body?.string() ?: "{}")
            if (json.has("error")) return@withContext null
            val value = json.optJSONObject("result")?.optJSONObject("value") ?: return@withContext null
            val data = value.optJSONArray("data") ?: return@withContext null
            val decoded = android.util.Base64.decode(data.optString(0), android.util.Base64.DEFAULT)
            if (decoded.size < 72) return@withContext null
            val nonceBytes = decoded.copyOfRange(40, 72)
            io.github.novacrypto.base58.Base58.base58Encode(nonceBytes)
        } catch (e: Exception) {
            SonicVaultLogger.e("[SolanaRpc] getNonce failed", e)
            null
        }
    }

    /**
     * Minimum lamports for rent exemption of [dataLength] bytes.
     */
    suspend fun getMinimumBalanceForRentExemption(dataLength: Long): Long = withContext(Dispatchers.IO) {
        try {
            val params = org.json.JSONArray().put(dataLength.toInt())
            val body = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 4)
                put("method", "getMinimumBalanceForRentExemption")
                put("params", params)
            }.toString()
            val request = Request.Builder()
                .url(rpcUrl)
                .post(body.toRequestBody(JSON_MEDIA))
                .addHeader("Content-Type", "application/json")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext 0L
            val json = JSONObject(response.body?.string() ?: "{}")
            if (json.has("error")) return@withContext 0L
            json.optLong("result", 0L)
        } catch (e: Exception) {
            SonicVaultLogger.e("[SolanaRpc] getMinimumBalanceForRentExemption failed", e)
            0L
        }
    }

    /**
     * Returns account pubkeys for a program matching the given filters.
     * Uses commitment "confirmed" so newly created accounts are visible.
     */
    suspend fun getProgramAccounts(programId: String, filters: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val config = JSONObject().apply {
                put("encoding", "base64")
                put("commitment", "confirmed")
                put("filters", org.json.JSONArray(filters))
            }
            val params = org.json.JSONArray().put(programId).put(config)
            val body = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 5)
                put("method", "getProgramAccounts")
                put("params", params)
            }.toString()
            val request = Request.Builder()
                .url(rpcUrl)
                .post(body.toRequestBody(JSON_MEDIA))
                .addHeader("Content-Type", "application/json")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val json = JSONObject(response.body?.string() ?: "{}")
            if (json.has("error")) return@withContext emptyList()
            val result = json.optJSONArray("result") ?: return@withContext emptyList()
            (0 until result.length()).mapNotNull { result.optJSONObject(it)?.optString("pubkey") }
        } catch (e: Exception) {
            SonicVaultLogger.e("[SolanaRpc] getProgramAccounts failed", e)
            emptyList()
        }
    }

    /**
     * Polls signature status until confirmed or max attempts reached.
     *
     * @param signature base58 tx signature
     * @param maxAttempts number of poll attempts
     * @param intervalMs delay between attempts
     * @return true if confirmed, false otherwise
     */
    suspend fun waitForConfirmation(
        signature: String,
        maxAttempts: Int = 5,
        intervalMs: Long = 2000L
    ): Boolean = withContext(Dispatchers.IO) {
        repeat(maxAttempts) { attempt ->
            try {
                val sigsArray = org.json.JSONArray().put(signature)
                val config = JSONObject().apply {
                    put("commitment", "confirmed")
                }
                val params = org.json.JSONArray().put(sigsArray).put(config)
                val body = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("id", 7)
                    put("method", "getSignatureStatuses")
                    put("params", params)
                }.toString()
                val request = Request.Builder()
                    .url(rpcUrl)
                    .post(body.toRequestBody(JSON_MEDIA))
                    .addHeader("Content-Type", "application/json")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@repeat
                val json = JSONObject(response.body?.string() ?: "{}")
                if (json.has("error")) return@repeat
                val result = json.optJSONObject("result") ?: return@repeat
                val statuses = result.optJSONArray("value") ?: return@repeat
                if (statuses.length() > 0) {
                    val status = statuses.optJSONObject(0) ?: return@repeat
                    val err = status.optJSONObject("err")
                    if (err != null) {
                        SonicVaultLogger.w("[SolanaRpc] tx $signature confirmed but FAILED: $err")
                        return@withContext false
                    }
                    val confirmationStatus = status.optString("confirmationStatus", "")
                    if (confirmationStatus.isNotEmpty() &&
                        ("confirmed" in confirmationStatus.lowercase() || "finalized" in confirmationStatus.lowercase())
                    ) {
                        SonicVaultLogger.i("[SolanaRpc] tx $signature confirmed")
                        return@withContext true
                    }
                }
            } catch (e: Exception) {
                SonicVaultLogger.w("[SolanaRpc] getSignatureStatuses attempt ${attempt + 1} failed", e)
            }
            if (attempt < maxAttempts - 1) delay(intervalMs)
        }
        false
    }

    /**
     * Checks RPC connectivity via getHealth.
     * Used for pre-flight connectivity check before signing ceremonies.
     *
     * @return true if RPC responds with "ok", false otherwise
     */
    suspend fun getHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 8)
                put("method", "getHealth")
            }.toString()
            val request = Request.Builder()
                .url(rpcUrl)
                .post(body.toRequestBody(JSON_MEDIA))
                .addHeader("Content-Type", "application/json")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false
            val json = JSONObject(response.body?.string() ?: "{}")
            if (json.has("error")) return@withContext false
            val result = json.optString("result", "")
            result == "ok"
        } catch (e: Exception) {
            SonicVaultLogger.w("[SolanaRpc] getHealth failed", e)
            false
        }
    }

    data class BlockhashResult(
        val blockhash: String,
        val lastValidBlockHeight: Long
    )

    /** Result of [simulateTransaction]. Check [success] before submitting. */
    data class SimulationResult private constructor(val success: Boolean, val error: String?) {
        companion object {
            val Success = SimulationResult(true, null)
            fun Failed(error: String) = SimulationResult(false, error)
        }
    }

    companion object {
        /** Safe fallback priority fee (1000 microlamports) when Helius estimate unavailable. */
        private const val DEFAULT_PRIORITY_FEE = 1000L

        /** Helius Sender: dual-routes through validators + Jito, free on all plans. */
        const val HELIUS_SENDER_URL = "https://sender.helius-rpc.com/fast"

        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** Explorer URL for a transaction signature. Uses Orb by Helius. */
        fun explorerUrl(signature: String, cluster: String = BuildConfig.SOLANA_CLUSTER): String =
            "https://orb.helius.dev/tx/$signature/"

        /**
         * Builds Helius RPC URL from BuildConfig API key and cluster.
         * Cluster is read from BuildConfig.SOLANA_CLUSTER (set in gradle.properties).
         */
        fun buildHeliusRpcUrl(): String {
            val key = BuildConfig.HELIUS_API_KEY
            val cluster = BuildConfig.SOLANA_CLUSTER
            return if (key.isNotBlank()) {
                if (cluster == "mainnet-beta") "https://mainnet.helius-rpc.com/?api-key=$key"
                else "https://devnet.helius-rpc.com/?api-key=$key"
            } else {
                if (cluster == "mainnet-beta") "https://api.mainnet-beta.solana.com"
                else "https://api.devnet.solana.com"
            }
        }
    }
}
