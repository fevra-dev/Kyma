package com.sonicvault.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Loads TestSonicVaultApplication so instrumented tests get FakeSeedVaultCrypto (no biometric).
 */
class TestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        return super.newApplication(cl, TestSonicVaultApplication::class.java.name, context)
    }
}
