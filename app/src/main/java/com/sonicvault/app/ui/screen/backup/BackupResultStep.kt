package com.sonicvault.app.ui.screen.backup

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.ui.component.CardSection
import com.sonicvault.app.ui.component.SuccessCelebration
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing
import com.sonicvault.app.util.ExportFormat

/**
 * Clear success or error. Share buttons use FileProvider so user can save to Photos/Drive.
 * Options: "Export as WAV" (universal) | "Export as FLAC" (50–70% smaller, lossless).
 * Displays checksum when present (Rams: honest, transparent).
 */
@Composable
fun BackupResultStep(
    stegoUri: Uri?,
    errorMessage: String?,
    checksum: String? = null,
    fingerprint: Bitmap? = null,
    shortId: String? = null,
    onShare: ((ExportFormat) -> Unit)? = null,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    if (stegoUri != null) {
        SonicVaultLogger.i("[BackupResultStep] success checksum=${checksum?.take(8)}...")
        LaunchedEffect(stegoUri) { view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM) }
    } else if (errorMessage != null) {
        SonicVaultLogger.i("[BackupResultStep] error message=$errorMessage")
    }

    Column(modifier = modifier.padding(vertical = Spacing.sm.dp)) {
        SuccessCelebration(trigger = stegoUri != null)
        if (stegoUri != null) {
            Text(
                text = "Backup created.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            Text(
                text = "Share to save in Google Photos, Drive, or Files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            Text(
                text = "Tip: Use a generic name like voice_memo.wav or Vacation_2024.wav when saving.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (checksum != null) {
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                CardSection(modifier = Modifier.padding(vertical = Spacing.xs.dp)) {
                    Text(
                        text = "CHECKSUM",
                        style = LabelUppercaseStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Text(
                        text = "$checksum...",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (fingerprint != null || shortId != null) {
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                CardSection(modifier = Modifier.padding(vertical = Spacing.xs.dp)) {
                    Text(
                        text = "FINGERPRINT",
                        style = LabelUppercaseStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (fingerprint != null) {
                            Image(
                                bitmap = fingerprint.asImageBitmap(),
                                contentDescription = "Sound fingerprint identicon",
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm.dp))
                        }
                        if (shortId != null) {
                            Text(
                                text = "#$shortId",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (onShare != null) {
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                /* FLAC export disabled: app crashes. TODO: fix FlacExporter/ShareHelper and re-enable. */
                OutlinedButton(onClick = { onShare(ExportFormat.WAV) }) {
                    Text("Export as WAV")
                }
            }
        } else if (errorMessage != null) {
            LaunchedEffect(errorMessage) { view.performHapticFeedback(HapticFeedbackConstantsCompat.REJECT) }
        }
        Spacer(modifier = Modifier.height(Spacing.md.dp))
    }
}
