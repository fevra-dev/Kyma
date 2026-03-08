package com.sonicvault.app.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonicvault.app.BuildConfig
import com.sonicvault.app.data.preferences.UserPreferences
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing
import com.sonicvault.app.util.FeatureFlags

/**
 * Settings: voice unlock, sound transfer, nonce pool, FAQ, recovery guide.
 * Rams: useful (grouped by category), understandable (clear labels),
 * as little design as possible (no decorative elements), thorough (all states considered).
 * Scrollable with nav bar padding for edge-to-edge support.
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
    onRecombineSeed: () -> Unit = {},
    onCnftDrop: () -> Unit = {},
    onPresenceOracle: () -> Unit = {},
    onGuardianVote: () -> Unit = {}
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
            Text(
                text = "Two protocols: Audible (2–6 kHz) & Ultrasonic (18–20 kHz).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.sm.dp))
            /* Anti-fingerprint toggle: randomizes ultrasonic spectral envelope per session */
            val context = LocalContext.current
            val userPrefs = remember { UserPreferences(context) }
            var useAntiFingerprint by remember { mutableStateOf(userPrefs.useAntiFingerprint) }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Anti-fingerprint (ultrasonic)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Randomizes spectral envelope to reduce device identification. On by default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useAntiFingerprint,
                    onCheckedChange = {
                        useAntiFingerprint = it
                        userPrefs.useAntiFingerprint = it
                    }
                )
            }
            Spacer(modifier = Modifier.height(Spacing.sm.dp))
            OutlinedButton(
                onClick = onDeadDrop,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium
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
                shape = MaterialTheme.shapes.medium
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

            /* ── SECURITY ── */
            SectionHeader(title = "SECURITY")
            Text(
                text = "All data stays on your device. Nothing is uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm.dp)
            )
            if (FeatureFlags.VOICE_BIOMETRIC_ENABLED) {
                OutlinedButton(
                    onClick = onVoiceUnlock,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("VOICE UNLOCK", style = LabelUppercaseStyle)
                }
                Text(
                    text = "Enroll your voice for biometric authentication. On-device only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs.dp)
                )
            }

            SectionDivider()

            /* ── ADVANCED ── */
            SectionHeader(title = "ADVANCED")
            OutlinedButton(
                onClick = onMatryoshka,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium
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
                shape = MaterialTheme.shapes.medium
            ) {
                Text("SPLIT BACKUP", style = LabelUppercaseStyle)
            }
            Text(
                text = "Shamir 2-of-3: split seed into 3 shares, recover with any 2.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            OutlinedButton(
                onClick = onRecombineSeed,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("RECOVER SHARES", style = LabelUppercaseStyle)
            }
            Text(
                text = "Recombine Shamir shares to restore your seed phrase.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs.dp)
            )
            SectionDivider()

            /* ── TIER 2 DEMOS ── */
            SectionHeader(title = "TIER 2 DEMOS")
            OutlinedButton(
                onClick = onCnftDrop,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("cNFT ACOUSTIC DROP", style = LabelUppercaseStyle)
            }
            Text(
                text = "Listen for event_id, claim compressed NFT to wallet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            OutlinedButton(
                onClick = onPresenceOracle,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("PRESENCE ORACLE", style = LabelUppercaseStyle)
            }
            Text(
                text = "Acoustic certificate assembly with dual-signature flow.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            OutlinedButton(
                onClick = onGuardianVote,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("GUARDIAN VOTING", style = LabelUppercaseStyle)
            }
            Text(
                text = "Broadcast proposal, receive vote via acoustic return.",
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
                shape = MaterialTheme.shapes.medium
            ) {
                Text("FAQ", style = LabelUppercaseStyle)
            }
            Spacer(modifier = Modifier.height(Spacing.sm.dp))
            OutlinedButton(
                onClick = onRecoveryGuide,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("RECOVERY GUIDE", style = LabelUppercaseStyle)
            }

            SectionDivider()

            /* ── ABOUT ── */
            SectionHeader(title = "ABOUT")
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

/** Category header label. Rams: clear hierarchy via consistent typography + color. */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
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
