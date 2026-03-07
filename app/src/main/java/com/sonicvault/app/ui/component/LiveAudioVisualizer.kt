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
import androidx.compose.ui.unit.dp
import com.sonicvault.app.domain.model.Protocol
import kotlin.math.PI
import kotlin.math.sin

/**
 * Unified audio visualizer that renders real FFT data when available,
 * or falls back to protocol-aware simulation for idle/encoding states.
 *
 * Accepts either FFT magnitude array or amplitude history list.
 * Renders Rams-style bar visualization with protocol-aware coloring:
 * - Ultrasonic: bars concentrate in high-frequency region
 * - Audible: bars spread across full spectrum
 *
 * Rams: honest (real data when available), functional (clear audio feedback),
 * as little design as possible (minimal decorative elements).
 */
@Composable
fun LiveAudioVisualizer(
    fftData: FloatArray,
    amplitudeHistory: List<Float>,
    protocol: Protocol,
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    heightDp: Int = 64
) {
    val color = MaterialTheme.colorScheme.primary
    val hasRealData = fftData.isNotEmpty()
    val hasAmplitude = amplitudeHistory.isNotEmpty()

    /* Fallback animation phase for when no real data is available */
    val infiniteTransition = rememberInfiniteTransition(label = "liveViz")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vizPhase"
    )

    /* Protocol-aware frequency bin weighting for simulated mode */
    val (lowBin, highBin) = when (protocol) {
        Protocol.ULTRASONIC -> (barCount * 0.5f).toInt() to barCount
        Protocol.AUDIBLE -> 0 to barCount
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
    ) {
        val barWidth = size.width / barCount
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val barHeight: Float
            val alpha: Float

            if (hasRealData) {
                /* Real FFT data: map frequency bins to bar positions */
                val binIndex = (i * fftData.size / barCount).coerceIn(0, fftData.size - 1)
                val magnitude = fftData[binIndex].coerceIn(0f, 1f)
                barHeight = size.height * 0.9f * magnitude
                alpha = 0.3f + 0.6f * magnitude
            } else if (hasAmplitude) {
                /* Amplitude fallback: distribute amplitude history across bars */
                val ampIndex = (i * amplitudeHistory.size / barCount).coerceIn(0, amplitudeHistory.size - 1)
                val amp = amplitudeHistory[ampIndex].coerceIn(0f, 1f)
                barHeight = size.height * 0.8f * amp
                alpha = 0.3f + 0.5f * amp
            } else {
                /* Simulated: protocol-aware animation for visual feedback during encoding */
                val inRange = i in lowBin until highBin
                val baseHeight = if (inRange) {
                    0.2f + 0.6f * (0.5f + 0.5f * sin(2 * PI * (phase + i * 0.08f)).toFloat())
                } else {
                    0.03f * (0.5f + 0.5f * sin(2 * PI * (phase + i * 0.1f)).toFloat())
                }
                barHeight = size.height * baseHeight
                alpha = 0.2f + 0.4f * baseHeight
            }

            val left = i * barWidth

            if (hasRealData || hasAmplitude) {
                /* Mirror style: bars extend above and below center line for real/amplitude data */
                val halfBar = barHeight / 2f
                drawRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(left, centerY - halfBar),
                    size = androidx.compose.ui.geometry.Size(barWidth * 0.85f, barHeight.coerceAtLeast(2f))
                )
            } else {
                /* Bottom-anchored bars for simulated mode (standard spectrogram look) */
                drawRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(left, size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth * 0.85f, barHeight.coerceAtLeast(1f))
                )
            }
        }
    }
}
