package com.sonicvault.app.data.solana

import com.sonicvault.app.logging.SonicVaultLogger
import com.solana.core.HotAccount
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Solana Pay transfer request URI per spec: solana:<recipient>?amount=&spl-token=&reference=&label=&message=&memo=
 *
 * Replay protection: memo may contain ts:{unixSeconds}. URIs with ts > 60 seconds old are rejected.
 *
 * @param recipient base58-encoded Solana public key (required)
 * @param amount SOL or token amount (optional; wallet prompts if omitted)
 * @param label merchant/source name (URL-encoded)
 * @param message item/order description
 * @param memo SPL Memo instruction; use memo=ts:{unixSeconds} for replay protection
 * @param splToken SPL Token mint for token transfers
 * @param reference client IDs for payment tracking
 */
data class SolanaPayUri(
    val recipient: String,
    val amount: Double?,
    val label: String?,
    val message: String?,
    val memo: String?,
    val splToken: String? = null,
    val reference: List<String>? = null
) {
    /**
     * Encodes this URI to UTF-8 bytes for acoustic transmission via ggwave.
     * Auto-generates a reference key for replay tracking when none provided.
     */
    fun encode(): ByteArray {
        val refs = reference?.filter { it.isNotBlank() }.orEmpty()
            .ifEmpty { listOf(HotAccount().publicKey.toBase58()) }
        val uri = buildString {
            append("solana:$recipient")
            val params = mutableListOf<String>()
            amount?.let { params.add("amount=$it") }
            label?.let { params.add("label=${URLEncoder.encode(it, Charsets.UTF_8)}") }
            message?.let { params.add("message=${URLEncoder.encode(it, Charsets.UTF_8)}") }
            memo?.let { params.add("memo=${URLEncoder.encode(it, Charsets.UTF_8)}") }
            splToken?.let { params.add("spl-token=$it") }
            refs.forEach { params.add("reference=$it") }
            if (params.isNotEmpty()) {
                append("?")
                append(params.joinToString("&"))
            }
        }
        return uri.toByteArray(Charsets.UTF_8)
    }

    /**
     * Parses a Solana Pay URI from raw bytes.
     *
     * @param bytes UTF-8 encoded URI string
     * @return Parsed SolanaPayUri or null if invalid
     */
    companion object {
        private const val REPLAY_STALE_SECONDS = 60L

        fun decode(bytes: ByteArray): SolanaPayUri? {
            return try {
                val str = String(bytes, Charsets.UTF_8).trim()
                if (!str.startsWith("solana:", ignoreCase = true)) return null

                val pathAndQuery = str.removePrefix("solana:")
                val (path, queryPart) = when {
                    pathAndQuery.contains("?") -> {
                        val idx = pathAndQuery.indexOf("?")
                        pathAndQuery.substring(0, idx) to pathAndQuery.substring(idx + 1)
                    }
                    else -> pathAndQuery to null
                }

                val recipient = path.trim()
                if (recipient.isBlank()) return null

                if (!isValidBase58(recipient)) {
                    SonicVaultLogger.w("[SolanaPayUri] invalid recipient (not base58)")
                    return null
                }

                var amount: Double? = null
                var label: String? = null
                var message: String? = null
                var memo: String? = null
                var splToken: String? = null
                val references = mutableListOf<String>()

                queryPart?.split("&")?.forEach { param ->
                    val eq = param.indexOf("=")
                    if (eq < 0) return@forEach
                    val key = param.substring(0, eq)
                    val value = param.substring(eq + 1)
                    when (key.lowercase()) {
                        "amount" -> amount = value.toDoubleOrNull()?.takeIf { it >= 0 }
                        "label" -> label = URLDecoder.decode(value, Charsets.UTF_8)
                        "message" -> message = URLDecoder.decode(value, Charsets.UTF_8)
                        "memo" -> memo = URLDecoder.decode(value, Charsets.UTF_8)
                        "spl-token" -> splToken = value
                        "reference" -> references.add(value)
                    }
                }

                val uri = SolanaPayUri(
                    recipient = recipient,
                    amount = amount,
                    label = label,
                    message = message,
                    memo = memo,
                    splToken = splToken,
                    reference = references.takeIf { it.isNotEmpty() }
                )

                if (!validateReplayProtection(uri)) {
                    SonicVaultLogger.w("[SolanaPayUri] rejected: stale timestamp in memo")
                    return null
                }

                uri
            } catch (e: Exception) {
                SonicVaultLogger.e("[SolanaPayUri] decode failed", e)
                null
            }
        }

        /**
         * Replay protection: if memo contains ts:{unixSeconds}, reject if > REPLAY_STALE_SECONDS old.
         * Uses regex to find ts: anywhere in memo (e.g. "order-42 ts:1740000000").
         */
        private fun validateReplayProtection(uri: SolanaPayUri): Boolean {
            val memo = uri.memo ?: return true
            val match = Regex("""ts:(\d{10})""").find(memo) ?: return true
            val ts = match.groupValues[1].toLongOrNull() ?: return true
            val now = System.currentTimeMillis() / 1000
            if (now - ts > REPLAY_STALE_SECONDS) return false
            return true
        }

        private fun isValidBase58(s: String): Boolean {
            val base58Chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
            return s.isNotEmpty() && s.all { it in base58Chars } && s.length in 32..44
        }
    }
}
