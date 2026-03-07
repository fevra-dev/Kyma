package com.sonicvault.app.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Clipboard-free seed phrase display: shows one word at a time with its index.
 *
 * User taps forward/back arrows to navigate through words. No clipboard involved,
 * no full phrase visible at once. Minimizes risk of shoulder surfing and clipboard
 * snooping by malicious apps.
 *
 * Rams: "Good design is as little design as possible." One word, one number, two arrows.
 *
 * @param seedPhrase Full seed phrase (space-separated words).
 * @param modifier Compose modifier.
 */
@Composable
fun WordByWordSeedDisplay(
    seedPhrase: String,
    modifier: Modifier = Modifier
) {
    val words = remember(seedPhrase) {
        seedPhrase.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }
    var currentIndex by remember { mutableIntStateOf(0) }

    if (words.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        /** Word counter: "WORD 3 OF 12" */
        Text(
            text = "WORD ${currentIndex + 1} OF ${words.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        /** Animated word display with slide transition. */
        AnimatedContent(
            targetState = currentIndex,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally { direction * it / 3 } + fadeIn())
                    .togetherWith(slideOutHorizontally { -direction * it / 3 } + fadeOut())
            },
            label = "word_transition"
        ) { index ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                /** Word index number — large, muted. */
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.W300,
                        fontSize = 48.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                /** The word itself — monospace, prominent. */
                Text(
                    text = words.getOrElse(index) { "" },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.W500
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        /** Navigation arrows — wraps around from last word back to first. */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    currentIndex = if (currentIndex > 0) currentIndex - 1 else words.size - 1
                }
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous word",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "${currentIndex + 1} / ${words.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            IconButton(
                onClick = {
                    currentIndex = if (currentIndex < words.size - 1) currentIndex + 1 else 0
                }
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next word",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "No clipboard needed.\nVerify each word carefully.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}
