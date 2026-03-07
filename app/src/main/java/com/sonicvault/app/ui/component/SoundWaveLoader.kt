package com.sonicvault.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.pow

/**
 * Minimal Pulse Ring loader — concentric rings expand outward from a static center dot.
 * Inspired by Dieter Rams' design: "One action, one response. Pure signal."
 * Ported from animation-concepts.html Section 1G (Minimal Pulse Ring).
 *
 * Three staggered rings expand and fade — calm, precise, geometric.
 * Rams: as little design as possible — purposeful, no decoration.
 */
@Composable
fun SoundWaveLoader(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 120.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseRingLoader")

    /* Three ring phases, staggered by 0.33 of the cycle for continuous visual flow. */
    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1"
    )
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2"
    )
    val ring3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring3"
    )

    val color = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(sizeDp)) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val maxRadius = size.minDimension / 2f

            /* Static center dot — anchor point. Rams: honest, stable. */
            drawCircle(
                color = color,
                radius = 6f,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY)
            )

            /* Expanding rings with ease-out deceleration and fade. */
            listOf(ring1, ring2, ring3).forEach { life ->
                val ease = 1f - (1f - life).pow(2)
                val radius = ease * maxRadius
                /* Alpha fades from 0.5 to 0 as ring expands; stroke thins with expansion. */
                val alpha = (1f - life) * 0.5f
                val strokeWidth = 1.5f * (1f - life) + 0.5f

                if (alpha > 0.01f) {
                    drawCircle(
                        color = color.copy(alpha = alpha),
                        radius = radius,
                        center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                        style = Stroke(width = strokeWidth)
                    )
                }
            }
        }
    }
}
