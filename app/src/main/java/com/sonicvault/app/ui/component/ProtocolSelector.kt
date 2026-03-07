package com.sonicvault.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.sonicvault.app.domain.model.Protocol
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing

/**
 * Radio-style protocol selector: Audible or Ultrasonic.
 * Selected: border-black, bg-neutral. Rams: unobtrusive, honest.
 *
 * @param compact When true, reduces spacing and row height for fixed layouts (transmit via sound).
 * @param enabled When false, disables interaction (e.g. during transmission).
 */
@Composable
fun ProtocolSelector(
    selectedProtocol: Protocol,
    onProtocolChange: (Protocol) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    enabled: Boolean = true
) {
    SonicVaultLogger.d("[ProtocolSelector] selected=$selectedProtocol enabled=$enabled")
    CardSection(modifier = modifier) {
        Text(
            text = "PROTOCOL",
            style = LabelUppercaseStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(if (compact) Spacing.xs.dp else Spacing.sm.dp))
        Protocol.entries.forEach { protocol ->
            val isSelected = protocol == selectedProtocol
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (compact) 0.dp else Spacing.xs.dp / 2)
                    .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
                    .then(
                        if (enabled) Modifier.clickable(enabled = true) {
                            SonicVaultLogger.d("[ProtocolSelector] user selected=$protocol")
                            onProtocolChange(protocol)
                        } else Modifier
                    ),
                shape = RoundedCornerShape(0.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outline
                ),
                color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerLow
                else MaterialTheme.colorScheme.surface
            ) {
                /* Minimum 44dp touch target (Material); Rams: thorough */
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .padding(if (compact) Spacing.sm.dp else Spacing.md.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = protocol.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = protocol.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // Radio indicator: outer ring, inner dot when selected
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            border = BorderStroke(
                                width = 2.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.outline
                            ),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                                if (isSelected) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ) {
                                        Box(modifier = Modifier.size(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
