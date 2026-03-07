package com.sonicvault.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sonicvault.app.domain.model.Protocol
import kotlin.math.PI
import kotlin.math.sin

/**
 * 4-band frequency spectrum with color-coded regions and data highlight.
 *
 * Frequency bands (mapped to 32 bars @ 48kHz Nyquist = 24kHz):
 * - Audible (0-8 kHz):     bars 0-10,  color = onSurfaceVariant (subtle)
 * - Transition (8-11 kHz): bars 11-14, color = tertiary
 * - Art Zone (11-18 kHz):  bars 15-23, color = secondary
 * - Data (18-22 kHz):      bars 24-29, color = RED (highlighted during transmission)
 * - Inaudible (22-24 kHz): bars 30-31, color = dim
 *
 * The red 18-20kHz highlight is the key "wow" visual during ggwave ultrasonic transmission.
 */

/** Band definition for frequency-mapped spectrogram coloring. */
private enum class FrequencyBand(val label: String, val startBin: Int, val endBin: Int) {
    AUDIBLE("Audible", 0, 11),
    TRANSITION("Transition", 11, 15),
    ART_ZONE("Art Zone", 15, 24),
    DATA("Data (18-22kHz)", 24, 30),
    INAUDIBLE("Inaudible", 30, 32)
}

@Composable
fun SpectrogramView(
    protocol: Protocol,
    modifier: Modifier = Modifier,
    isTransmitting: Boolean = false,
    height: Dp = 64.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spectrogram")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val barCount = 32
    val primaryColor = MaterialTheme.colorScheme.primary
    val subtleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val dataColor = Color(0xFFE53935) // vivid red for data band

    /* Protocol-aware bin weighting: ultrasonic = high bins; audible = full spectrum */
    val (activeLow, activeHigh) = when (protocol) {
        Protocol.ULTRASONIC -> 20 to 30
        Protocol.AUDIBLE -> 0 to 20
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val barWidth = size.width / barCount
        for (i in 0 until barCount) {
            val inActiveRange = i in activeLow until activeHigh

            /* Determine which band this bar belongs to */
            val band = FrequencyBand.entries.firstOrNull { i in it.startBin until it.endBin }
                ?: FrequencyBand.INAUDIBLE

            val bandColor = when (band) {
                FrequencyBand.AUDIBLE -> subtleColor
                FrequencyBand.TRANSITION -> tertiaryColor
                FrequencyBand.ART_ZONE -> secondaryColor
                FrequencyBand.DATA -> if (isTransmitting) dataColor else primaryColor
                FrequencyBand.INAUDIBLE -> subtleColor
            }

            /* Height calculation: active bars animate prominently, inactive are ambient noise floor */
            val baseHeight = if (inActiveRange) {
                0.3f + 0.7f * (0.5f + 0.5f * sin(2 * PI * (phase + i * 0.08f)).toFloat())
            } else if (band == FrequencyBand.DATA && isTransmitting) {
                /* Data band pulses strongly when transmitting even outside active range */
                0.5f + 0.5f * (0.5f + 0.5f * sin(2 * PI * (phase * 2 + i * 0.15f)).toFloat())
            } else {
                0.03f + 0.07f * (0.5f + 0.5f * sin(2 * PI * (phase + i * 0.1f)).toFloat())
            }

            val barHeight = size.height * baseHeight
            val left = i * barWidth
            val alpha = if (band == FrequencyBand.DATA && isTransmitting) {
                0.6f + 0.4f * baseHeight // brighter for data highlight
            } else {
                0.15f + 0.55f * baseHeight
            }

            drawRect(
                color = bandColor.copy(alpha = alpha),
                topLeft = Offset(left, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth * 0.85f, barHeight)
            )
        }
    }
}

/**
 * Enhanced spectrogram with band labels for demo/debugging.
 */
@Composable
fun SpectrogramViewWithLabels(
    protocol: Protocol,
    isTransmitting: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SpectrogramView(
            protocol = protocol,
            isTransmitting = isTransmitting,
            height = 80.dp
        )
        /* Band legend — compact, below spectrogram */
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
        ) {
            BandLabel("0-8k", MaterialTheme.colorScheme.onSurfaceVariant)
            BandLabel("8-11k", MaterialTheme.colorScheme.tertiary)
            BandLabel("11-18k", MaterialTheme.colorScheme.secondary)
            BandLabel("18-22k", if (isTransmitting) Color(0xFFE53935) else MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun BandLabel(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}
