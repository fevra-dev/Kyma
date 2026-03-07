package com.sonicvault.app.data.crypto

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.isSeedVaultAvailable

/**
 * Seed Vault crypto provider for Solana Seeker devices.
 *
 * The Solana Mobile Seed Vault SDK (seedvault-wallet-sdk) exposes signTransaction, signMessage,
 * and authorizeSeed — but not generic encrypt/decrypt of arbitrary data. SonicVault needs
 * AES-256-GCM encryption for backup payloads.
 *
 * **Backup encrypt/decrypt:** Uses Android Keystore (TEE-backed on Seeker) via [AndroidSeedVaultCrypto].
 * **Save recovered seed:** Optional "Save to Seed Vault" in RecoveryScreen uses [ACTION_IMPORT_SEED];
 * user enters seed in Seed Vault's secure UI.
 *
 * **Integration:** When [isSolanaSeeker] is true, this provider is used. We check
 * [isSeedVaultAvailable] (Seeker device or SeedVaultSimulator).
 * **Fallback:** Android Keystore for backup encrypt/decrypt.
 *
 * Reference: Roadmap Part 6.1 Seed Vault; solana-mobile/seed-vault-sdk integration_guide.md.
 */
class SeekerSeedVaultCrypto(
    private val context: Context
) : SeedVaultCrypto {

    private val delegate: AndroidSeedVaultCrypto = AndroidSeedVaultCrypto(context)

    init {
        val seedVaultAvailable = isSeedVaultAvailable(context)
        SonicVaultLogger.i("[SeedVault] provider=SeekerSeedVault seedVaultAvailable=$seedVaultAvailable fallback=Keystore")
    }

    override fun hasKey(): Boolean = delegate.hasKey()

    override fun ensureKeyCreated() = delegate.ensureKeyCreated()

    override suspend fun encrypt(plaintext: ByteArray, activity: FragmentActivity): BiometricAuthResult<EncryptedPayload> =
        delegate.encrypt(plaintext, activity)

    override suspend fun decrypt(payload: EncryptedPayload, activity: FragmentActivity): BiometricAuthResult<ByteArray> =
        delegate.decrypt(payload, activity)
}
