package com.sonicvault.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AirDrop-style radiating rings animation for sound handshake UX.
 *
 * Concentric rings expand outward from center, fading as they grow.
 * Active during BROADCASTING/LISTENING states; provides visual confirmation
 * that the device is emitting or scanning for ultrasonic data.
 *
 * @param isActive controls animation playback — false freezes rings
 * @param ringCount number of concentric rings (3-5 recommended)
 * @param color base color for rings; alpha fades with expansion
 * @param size composable size
 */
@Composable
fun RadiatingRingsAnimation(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    ringCount: Int = 4,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 160.dp
) {
    val transition = rememberInfiniteTransition(label = "radiating_rings")

    /* Each ring has a staggered phase offset for continuous ripple effect */
    val phases = (0 until ringCount).map { index ->
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 2000,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "ring_$index"
        )
        if (isActive) {
            // Stagger each ring's starting point within the cycle
            (phase + index.toFloat() / ringCount) % 1f
        } else {
            0f
        }
    }

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val maxRadius = this.size.minDimension / 2f

        /* Center dot — always visible */
        drawCircle(
            color = color,
            radius = 6.dp.toPx(),
            center = center
        )

        if (isActive) {
            phases.forEach { progress ->
                val radius = maxRadius * 0.15f + maxRadius * 0.85f * progress
                /* Alpha fades from 0.7 to 0 as ring expands */
                val alpha = (1f - progress) * 0.7f
                /* Stroke thins as ring grows */
                val strokeWidth = (3f - 2f * progress).coerceAtLeast(0.5f)

                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth.dp.toPx())
                )
            }
        }
    }
}
