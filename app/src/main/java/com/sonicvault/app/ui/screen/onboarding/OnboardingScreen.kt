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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonicvault.app.ui.component.AudioVaultIcon
import com.sonicvault.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * First-time onboarding: 4 screens introducing Kyma's primary features.
 * Lead with acoustic transactions (core value), end with seed backup (secondary).
 * Rams: useful, understandable, minimal.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val pageCount = 4
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val lastPage = pageCount - 1

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
                    title = "Pay By Ear",
                    body = "Hold two devices close.\nTransmit encrypted Solana transactions\nthrough near-ultrasonic sound."
                )
                1 -> OnboardingPage(
                    title = "Sign in silence",
                    body = "Air-gapped cold signing via Seed Vault TEE. Your private key never leaves secure hardware. Sound is the only bridge."
                )
                2 -> OnboardingPage(
                    title = "Set up your nonce pool",
                    body = "Durable nonce accounts keep your transactions valid during acoustic transfer. Set up once in Settings, use everywhere."
                )
                3 -> OnboardingPage(
                    title = "Your seed, hidden in sound",
                    body = "Back up your seed phrase inside audio files. Steganographically embedded, invisible to the ear. Zero cloud. All local."
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pageCount) { index ->
                if (index > 0) Spacer(modifier = Modifier.size(Spacing.xs.dp))
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
        Button(
            shape = RectangleShape,
            onClick = {
                if (pagerState.currentPage < lastPage) {
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
                text = if (pagerState.currentPage < lastPage) "NEXT" else "GET STARTED"
            )
        }
        if (pagerState.currentPage < lastPage) {
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
