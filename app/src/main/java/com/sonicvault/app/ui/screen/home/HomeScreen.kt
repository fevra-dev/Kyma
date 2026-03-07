package com.sonicvault.app.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonicvault.app.data.preferences.UserPreferences
import com.sonicvault.app.ui.component.AudioVaultIcon
import com.sonicvault.app.ui.component.icons.IconTransmit
import com.sonicvault.app.ui.component.icons.IconReceive
import com.sonicvault.app.data.security.RootDetector
import com.sonicvault.app.util.isEmulator
import com.sonicvault.app.util.formatTimeAgo
import com.sonicvault.app.ui.theme.HeadlineLargeStyle
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing
import androidx.compose.runtime.remember

/**
 * Home: branding (icon, title, tagline), 4 flat action buttons (BACKUP, RESTORE, TRANSMIT, RECEIVE).
 * Rams: useful, understandable, unobtrusive. Less, but better.
 * Japanese Ma: generous spacing; Kanso: essential elements; Human scale: proportions for touch.
 */
@Composable
fun HomeScreen(
    onCreateBackup: () -> Unit,
    onRecover: () -> Unit,
    onTransmitSound: () -> Unit = {},
    onReceiveSound: () -> Unit = {},
    onSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val userPrefs = remember { UserPreferences(context) }
    val lastBackupText = remember(userPrefs.lastBackupTimestamp) {
        formatTimeAgo(userPrefs.lastBackupTimestamp)
    }
    val isEmulator = isEmulator()
    /** Root detection: check once per composition; cached in remember. */
    val rootResult = remember { RootDetector.check() }
    val padH = Spacing.sm.dp
    val padV = Spacing.md.dp

    /**
     * BoxWithConstraints + heightIn(min) pattern: centers content when it fits the screen,
     * enables scrolling when content overflows (large font / display sizes).
     */
    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = padH)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = screenHeight)
                    .padding(vertical = padV)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                /* Emulator banner: mic and full features work best on real device */
                if (isEmulator) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Use a real device for microphone and full features.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(Spacing.sm.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                }
                /* Root/Magisk detection warning. Rams: honest — user deserves to know security is compromised. */
                if (rootResult.isRooted) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Security warning: rooted device detected (${rootResult.signalCount} signal${if (rootResult.signalCount > 1) "s" else ""}). " +
                                   "Encryption keys may be extractable. Proceed with caution.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(Spacing.sm.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                }
                AudioVaultIcon(modifier = Modifier)
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                /* Brand: KYMA. Work Sans SemiBold reinforces code/security vibe. */
                Text(
                    text = "KYMA",
                    style = HeadlineLargeStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "ACOUSTIC CRYPTOGRAPHY",
                    style = LabelUppercaseStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                /* Stickiness signal: last backup timestamp. Rams: honest feedback. */
                Text(
                    text = if (userPrefs.lastBackupTimestamp > 0) "Last backup: $lastBackupText" else "No backups yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm.dp)
                )
                Spacer(modifier = Modifier.height(Spacing.xl.dp))

                /* 2x2 action grid: BACKUP, RESTORE, TRANSMIT, RECEIVE. Rams: understandable, as little design as possible. */
                val buttonMod = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                val soundButtonMod = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)
                    ) {
                        Button(
                            onClick = onCreateBackup,
                            modifier = buttonMod,
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("BACKUP", style = LabelUppercaseStyle)
                        }
                        Button(
                            onClick = onRecover,
                            modifier = buttonMod,
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("RESTORE", style = LabelUppercaseStyle)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTransmitSound,
                            modifier = soundButtonMod,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                IconTransmit(
                                    size = 24.dp,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("TRANSMIT", style = LabelUppercaseStyle)
                            }
                        }
                        OutlinedButton(
                            onClick = onReceiveSound,
                            modifier = soundButtonMod,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                IconReceive(
                                    size = 24.dp,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("RECEIVE", style = LabelUppercaseStyle)
                            }
                        }
                    }
                }
            }
        }

        /** Settings: pinned to bottom, stays visible even when content scrolls. 44dp touch target for accessibility. */
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
                .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }
    }
}
