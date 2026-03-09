package com.sonicvault.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Oscilloscope-style wave logo. Replaces circle-dot motif.
 * Rams: as little design as possible — one clean waveform, geometric, honest.
 * Kanso: reduction to essential; communicates acoustic/sound without decoration.
 *
 * Path: quadratic bezier wave (M 12 48 Q 24 28, 36 48 T 60 48 T 84 48).
 * Size 96dp for confident presence; title stays subordinate per hierarchy.
 *
 * @param mode Kept for API compatibility; visuals are static (Rams: no unnecessary motion).
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun AudioVaultIcon(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    mode: String = "encode"
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        /* Scale from 96-unit design to canvas */
        val s = w / 96f

        val path = Path().apply {
            moveTo(12f * s, 48f * s)
            quadraticBezierTo(24f * s, 28f * s, 36f * s, 48f * s)
            quadraticBezierTo(48f * s, 68f * s, 60f * s, 48f * s)
            quadraticBezierTo(72f * s, 28f * s, 84f * s, 48f * s)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5f * (w / 96f), cap = StrokeCap.Round)
        )
    }
}
