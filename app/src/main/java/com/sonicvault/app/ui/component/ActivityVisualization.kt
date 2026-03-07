package com.sonicvault.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Animated spectral-style visualization during transmit/receive.
 * Simulates activity bars (no real FFT) to give users confidence that transmission is active.
 * Accessibility: ContentDescription should be set by parent.
 */
@Composable
fun ActivityVisualization(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "activity_bars")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val barCount = 32
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        val barWidth = size.width / barCount
        for (i in 0 until barCount) {
            val heightFraction = 0.2f + 0.8f * (0.5f + 0.5f * sin(2 * PI * (phase + i * 0.1f)).toFloat())
            val barHeight = size.height * heightFraction
            val left = i * barWidth
            drawRect(
                color = color.copy(alpha = 0.5f + 0.5f * heightFraction),
                topLeft = Offset(left, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, barHeight)
            )
        }
    }
}
