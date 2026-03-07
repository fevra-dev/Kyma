package com.sonicvault.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.sonicvault.app.logging.SonicVaultLogger
import com.solanamobile.seedvault.Wallet
import com.solanamobile.seedvault.WalletContractV1

/**
 * Device detection for Solana Seeker and Seed Vault availability.
 * Used to select SeedVaultCrypto provider: SeekerSeedVault (on Seeker) vs AndroidKeystore (fallback).
 *
 * Reference: Roadmap Part 6.1 Seed Vault (Solana Seeker);
 * Solana Mobile Security Research: Seed Vault = TEE; keys non-exportable.
 */

/** Package name of Solana Seeker app (when installed, device is considered Seeker). */
private const val PACKAGE_SOLANA_SEEKER = "com.solana.seeker"

/** Seed Vault implementation package (from seed-vault-sdk WalletContractV1). */
private const val PACKAGE_SEED_VAULT = "com.solanamobile.seedvaultimpl"

/** Intent action to check Seed Vault availability (resolveComponentForIntent). */
private const val ACTION_AUTHORIZE_SEED = "com.solanamobile.seedvault.wallet.v1.ACTION_AUTHORIZE_SEED_ACCESS"

/**
 * Returns an Intent to launch the Seed Vault import flow. User enters seed in Seed Vault's secure UI.
 * Requires API 30+ (Wallet.importSeed). Use with {@link Activity#startActivityForResult}.
 *
 * @return Intent for {@link WalletContractV1#ACTION_IMPORT_SEED}, or null if SDK unavailable
 */
fun createImportSeedIntent(context: Context): Intent? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            Wallet.importSeed(context, WalletContractV1.PURPOSE_SIGN_SOLANA_TRANSACTION)
        } catch (e: Exception) {
            SonicVaultLogger.w("[SeedVault] createImportSeedIntent failed: ${e.message}")
            null
        }
    } else {
        null
    }
}

/**
 * Returns true if Solana Seeker app is installed on this device.
 * When true, SonicVault may use SeekerSeedVaultCrypto (Seed Vault or Keystore fallback).
 */
fun isSolanaSeeker(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo(PACKAGE_SOLANA_SEEKER, 0)
        SonicVaultLogger.d("[SeedVault] isSolanaSeeker=true (package found)")
        true
    } catch (e: PackageManager.NameNotFoundException) {
        SonicVaultLogger.d("[SeedVault] isSolanaSeeker=false (package not found)")
        false
    }
}

/**
 * Returns true if Seed Vault implementation is available (can resolve authorize-seed intent).
 * Seed Vault may be present on Seeker devices or when SeedVaultSimulator is installed (dev/test).
 */
fun isSeedVaultAvailable(context: Context): Boolean {
    return try {
        val intent = Intent(ACTION_AUTHORIZE_SEED).setPackage(PACKAGE_SEED_VAULT)
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val available = resolveInfo != null
        SonicVaultLogger.d("[SeedVault] isSeedVaultAvailable=$available")
        available
    } catch (e: Exception) {
        SonicVaultLogger.w("[SeedVault] isSeedVaultAvailable check failed: ${e.message}")
        false
    }
}
