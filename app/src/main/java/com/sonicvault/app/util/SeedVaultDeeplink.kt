package com.sonicvault.app.util

import android.content.Context
import android.content.Intent

/**
 * Deeplink to Seed Vault "Import Existing" flow.
 *
 * User manually types mnemonic words (e.g. from MnemonicTeleprompter) into Seed Vault.
 * No importSeed API for third-party — we launch the intent; user enters in secure UI.
 *
 * @return Intent for ACTION_IMPORT_SEED, or null if Seed Vault unavailable
 */
fun createSeedVaultImportDeeplink(context: Context): Intent? =
    createImportSeedIntent(context)
