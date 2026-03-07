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

/**
 * Seed phrase input with label, eye toggle, word count, validity.
 * Label: "SEED PHRASE" (uppercase). Rams: useful, understandable.
 *
 * @param compactForFixedLayout When true, text field shows 3 lines only (12 words fit). Keeps layout fixed.
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
    val wordCount = remember(seedPhrase) {
        seedPhrase.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
    }
    SonicVaultLogger.d("[SeedInputCard] wordCount=$wordCount")

    CardSection(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SEED PHRASE",
                style = LabelUppercaseStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = { onShowPhraseChange(!showPhrase) },
                modifier = Modifier
            ) {
                Icon(
                    imageVector = if (showPhrase) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (showPhrase) "Hide seed" else "Show seed"
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm.dp))
        OutlinedTextField(
            value = seedPhrase,
            onValueChange = onSeedPhraseChange,
            placeholder = { Text("Enter your recovery phrase", style = MaterialTheme.typography.bodyMedium) },
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
        /* Optional hint: only when hintText is explicitly provided (not the default). */
        if (hintText != null) {
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            Text(
                text = hintText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xs.dp))
        /* Word count with target: "0 / 12 words" progresses to "12 / 12 VALID". Rams: honest. */
        val isValid = wordCount == 12 || wordCount == 24
        val target = if (wordCount > 12) 24 else 12
        Text(
            text = if (isValid) "$wordCount / $target VALID" else "$wordCount / $target words",
            style = MaterialTheme.typography.bodySmall,
            color = if (isValid) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
