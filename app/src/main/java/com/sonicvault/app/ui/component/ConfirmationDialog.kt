package com.sonicvault.app.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Phase 5.1: Reusable confirmation dialog for destructive actions.
 * Rams-aligned: clear text, simple buttons. Use for actions that lose user data.
 *
 * @param title Dialog title (e.g. "Clear voice enrollment?")
 * @param message Explanatory text (e.g. "You will need to re-enroll to use voice unlock.")
 * @param confirmLabel Label for confirm button (e.g. "Clear")
 * @param dismissLabel Label for cancel button (e.g. "Cancel")
 * @param onConfirm Called when user confirms the destructive action.
 * @param onDismiss Called when user cancels or dismisses.
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}
