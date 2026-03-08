package com.sonicvault.app.ui.screen.faq

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonicvault.app.ui.theme.Spacing

/**
 * In-app FAQ: comprehensive answers to common questions about security, features, and usage.
 * Expandable accordion style. Rams: useful, understandable, thorough.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(onBack: () -> Unit) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FAQ", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.md.dp)
                .padding(top = Spacing.sm.dp, bottom = Spacing.lg.dp)
        ) {
            /* ── Security ── */
            FaqSectionHeader("Security")
            FaqItem(
                question = "Does my data leave my device?",
                answer = "No. All encryption, decryption, audio encoding, and seed processing happen entirely on your device. " +
                        "Your seed phrase is never uploaded, transmitted over the internet, or stored on any server. " +
                        "Kyma has no network permissions for data upload."
            )
            FaqItem(
                question = "What encryption does Kyma use?",
                answer = "AES-256-GCM with a 256-bit key derived from your biometric or password via PBKDF2 (600,000 iterations). " +
                        "This is the same encryption standard used by governments and financial institutions. " +
                        "The encrypted payload is then embedded into cover audio using LSB steganography."
            )
            Spacer(modifier = Modifier.height(Spacing.md.dp))

            /* ── Audio & Transmission ── */
            FaqSectionHeader("Audio & Transmission")
            FaqItem(
                question = "Why ultrasonic sound?",
                answer = "Ultrasonic sound (15–19.5 kHz) is inaudible to most adults. " +
                        "It lets you transfer your seed between devices without anyone nearby hearing it, " +
                        "reducing shoulder-surfing and audio surveillance risks. " +
                        "The data is encoded using ggwave, a proven data-over-sound library."
            )
            FaqItem(
                question = "What are the two protocols?",
                answer = "Ultrasonic (default): 15–19.5 kHz, inaudible to most adults. Best for private transfers.\n\n" +
                        "Audible: 2–6 kHz, hearable tones. Use if you want confirmation that transfer is happening, " +
                        "or for accessibility. Select on the transmit or receive screen."
            )
            FaqItem(
                question = "Can someone intercept my transmission?",
                answer = "The sound signal can be recorded by any nearby microphone within range (~1–3 metres). " +
                        "However, the payload is AES-256-GCM encrypted before encoding to sound, so even if captured, " +
                        "the data cannot be decrypted without your biometric or password. " +
                        "For maximum security, transmit in a private space."
            )
            FaqItem(
                question = "What is the secure handshake (ECDH)?",
                answer = "The secure handshake uses X25519 ECDH to exchange keys over sound before sending data. " +
                        "Each session gets a fresh key — if someone records the audio later, they cannot decrypt it. " +
                        "No shared passphrase needed. Enable \"Secure handshake\" in Sound Transfer for app-to-app transfers."
            )
            FaqItem(
                question = "What is the noise-resistant fountain?",
                answer = "Instead of sending the payload once or twice, the transmitter sends a stream of small \"droplets\". " +
                        "The receiver collects enough to reconstruct the full message (LT codes). " +
                        "If some droplets are lost to noise, others still arrive — dramatically improves success in cafes or outdoors. " +
                        "Enable \"Noise-resistant (fountain)\" in Sound Transfer when the environment is noisy."
            )
            FaqItem(
                question = "When do I need a session code?",
                answer = "Session code is a shared passphrase (e.g. abc123) for encrypting broadcasts. " +
                        "Use it when sending to the web receiver (browser) — the web cannot do ECDH handshake, so it needs a shared secret. " +
                        "Both sender and receiver enter the same code. App-to-app can use secure handshake instead (no code needed)."
            )

            Spacer(modifier = Modifier.height(Spacing.md.dp))

            /* ── Backup & Recovery ── */
            FaqSectionHeader("Backup & Recovery")
            FaqItem(
                question = "Can I use a music file as cover audio?",
                answer = "Yes. You can hide your seed inside any audio file — music, voice memos, podcasts, etc. " +
                        "Kyma embeds the encrypted data in the audio using LSB steganography. " +
                        "Use a file of at least 5 seconds. Longer files provide more steganographic capacity. " +
                        "The \"Upload\" button opens your device's file browser where you can navigate to Music, Downloads, or any folder."
            )
            FaqItem(
                question = "How long should my cover audio be?",
                answer = "Minimum 5 seconds. For best results, use 10–30 seconds of cover audio. " +
                        "Longer audio provides more capacity and makes the hidden data harder to detect. " +
                        "When recording in-app, you can choose 5s, 10s, 15s, or 30s."
            )
            FaqItem(
                question = "What if I lose a Shamir share?",
                answer = "With a 2-of-3 split, you need any 2 shares to recover your seed. " +
                        "If you lose 1 share, the other 2 still work. With 3-of-5, losing up to 2 shares is safe. " +
                        "Store shares in different secure locations — home safe, bank safety deposit box, trusted family member. " +
                        "A single share reveals nothing about your seed."
            )
            FaqItem(
                question = "What is Shamir's Secret Sharing?",
                answer = "Shamir's Secret Sharing (SLIP-0039) splits your seed into multiple \"shares\". " +
                        "You define how many shares to create (e.g. 3) and how many are needed to reconstruct (e.g. 2). " +
                        "No single share can reveal your seed — you need the minimum threshold together. " +
                        "This protects against loss (redundancy) and theft (no single point of compromise)."
            )
            FaqItem(
                question = "Can I recover my seed from the stego audio file?",
                answer = "Yes. Go to Decode → Recover Backup. Select the audio file containing your hidden seed. " +
                        "You'll need to authenticate with your biometric or enter your encryption password. " +
                        "Kyma extracts the hidden encrypted payload and decrypts it to reveal your seed phrase."
            )

            Spacer(modifier = Modifier.height(Spacing.md.dp))

            /* ── General ── */
            FaqSectionHeader("General")
            FaqItem(
                question = "What wallets does Kyma support?",
                answer = "Kyma supports BIP-39 seed phrases (12 or 24 words) with the Solana derivation path " +
                        "m/44'/501'/0'/0'. This is compatible with Phantom, Solflare, Backpack, and most Solana wallets. " +
                        "The seed phrase format is standard — you can import/export with any BIP-39 compatible wallet."
            )
        }
    }
}

/** Section header for FAQ categories. */
@Composable
private fun FaqSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = Spacing.sm.dp)
    )
}

/**
 * Expandable FAQ item with accordion animation.
 * Rams: understandable (question visible at a glance), as little as possible (answer hidden until needed).
 */
@Composable
private fun FaqItem(
    question: String,
    answer: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = Spacing.sm.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Text(
                text = answer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm.dp)
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )
    }
}
