package com.sonicvault.app.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonicvault.app.BuildConfig
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing
import com.sonicvault.app.util.FeatureFlags

/**
 * Settings: essential actions only. Rams: useful, understandable, as little as possible.
 * Ma: breathing room between sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onVoiceUnlock: () -> Unit = {},
    onFaq: () -> Unit = {},
    onRecoveryGuide: () -> Unit = {},
    onDeadDrop: () -> Unit = {},
    onNoncePoolSetup: () -> Unit = {},
    onMatryoshka: () -> Unit = {},
    onSplitSeed: () -> Unit = {},
    onRecombineSeed: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SETTINGS", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                    ) {
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md.dp)
                .padding(top = Spacing.sm.dp, bottom = Spacing.lg.dp)
        ) {
            /* ── AUDIO ── */
            SectionHeader(title = "AUDIO")
            if (FeatureFlags.VOICE_BIOMETRIC_ENABLED) {
                OutlinedButton(
                    onClick = onVoiceUnlock,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = androidx.compose.ui.graphics.RectangleShape
                ) {
                    Text("VOICE UNLOCK", style = LabelUppercaseStyle)
                }
                Text(
                    text = "Enroll voice for biometric auth. On-device only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs.dp)
                )
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
            }
            OutlinedButton(
                onClick = onDeadDrop,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = androidx.compose.ui.graphics.RectangleShape
            ) {
                Text("SOUND TRANSFER", style = LabelUppercaseStyle)
            }
            Text(
                text = "Send SOL, broadcast data, or sign transactions via sound.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            OutlinedButton(
                onClick = onNoncePoolSetup,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = androidx.compose.ui.graphics.RectangleShape
            ) {
                Text("NONCE POOL", style = LabelUppercaseStyle)
            }
            Text(
                text = "Discover or create durable nonce accounts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs.dp)
            )
            SectionDivider()

            /* ── ADVANCED ── */
            SectionHeader(title = "ADVANCED")
            OutlinedButton(
                onClick = onMatryoshka,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = androidx.compose.ui.graphics.RectangleShape
            ) {
                Text("MATRYOSHKA STEGO", style = LabelUppercaseStyle)
            }
            Text(
                text = "3-layer: audio → spectrogram → image LSB → audio",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            OutlinedButton(
                onClick = onSplitSeed,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = androidx.compose.ui.graphics.RectangleShape
            ) {
                Text("SPLIT BACKUP", style = LabelUppercaseStyle)
            }
            Text(
                text = "Shamir: split seed into shares.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            OutlinedButton(
                onClick = onRecombineSeed,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = androidx.compose.ui.graphics.RectangleShape
            ) {
                Text("RECOVER SHARES", style = LabelUppercaseStyle)
            }
            Text(
                text = "Recombine shares to restore your seed phrase.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs.dp)
            )
            SectionDivider()

            /* ── HELP ── */
            SectionHeader(title = "HELP")
            OutlinedButton(
                onClick = onFaq,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = androidx.compose.ui.graphics.RectangleShape
            ) {
                Text("FAQ", style = LabelUppercaseStyle)
            }
            Spacer(modifier = Modifier.height(Spacing.sm.dp))
            OutlinedButton(
                onClick = onRecoveryGuide,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = androidx.compose.ui.graphics.RectangleShape
            ) {
                Text("RECOVERY GUIDE", style = LabelUppercaseStyle)
            }

            SectionDivider()

            /* ── ABOUT ── */
            SectionHeader(title = "ABOUT")
            Text(
                text = "All data stays on your device. Nothing is uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs.dp)
            )
            Text(
                text = "Kyma — Acoustic Cryptography",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs.dp)
            )
            Text(
                text = "Solana derivation: m/44'/501'/0'/0'",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs.dp)
            )
            Text(
                text = "AES-256-GCM · BIP-39 · SLIP-0039 · ggwave",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs.dp)
            )
        }
    }
}

/** Category header label. Rams: clear hierarchy, matches home section labels. */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = LabelUppercaseStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Spacing.xs.dp)
    )
}

/** Minimal divider between sections. Rams: thorough, Ma: breathing room. */
@Composable
private fun SectionDivider() {
    Spacer(modifier = Modifier.height(Spacing.lg.dp))
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp
    )
    Spacer(modifier = Modifier.height(Spacing.lg.dp))
}
