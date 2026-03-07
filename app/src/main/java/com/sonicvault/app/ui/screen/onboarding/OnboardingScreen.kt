package com.sonicvault.app.ui.screen.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonicvault.app.ui.component.AudioVaultIcon
import com.sonicvault.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * First-time onboarding: 3 screens explaining backup, recovery, and local-only storage.
 * Rams: useful, understandable, minimal.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(Spacing.md.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            userScrollEnabled = true
        ) { page ->
            when (page) {
                0 -> OnboardingPage(
                    title = "Your seed phrase, hidden in sound",
                    body = "Create a backup file that looks like an ordinary audio recording. Your seed is steganographically embedded—invisible to the ear."
                )
                1 -> OnboardingPage(
                    title = "Recover anytime from your audio file",
                    body = "When you need to restore, select your backup file and decode. Enter your password if you encrypted it."
                )
                2 -> OnboardingPage(
                    title = "Zero cloud. Zero tracking. All local.",
                    body = "Data never leaves your device. You control your backup files. All processing happens on your phone—your seed never leaves it."
                )
            }
        }
        /* Page indicator dots: filled for current page, outline for others. Rams: feedback. */
        Row(
            modifier = Modifier.padding(top = Spacing.sm.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp)
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == index)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.md.dp))
        /** Bottom button: extra margin clears gesture-nav / system bar on edge-to-edge. */
        Button(
            onClick = {
                if (pagerState.currentPage < 2) {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                } else {
                    onComplete()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (pagerState.currentPage < 2) "NEXT" else "GET STARTED"
            )
        }
        /* Skip: lowest visual weight, only on pages before the last. Rams: unobtrusive. */
        if (pagerState.currentPage < 2) {
            TextButton(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SKIP")
            }
        } else {
            Spacer(modifier = Modifier.height(Spacing.sm.dp))
        }
    }
}

@Composable
private fun OnboardingPage(
    title: String,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = Spacing.md.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AudioVaultIcon()
        Spacer(modifier = Modifier.height(Spacing.xl.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.md.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
