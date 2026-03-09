package com.sonicvault.app.ui.screen.recovery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.ui.component.CardSection
import com.sonicvault.app.ui.component.WordByWordSeedDisplay
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Recovered seed or private key: clear label, show/hide toggle, CardSection.
 * When [isPrivateKey] true, hides word-by-word mode and shows char count.
 * Rams: understandable, honest.
 *
 * @param seedPhrase The recovered seed phrase or private key to display.
 * @param onCopy Optional callback when user taps copy.
 * @param isPrivateKey True when recovered payload is a Solana private key (no word-by-word, no Seed Vault).
 */
@Composable
fun ShowSeedStep(
    seedPhrase: String,
    onCopy: (() -> Unit)? = null,
    isPrivateKey: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showPhrase by remember { mutableStateOf(false) }
    var wordByWordMode by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    SonicVaultLogger.d("[ShowSeedStep] recovered length=${seedPhrase.length} isPrivateKey=$isPrivateKey")

    val wordCount = remember(seedPhrase) {
        seedPhrase.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
    }

    CardSection(
        modifier = modifier
            .padding(vertical = Spacing.xs.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    isPrivateKey -> "RECOVERED PRIVATE KEY"
                    wordByWordMode -> "WORD-BY-WORD"
                    else -> "RECOVERED SEED"
                },
                style = LabelUppercaseStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                /** Toggle word-by-word mode (seed phrase only). */
                if (!isPrivateKey) {
                    IconButton(onClick = { wordByWordMode = !wordByWordMode }) {
                        Text(
                            text = if (wordByWordMode) "ALL" else "1x1",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!wordByWordMode) {
                    IconButton(onClick = { showPhrase = !showPhrase }) {
                        Icon(
                            imageVector = if (showPhrase) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPhrase) "Hide seed" else "Show seed"
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm.dp))

        if (wordByWordMode) {
            /** Clipboard-free: show one word at a time with navigation arrows. */
            WordByWordSeedDisplay(seedPhrase = seedPhrase)
        } else {
            /** Standard full-field view. */
            OutlinedTextField(
                value = seedPhrase,
                onValueChange = { },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                visualTransformation = if (showPhrase) VisualTransformation.None else PasswordVisualTransformation(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        if (!wordByWordMode) {
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            Text(
                text = if (isPrivateKey) "${seedPhrase.length} chars" else "$wordCount WORDS",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.sm.dp))
            Text(
                text = "Do not screenshot. Copy only when needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        /** Copy button: hide seed (password circles) first, yield one frame, then copy. Privacy/security. */
        if (onCopy != null && !wordByWordMode) {
            Spacer(modifier = Modifier.height(Spacing.sm.dp))
            OutlinedButton(
                onClick = {
                    showPhrase = false
                    scope.launch {
                        delay(0) // Yield one frame so UI shows dots before clipboard write
                        onCopy()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape
            ) {
                Text("COPY TO CLIPBOARD")
            }
        }
    }
}

/**
 * Copies text to clipboard and schedules auto-clear after [clearDelayMs] to prevent
 * clipboard snooping by other apps. On Android 13+ the OS shows a visual confirmation;
 * on older versions this is the primary defense against clipboard leak.
 *
 * SECURITY PATCH [SVA-003]: Label is always generic ("Copied text") regardless of what
 * the caller passes. Previous labels like "Kyma" or app-specific seed labels uniquely
 * identified clipboard contents as crypto seed phrases to malicious clipboard-reading apps.
 * Auto-clear reduced from 60s to 20s to minimize exposure window.
 *
 * @param context Application context.
 * @param label Ignored — overridden with generic label to prevent fingerprinting.
 * @param text Sensitive text to copy.
 * @param clearDelayMs Milliseconds before clipboard is cleared (default 20 seconds).
 */
fun copyToClipboard(context: Context, @Suppress("UNUSED_PARAMETER") label: String, text: String, clearDelayMs: Long = CLIPBOARD_CLEAR_DELAY_MS) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    /** Use generic label to prevent clipboard-reading apps from identifying crypto seeds. */
    clipboard.setPrimaryClip(ClipData.newPlainText(CLIPBOARD_GENERIC_LABEL, text))
    /** Auto-clear clipboard after timeout to prevent snooping by other apps. */
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } catch (_: Exception) { /* best-effort */ }
    }, clearDelayMs)
}

/** Generic label prevents clipboard fingerprinting by malicious apps. */
private const val CLIPBOARD_GENERIC_LABEL = "Copied text"
/** Auto-clear after 20s (reduced from 60s to minimize exposure window). */
private const val CLIPBOARD_CLEAR_DELAY_MS = 20_000L

/** Seconds until clipboard auto-clears; use for user-facing messages. */
const val CLIPBOARD_CLEAR_DELAY_SECONDS = 20
