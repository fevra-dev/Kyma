package com.sonicvault.app.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Minimal amplitude visualizer inspired by Dieter Rams' design principles.
 *
 * Renders centered vertical bars that breathe with audio amplitude data.
 * "Less, but better." — Nothing unnecessary; each bar represents a data point.
 *
 * Ported from the HTML concept in design/animation-concepts.html (Section 2F).
 *
 * @param amplitudeHistory Rolling list of amplitude values (0f..1f), typically 24-48 entries.
 * @param modifier Compose modifier.
 * @param barCount Number of bars to render (default 24).
 * @param barWidth Width of each bar in dp (default 5dp).
 * @param barGap Gap between bars in dp (default 3dp).
 * @param height Total height of the visualizer.
 * @param barColor Base color for bars (alpha modulated by amplitude).
 * @param minBarHeight Minimum bar height even when silent (keeps visual presence).
 */
@Composable
fun MinimalAmplitudeVisualizer(
    amplitudeHistory: List<Float>,
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    barWidth: Dp = 5.dp,
    barGap: Dp = 3.dp,
    height: Dp = 100.dp,
    barColor: Color = Color.White,
    minBarHeight: Dp = 4.dp
) {
    /**
     * Map amplitude history to exactly [barCount] values.
     * If fewer values available, pad with zeros on the left.
     * If more, take the last [barCount] entries.
     */
    val amplitudes = remember(amplitudeHistory, barCount) {
        when {
            amplitudeHistory.size >= barCount ->
                amplitudeHistory.takeLast(barCount)
            amplitudeHistory.isEmpty() ->
                List(barCount) { 0f }
            else -> {
                val padding = List(barCount - amplitudeHistory.size) { 0f }
                padding + amplitudeHistory
            }
        }
    }

    /** Animate each bar height for smooth transitions. */
    val animatedAmplitudes = amplitudes.mapIndexed { index, amp ->
        val targetAmp = amp.coerceIn(0f, 1f)
        val animated by animateFloatAsState(
            targetValue = targetAmp,
            animationSpec = tween(durationMillis = 80),
            label = "bar_$index"
        )
        animated
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barWidthPx = barWidth.toPx()
        val barGapPx = barGap.toPx()
        val minBarHeightPx = minBarHeight.toPx()
        val totalBarsWidth = barCount * barWidthPx + (barCount - 1) * barGapPx
        val startX = (canvasWidth - totalBarsWidth) / 2f
        val centerY = canvasHeight / 2f

        for (i in 0 until barCount) {
            val amp = if (i < animatedAmplitudes.size) animatedAmplitudes[i] else 0f
            val barHeight = (amp * (canvasHeight - minBarHeightPx * 2) + minBarHeightPx)
                .coerceAtLeast(minBarHeightPx)
            val alpha = (0.2f + amp * 0.6f).coerceIn(0.1f, 0.9f)
            val x = startX + i * (barWidthPx + barGapPx)
            val y = centerY - barHeight / 2f

            drawRect(
                color = barColor.copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(barWidthPx, barHeight)
            )
        }
    }
}
