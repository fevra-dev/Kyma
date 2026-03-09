package com.sonicvault.app.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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
 * Home: TRANSMIT/RECEIVE as primary actions (acoustic transactions),
 * BACKUP/RESTORE as secondary under "VAULT INFRASTRUCTURE" divider.
 * Rams: useful, understandable, unobtrusive. Less, but better.
 * Ma: generous spacing between tiers; Kanso: clear hierarchy.
 */
@Composable
fun HomeScreen(
    onCreateBackup: () -> Unit,
    onRecover: () -> Unit,
    onTransmitSound: () -> Unit = {},
    onReceiveSound: () -> Unit = {},
    onSettings: () -> Unit = {},
    onBackupGallery: () -> Unit = {}
) {
    val context = LocalContext.current
    val userPrefs = remember { UserPreferences(context) }
    val lastBackupText = remember(userPrefs.lastBackupTimestamp) {
        formatTimeAgo(userPrefs.lastBackupTimestamp)
    }
    val isEmulator = isEmulator()
    val rootResult = remember { RootDetector.check() }
    val padH = Spacing.sm.dp
    val padV = Spacing.md.dp

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
                Spacer(modifier = Modifier.height(Spacing.xl.dp))

                /* ── Primary actions: TRANSMIT / RECEIVE ── Ma: generous size, confident touch targets ── */
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 180.dp)
                            .clickable(onClick = onTransmitSound),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(Spacing.md.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            IconTransmit(
                                size = 56.dp,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm.dp))
                            Text("TRANSMIT", style = LabelUppercaseStyle)
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 180.dp)
                            .clickable(onClick = onReceiveSound),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(Spacing.md.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            IconReceive(
                                size = 56.dp,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm.dp))
                            Text("RECEIVE", style = LabelUppercaseStyle)
                        }
                    }
                }

                /* Ma: 64dp between major zones — breathing room before vault (Rams grid) */
                Spacer(modifier = Modifier.height(64.dp))

                /* ── Divider: VAULT INFRASTRUCTURE ── */
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "VAULT INFRASTRUCTURE",
                        style = LabelUppercaseStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.sm.dp),
                        textAlign = TextAlign.Center
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md.dp))

                /* ── Last backup status (clickable → gallery) ── */
                val lastBackupTextContent = if (userPrefs.lastBackupTimestamp > 0) "Last backup: $lastBackupText" else "No backups yet"
                Text(
                    text = lastBackupTextContent,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (userPrefs.lastBackupTimestamp > 0) Modifier.clickable(onClick = onBackupGallery) else Modifier
                )

                Spacer(modifier = Modifier.height(Spacing.md.dp))

                /* ── Secondary actions: BACKUP / RESTORE ── subdued text buttons ── */
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp)
                ) {
                    TextButton(
                        onClick = onCreateBackup,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Icon(
                            Icons.Outlined.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.sizeIn(maxWidth = 18.dp, maxHeight = 18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs.dp))
                        Text(
                            "BACKUP",
                            style = LabelUppercaseStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = onRecover,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Icon(
                            Icons.Outlined.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.sizeIn(maxWidth = 18.dp, maxHeight = 18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs.dp))
                        Text(
                            "RESTORE",
                            style = LabelUppercaseStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

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
