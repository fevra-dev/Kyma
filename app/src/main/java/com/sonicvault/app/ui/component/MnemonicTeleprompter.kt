package com.sonicvault.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing

/**
 * Teleprompter: one word at a time, large font.
 *
 * Rams: useful, understandable. Ma: breathing room.
 * User enters each word into Seed Vault manually; no programmatic import.
 *
 * @param words BIP39 mnemonic words (12 or 24)
 * @param currentIndex 0-based index of word being displayed
 * @param onNext Called when user taps "Entered — Next"
 * @param onDone Called when user taps "All Done" (on last word)
 * @param onBack Called when user taps "Back" for typo correction (optional)
 */
@Composable
fun MnemonicTeleprompter(
    words: List<String>,
    currentIndex: Int,
    onNext: () -> Unit,
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (words.isEmpty()) return

    val currentWord = words.getOrNull(currentIndex) ?: ""
    val nextWord = words.getOrNull(currentIndex + 1)
    val isLast = currentIndex >= words.size - 1
    val canGoBack = currentIndex > 0 && onBack != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.md.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg.dp)
    ) {
        LinearProgressIndicator(
            progress = { (currentIndex + 1).toFloat() / words.size },
            modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text = "${currentIndex + 1} of ${words.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.sm.dp))
        Text(
            text = "Type this word:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = currentWord,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        nextWord?.let { next ->
            Text(
                text = "Next: $next",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(Spacing.lg.dp))
        if (canGoBack || !isLast) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)
            ) {
                if (canGoBack) {
                    OutlinedButton(
                        onClick = { onBack?.invoke() },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                    ) {
                        Text("← BACK")
                    }
                }
                Button(
                    onClick = if (isLast) onDone else onNext,
                    modifier = Modifier
                        .weight(if (canGoBack) 2f else 1f)
                        .heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (isLast) "ALL DONE" else "ENTERED — NEXT",
                        style = LabelUppercaseStyle
                    )
                }
            }
        } else {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("ALL DONE", style = LabelUppercaseStyle)
            }
        }
    }
}
