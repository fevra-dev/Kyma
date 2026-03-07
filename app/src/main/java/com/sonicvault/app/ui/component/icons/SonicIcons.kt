package com.sonicvault.app.ui.component.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimal custom icons for Rams-aligned UI. Canvas-based to avoid asset bloat.
 * Arrow + wave pattern: communicates direction (in/out) and medium (sound) simultaneously.
 * Rams #4 (understandable): self-explanatory without text.
 * Rams #10 (as little as possible): two visual elements — arrow and arcs — nothing more.
 */

/**
 * Transmit: bold diagonal arrow ↗ with 3 concentric wave arcs radiating from tip.
 * Faithful to Braun-inspired concept: arrow on the left half, waves on the right.
 * Arrow is thick/bold (not hairline) — industrial, purposeful.
 *
 * Layout within canvas (matches concept reference):
 *   [arrow ↗]  [))) waves]
 *
 * @param tint Override color; defaults to onSurface for theme-awareness.
 */
@Composable
fun IconTransmit(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 24.dp,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(size)) {
        val w = size.toPx()
        /** Bold stroke — matches the thick industrial look of the concept. */
        val arrowStroke = w * 0.09f
        val arcStroke = w * 0.065f
        val capStyle = StrokeCap.Round

        /* --- Arrow shaft: bottom-left → upper-right (≈45°) --- */
        val shaftStart = Offset(w * 0.05f, w * 0.75f)
        val shaftEnd = Offset(w * 0.42f, w * 0.22f)
        drawLine(tint, shaftStart, shaftEnd, strokeWidth = arrowStroke, cap = capStyle)

        /* --- Arrowhead: two chevron strokes from the tip --- */
        val headLen = w * 0.20f
        /* Arrow angle (direction shaft points): ~315° in standard canvas coords (up-right) */
        val dx = shaftEnd.x - shaftStart.x
        val dy = shaftEnd.y - shaftStart.y
        val arrowAngle = kotlin.math.atan2(dy.toDouble(), dx.toDouble())
        val spread = Math.toRadians(38.0) // Chevron opening angle
        drawLine(
            tint, shaftEnd,
            Offset(
                shaftEnd.x - (headLen * cos(arrowAngle - spread)).toFloat(),
                shaftEnd.y - (headLen * sin(arrowAngle - spread)).toFloat()
            ),
            strokeWidth = arrowStroke, cap = capStyle
        )
        drawLine(
            tint, shaftEnd,
            Offset(
                shaftEnd.x - (headLen * cos(arrowAngle + spread)).toFloat(),
                shaftEnd.y - (headLen * sin(arrowAngle + spread)).toFloat()
            ),
            strokeWidth = arrowStroke, cap = capStyle
        )

        /* --- 3 concentric wave arcs: right half, centered vertically --- */
        /* Arcs face right (~-45° to +45° range), concentric from a point near arrow tip. */
        val arcOrigin = Offset(w * 0.48f, w * 0.50f)
        for (i in 1..3) {
            val r = w * 0.11f + (i * w * 0.10f)
            drawArc(
                color = tint,
                startAngle = -40f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = Offset(arcOrigin.x - r, arcOrigin.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = arcStroke, cap = capStyle)
            )
        }
    }
}

/**
 * Receive: bold diagonal arrow ↙ with 3 concentric wave arcs arriving from the right.
 * Mirror of Transmit — arrow points down-left (incoming), waves on the right.
 *
 * Layout within canvas (matches concept reference):
 *   [arrow ↙]  [))) waves]
 *
 * @param tint Override color; defaults to onSurface for theme-awareness.
 */
@Composable
fun IconReceive(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 24.dp,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(size)) {
        val w = size.toPx()
        val arrowStroke = w * 0.09f
        val arcStroke = w * 0.065f
        val capStyle = StrokeCap.Round

        /* --- Arrow shaft: upper-left → bottom-left (≈225° = down-left incoming) --- */
        val shaftStart = Offset(w * 0.42f, w * 0.22f)
        val shaftEnd = Offset(w * 0.05f, w * 0.75f)
        drawLine(tint, shaftStart, shaftEnd, strokeWidth = arrowStroke, cap = capStyle)

        /* --- Arrowhead: two chevron strokes at the tip (bottom-left) --- */
        val headLen = w * 0.20f
        val dx = shaftEnd.x - shaftStart.x
        val dy = shaftEnd.y - shaftStart.y
        val arrowAngle = kotlin.math.atan2(dy.toDouble(), dx.toDouble())
        val spread = Math.toRadians(38.0)
        drawLine(
            tint, shaftEnd,
            Offset(
                shaftEnd.x - (headLen * cos(arrowAngle - spread)).toFloat(),
                shaftEnd.y - (headLen * sin(arrowAngle - spread)).toFloat()
            ),
            strokeWidth = arrowStroke, cap = capStyle
        )
        drawLine(
            tint, shaftEnd,
            Offset(
                shaftEnd.x - (headLen * cos(arrowAngle + spread)).toFloat(),
                shaftEnd.y - (headLen * sin(arrowAngle + spread)).toFloat()
            ),
            strokeWidth = arrowStroke, cap = capStyle
        )

        /* --- 3 concentric wave arcs: right half, same position as Transmit for visual pairing --- */
        val arcOrigin = Offset(w * 0.48f, w * 0.50f)
        for (i in 1..3) {
            val r = w * 0.11f + (i * w * 0.10f)
            drawArc(
                color = tint,
                startAngle = -40f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = Offset(arcOrigin.x - r, arcOrigin.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = arcStroke, cap = capStyle)
            )
        }
    }
}

/** Split diagram: 1 → 3 (Shamir). */
@Composable
fun IconShamir(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 20.dp
) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.size(size)) {
        val w = size.toPx()
        val h = size.toPx()
        val strokeW = 1.2f
        // Left: single circle (source)
        drawCircle(
            color = color,
            radius = w * 0.15f,
            center = Offset(w * 0.25f, h / 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
        )
        // Right: three small circles (shares)
        listOf(0.65f, 0.8f, 0.95f).forEach { frac ->
            drawCircle(
                color = color,
                radius = w * 0.1f,
                center = Offset(w * frac, h / 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
            )
        }
        // Connecting line
        drawLine(
            color = color,
            start = Offset(w * 0.4f, h / 2f),
            end = Offset(w * 0.55f, h / 2f),
            strokeWidth = strokeW
        )
    }
}

/** Shield/document: backup. */
@Composable
fun IconBackup(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 20.dp
) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.size(size)) {
        val w = size.toPx()
        val h = size.toPx()
        val strokeW = 1.2f
        // Simple shield shape: arc top, rectangle body
        val cx = w / 2f
        val top = h * 0.1f
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.15f, top),
            size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.4f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
        )
        drawLine(
            color = color,
            start = Offset(w * 0.2f, top + h * 0.2f),
            end = Offset(w * 0.2f, h * 0.85f),
            strokeWidth = strokeW
        )
        drawLine(
            color = color,
            start = Offset(w * 0.8f, top + h * 0.2f),
            end = Offset(w * 0.8f, h * 0.85f),
            strokeWidth = strokeW
        )
        drawLine(
            color = color,
            start = Offset(w * 0.2f, h * 0.85f),
            end = Offset(w * 0.8f, h * 0.85f),
            strokeWidth = strokeW
        )
    }
}
