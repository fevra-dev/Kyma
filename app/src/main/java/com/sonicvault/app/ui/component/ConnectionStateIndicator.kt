package com.sonicvault.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 7-state connection indicator for the sound handshake UX.
 *
 * States follow the acoustic data transfer lifecycle:
 * IDLE -> INITIALISING -> BROADCASTING/LISTENING -> WAITING_FOR_ACK -> COMPLETE / FAILED / TIMEOUT
 *
 * Visual: colored dot + label. Color transitions smoothly between states.
 */
enum class ConnectionState(val label: String) {
    IDLE(""),
    INITIALISING("Initializing…"),
    BROADCASTING("Broadcasting…"),
    LISTENING("Listening…"),
    WAITING_FOR_ACK("Waiting for confirmation…"),
    COMPLETE("Transfer complete"),
    FAILED("Transfer failed"),
    TIMEOUT("Timed out")
}

@Composable
fun ConnectionStateIndicator(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val dotColor by animateColorAsState(
        targetValue = when (state) {
            ConnectionState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
            ConnectionState.INITIALISING -> MaterialTheme.colorScheme.tertiary
            ConnectionState.BROADCASTING -> MaterialTheme.colorScheme.primary
            ConnectionState.LISTENING -> MaterialTheme.colorScheme.secondary
            ConnectionState.WAITING_FOR_ACK -> MaterialTheme.colorScheme.tertiary
            ConnectionState.COMPLETE -> Color(0xFF4CAF50) // success green
            ConnectionState.FAILED -> MaterialTheme.colorScheme.error
            ConnectionState.TIMEOUT -> MaterialTheme.colorScheme.error
        },
        animationSpec = tween(300),
        label = "dot_color"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        /* Dot hidden when IDLE (Ready) per design; shown for all other states */
        if (state != ConnectionState.IDLE) {
            Surface(
                modifier = Modifier.size(10.dp),
                shape = CircleShape,
                color = dotColor
            ) {}
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = state.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Vertical layout with radiating rings + connection state for full handshake UX.
 * Rams: animation is the forefront of sound transfer — prominent, centered.
 * During BROADCASTING: hide ConnectionStateIndicator (dot + label); StatusBar shows "Broadcasting…" with shadow.
 */
/**
 * Vertical layout with radiating rings + connection state for full handshake UX.
 * Hidden in IDLE — rings only appear as feedback during active transfer (Rams #5: unobtrusive).
 * During BROADCASTING: hide ConnectionStateIndicator label; StatusBar shows "Broadcasting…" instead.
 */
@Composable
fun SoundHandshakeIndicator(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    /* Rams #10: show nothing in IDLE — rings are feedback, not decoration. */
    if (connectionState == ConnectionState.IDLE) return

    val isActive = connectionState == ConnectionState.BROADCASTING ||
            connectionState == ConnectionState.LISTENING ||
            connectionState == ConnectionState.INITIALISING

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RadiatingRingsAnimation(
            isActive = isActive,
            size = 180.dp
        )
    }
}
