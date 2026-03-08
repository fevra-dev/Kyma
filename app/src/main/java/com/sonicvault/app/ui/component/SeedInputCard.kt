package com.sonicvault.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.ui.theme.JetBrainsMonoFamily
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing
import com.sonicvault.app.util.SolanaPrivateKeyValidator

/**
 * Seed phrase or private key input with label, eye toggle, word/char count.
 * Detects input type: seed phrase (space-separated words) vs private key (long base58 string).
 * Shows word count for seed, char count for private key. Rams: useful, understandable.
 *
 * @param compactForFixedLayout When true, text field shows 3 lines. Keeps layout fixed.
 */
@Composable
fun SeedInputCard(
    seedPhrase: String,
    onSeedPhraseChange: (String) -> Unit,
    showPhrase: Boolean,
    onShowPhraseChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Optional hint under seed input (e.g. wallet compatibility). */
    hintText: String? = null,
    /** 3-line text field for fixed screens (12 words fit); Rams: as little as possible. */
    compactForFixedLayout: Boolean = false
) {
    val trimmed = remember(seedPhrase) { seedPhrase.trim() }
    val words = remember(trimmed) { trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() } }
    val wordCount = words.size
    val charCount = trimmed.length

    val isPrivateKeyMode = remember(charCount, words) {
        charCount > 50 && words.size <= 1 ||
        words.any { it.length > 10 }
    }

    SonicVaultLogger.d("[SeedInputCard] wordCount=$wordCount charCount=$charCount isPrivateKeyMode=$isPrivateKeyMode")

    val label = if (isPrivateKeyMode) "PRIVATE KEY" else "SEED PHRASE"
    val placeholder = if (isPrivateKeyMode) "Paste Solana private key (base58)" else "12 or 24 recovery words"
    val (countText, isValid) = if (isPrivateKeyMode) {
        val pkValid = charCount in 80..95 && trimmed.all { it in "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz" }
        (if (pkValid) "$charCount / 88 VALID" else "$charCount chars") to pkValid
    } else {
        val seedValid = wordCount == 12 || wordCount == 24
        val target = if (wordCount > 12) 24 else 12
        (if (seedValid) "$wordCount / $target VALID" else "$wordCount / $target words") to seedValid
    }

    CardSection(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = LabelUppercaseStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = { onShowPhraseChange(!showPhrase) },
                modifier = Modifier
            ) {
                Icon(
                    imageVector = if (showPhrase) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (showPhrase) "Hide" else "Show"
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm.dp))
        OutlinedTextField(
            value = seedPhrase,
            onValueChange = onSeedPhraseChange,
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
            visualTransformation = if (showPhrase) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compactForFixedLayout) 56.dp else 120.dp),
            minLines = if (compactForFixedLayout) 3 else 3,
            maxLines = if (compactForFixedLayout) 3 else 6,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
        if (hintText != null) {
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            Text(
                text = hintText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xs.dp))
        Text(
            text = countText,
            style = MaterialTheme.typography.bodySmall,
            color = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
