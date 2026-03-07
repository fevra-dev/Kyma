package com.sonicvault.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Real-time waveform visualization of mic input during recording.
 * Mirror style: bars extend above and below center line for visual prominence.
 * Rams: honest (real amplitude data), functional (clear audio feedback),
 * as little design as possible (monochrome bars, no decorative elements).
 *
 * @param heightDp Visualization height. Default 64dp for prominent display during recording.
 * @param mirror When true, bars extend above and below center. When false, center-anchored only.
 */
@Composable
fun WaveformView(
    amplitudeHistory: List<Float>,
    modifier: Modifier = Modifier,
    heightDp: Dp = 64.dp,
    mirror: Boolean = true,
    tint: Color? = null
) {
    val color = tint ?: MaterialTheme.colorScheme.primary
    val barCount = amplitudeHistory.size.coerceAtLeast(1)
    val history = if (amplitudeHistory.isEmpty()) listOf(0f) else amplitudeHistory

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
    ) {
        val barWidth = size.width / barCount
        val centerY = size.height / 2f

        for (i in history.indices) {
            val amp = history[i].coerceIn(0f, 1f)
            val left = i * barWidth
            val alpha = 0.3f + 0.6f * amp

            if (mirror) {
                /* Mirror style: bars extend above and below center line for visual impact. */
                val halfBar = (size.height * 0.45f) * amp
                drawRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(left, centerY - halfBar),
                    size = androidx.compose.ui.geometry.Size(
                        barWidth * 0.85f,
                        (halfBar * 2f).coerceAtLeast(2f)
                    )
                )
            } else {
                /* Simple upward bars from center (legacy mode). */
                val barHeight = (size.height * 0.45f) * amp
                drawRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(left, centerY - barHeight),
                    size = androidx.compose.ui.geometry.Size(
                        barWidth * 0.85f,
                        barHeight.coerceAtLeast(2f)
                    )
                )
            }
        }
    }
}
