package com.sonicvault.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing

/**
 * Status bar with optional determinate progress (0f–1f).
 * Indeterminate when progress is null.
 * @param isActive When true, adds subtle 2dp primary accent line at bottom.
 */
@Composable
fun StatusBarWithProgress(
    status: String,
    progress: Float? = null,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val progressDesc = if (progress != null) "Status: $status, progress ${(progress * 100).toInt()}%" else "Status: $status"
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = progressDesc }
            .then(
                if (isActive) Modifier.drawBehind {
                    val lineHeight = 2.dp.toPx()
                    drawRect(
                        color = accentColor,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - lineHeight),
                        size = androidx.compose.ui.geometry.Size(size.width, lineHeight)
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(Spacing.sm.dp)) {
            Text(
                text = status.uppercase(),
                style = LabelUppercaseStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
            }
        }
    }
}
