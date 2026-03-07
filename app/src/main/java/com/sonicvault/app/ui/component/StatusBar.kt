package com.sonicvault.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing

/**
 * Bordered status display: Ready, Encoding, Complete, Error.
 * Uppercase, muted, centered. Rams: honest, understandable.
 * @param isActive When true, adds subtle 2dp primary accent line at bottom.
 * @param shimmer When true, applies metallic shimmer animation to text (e.g. during Broadcasting).
 */
@Composable
fun StatusBar(
    status: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isError: Boolean = false,
    shimmer: Boolean = false
) {
    // Log only when status/state changes; avoids spam during shimmer animation recomposition
    LaunchedEffect(status, isActive, isError, shimmer) {
        SonicVaultLogger.d("[StatusBar] status=$status isActive=$isActive isError=$isError shimmer=$shimmer")
    }
    val accentColor = MaterialTheme.colorScheme.primary
    val textColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    /* Black shadow sweep: dark band moves through text during Broadcasting (opposite of light shimmer). */
    val shimmerProgress by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_progress"
    )
    val baseColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val shimmerBrush = if (shimmer) {
        Brush.linearGradient(
            colors = listOf(
                baseColor,
                baseColor,
                Color.Black.copy(alpha = 0.85f),
                baseColor,
                baseColor
            ),
            start = Offset(shimmerProgress * 1200f - 400f, 0f),
            end = Offset(shimmerProgress * 1200f - 200f, 0f)
        )
    } else null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Status: $status" }
            .then(
                if (isActive) Modifier.drawBehind {
                    val lineHeight = 2.dp.toPx()
                    drawRect(
                        color = accentColor,
                        topLeft = Offset(0f, size.height - lineHeight),
                        size = androidx.compose.ui.geometry.Size(size.width, lineHeight)
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = status.uppercase(),
            style = LabelUppercaseStyle.copy(
                brush = shimmerBrush
            ),
            color = if (shimmerBrush != null) Color.Unspecified else textColor,
            modifier = Modifier
                .padding(Spacing.sm.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}
