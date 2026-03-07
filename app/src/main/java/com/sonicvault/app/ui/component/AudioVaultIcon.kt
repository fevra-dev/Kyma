package com.sonicvault.app.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Concentric circle motif from design concept (ultimate_seed_vault_0.tsx).
 * Outer ring (r=38/80), inner dot (r=8/80), mid ring (r=24/80, opacity 0.3).
 * Rams: aesthetic, unobtrusive — icon serves branding without competing with content.
 * Size 96dp (12×8 grid) for confident presence; title stays subordinate per hierarchy.
 *
 * @param mode "encode" | "decode" — Scale morph: center dot grows to fill ring (decode).
 *   Clip to ring so fill stays circular, not square. ~400ms fluid animation.
 */
@Composable
fun AudioVaultIcon(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    mode: String = "encode"
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    /* Scale morph: encode = 0.21 (center dot), decode = 1 (ring fill). Clip to ring. */
    val scale by animateFloatAsState(
        targetValue = if (mode == "encode") 0.21f else 1f,
        animationSpec = tween(400),
        label = "AudioVaultIconScale"
    )
    val innerDotOpacity by animateFloatAsState(
        targetValue = if (mode == "encode") 0f else 1f,
        animationSpec = tween(400),
        label = "AudioVaultIconInnerDot"
    )
    Canvas(modifier = modifier.size(size)) {
        val center = size.toPx() / 2f
        val outerRadius = center * 0.475f
        val ringFillRadius = center * 0.44f
        // Outer circle: stroke only; same for both modes
        drawCircle(
            color = color,
            radius = outerRadius,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5f)
        )
        // Scale morph: center dot grows to fill ring. Clip to ring so fill stays circular.
        clipPath(
            Path().apply {
                addOval(Rect(center - ringFillRadius, center - ringFillRadius, center + ringFillRadius, center + ringFillRadius))
            }
        ) {
            scale(scale, scale, Offset(center, center)) {
                drawCircle(color = color, radius = ringFillRadius)
            }
        }
        // Inner dot (decode only): surface-colored center; fades in
        if (innerDotOpacity > 0f) {
            drawCircle(
                color = surfaceColor.copy(alpha = innerDotOpacity),
                radius = center * 0.06f
            )
        }
        // Mid ring: faded stroke; same for both modes
        drawCircle(
            color = color.copy(alpha = 0.3f),
            radius = center * 0.3f,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.3f)
        )
    }
}
