package com.sonicvault.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.sonicvault.app.ui.nav.SonicVaultNav
import com.sonicvault.app.ui.theme.SonicVaultTheme

/**
 * Single Activity; Compose + Navigation for Home, Backup, Recovery.
 * AppCompatActivity (FragmentActivity) required for BiometricPrompt.
 *
 * Share Target: when launched via ACTION_SEND with audio MIME type, navigates to Recovery with the shared Uri.
 *
 * Screen capture allowed for demos/presentations (FLAG_SECURE removed).
 */
class MainActivity : AppCompatActivity() {

    /**
     * MWA ActivityResultSender — must be created before STARTED (i.e., in onCreate).
     * Shared by all screens that need MWA wallet interactions.
     */
    lateinit var activityResultSender: ActivityResultSender
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityResultSender = ActivityResultSender(this)
        enableEdgeToEdge()
        window.decorView.setFitsSystemWindows(false)
        setContent {
            SonicVaultTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SonicVaultNav(
                        /**
                         * SECURITY PATCH [SVA-012]: Validate shared URI MIME type before accepting.
                         * Prevents malicious apps from crafting intents with non-audio URIs
                         * that could cause crashes or unexpected behavior in the recovery flow.
                         */
                        sharedAudioUri = intent?.takeIf { it.action == Intent.ACTION_SEND }
                            ?.takeIf { it.type?.startsWith("audio/") == true }
                            ?.let { androidx.core.content.IntentCompat.getParcelableExtra(it, Intent.EXTRA_STREAM, Uri::class.java) }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
