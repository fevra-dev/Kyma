package com.sonicvault.app.data.crypto

import android.net.Uri
import androidx.activity.ComponentActivity
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.sonicvault.app.MainActivity
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Derives AES-256 key from Seed Vault via signMessage KDF.
 *
 * Flow: MWA transact → signMessagesDetached(derivationMessage) → HKDF(signature) → 32B key.
 * Same device + same key produces same signature (Ed25519 deterministic) → same AES key.
 * Enables SE-bound backup: decrypt only on same Seeker that created the backup.
 */
object SeedVaultKeyOracle {

    private const val DERIVATION_MESSAGE = "SonicVault:AES-Key-Derivation:v1"

    /**
     * Derives a 32-byte AES key via Seed Vault signMessage + HKDF.
     *
     * @param sender pre-registered ActivityResultSender (created in MainActivity.onCreate)
     * @return 32-byte AES key or null if user cancelled / no wallet / sign failed
     */
    suspend fun deriveAesKey(sender: ActivityResultSender): ByteArray? = withContext(Dispatchers.Main) {
        val walletAdapter = MobileWalletAdapter(
            connectionIdentity = ConnectionIdentity(
                identityUri = Uri.parse("https://sonicvault.app"),
                iconUri = Uri.parse("/icon.png"),
                identityName = "SonicVault"
            )
        )

        val result = walletAdapter.transact(sender, null) { authResult ->
            val account = authResult.accounts.firstOrNull() ?: return@transact null
            val message = DERIVATION_MESSAGE.toByteArray(Charsets.UTF_8)
            val addresses = arrayOf(account.publicKey)
            val signResult = signMessagesDetached(arrayOf(message), addresses)
            signResult?.messages?.firstOrNull()?.signatures?.firstOrNull()
        }

        when (result) {
            is TransactionResult.Success -> {
                val signature = result.payload ?: return@withContext null
                val key = HkdfKeyDeriver.derive(
                    ikm = signature,
                    salt = null,
                    info = DERIVATION_MESSAGE.toByteArray(Charsets.UTF_8)
                )
                SonicVaultLogger.i("[SeedVaultKeyOracle] derived key, ${key?.size ?: 0} bytes")
                key
            }
            is TransactionResult.NoWalletFound -> {
                SonicVaultLogger.w("[SeedVaultKeyOracle] no wallet found")
                null
            }
            is TransactionResult.Failure -> {
                SonicVaultLogger.w("[SeedVaultKeyOracle] sign failed", result.e)
                null
            }
        }
    }

    /**
     * Convenience overload for callers that have a ComponentActivity (e.g. BackupRepository).
     * Extracts the pre-registered sender from MainActivity.
     */
    suspend fun deriveAesKey(activity: ComponentActivity): ByteArray? {
        val sender = (activity as? MainActivity)?.activityResultSender
            ?: throw IllegalStateException("deriveAesKey requires MainActivity with pre-registered ActivityResultSender")
        return deriveAesKey(sender)
    }
}
