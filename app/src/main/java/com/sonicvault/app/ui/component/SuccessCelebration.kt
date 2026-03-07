package com.sonicvault.app.ui.component

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Multimodal success celebration: haptic + particle burst + ascending chirp.
 *
 * Triggers on successful backup/transmit/receive. Provides three channels
 * of confirmation simultaneously:
 * - Haptic: CONFIRM pattern (API 33+) or double-pulse fallback
 * - Visual: particle burst animation (gold/green particles from center)
 * - Audio: C5->E5 ascending chirp (160ms, synthesized inline)
 */

/**
 * Particle data for the burst animation.
 */
private data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val size: Float,
    val alpha: Float
)

/**
 * Composable success celebration overlay with particle burst.
 *
 * @param trigger set to true to fire the celebration; resets automatically
 */
@Composable
fun SuccessCelebration(
    trigger: Boolean,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var particles by remember { mutableStateOf(emptyList<Particle>()) }
    var animProgress by remember { mutableStateOf(0f) }
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(trigger) {
        if (!trigger) return@LaunchedEffect

        isVisible = true

        // Haptic feedback
        triggerHaptic(view)

        // Generate particles
        val colors = listOf(
            Color(0xFFFFD700), // gold
            Color(0xFF4CAF50), // green
            Color(0xFFFFA726), // amber
            Color(0xFF66BB6A), // light green
            Color(0xFFFFEE58)  // yellow
        )

        particles = List(30) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = 200f + Random.nextFloat() * 300f
            Particle(
                x = 0.5f,
                y = 0.5f,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                color = colors.random(),
                size = 3f + Random.nextFloat() * 5f,
                alpha = 0.8f + Random.nextFloat() * 0.2f
            )
        }

        // Ascending chirp (C5 -> E5)
        launch {
            withContext(Dispatchers.IO) {
                playConfirmationChirp()
            }
        }

        // Animate particles over 800ms
        val steps = 40
        for (i in 0..steps) {
            animProgress = i.toFloat() / steps
            delay(20)
        }

        delay(200)
        isVisible = false
        particles = emptyList()
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(100)),
        exit = fadeOut(tween(300))
    ) {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            val w = size.width
            val h = size.height

            particles.forEach { p ->
                val elapsed = animProgress
                val x = (p.x * w) + p.vx * elapsed * 0.002f
                val y = (p.y * h) + p.vy * elapsed * 0.002f + 100f * elapsed * elapsed
                val alpha = (p.alpha * (1f - elapsed)).coerceAtLeast(0f)
                val radius = p.size * (1f - elapsed * 0.5f)

                if (alpha > 0f && x in 0f..w && y in 0f..h) {
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = radius,
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}

/**
 * Triggers haptic confirmation pattern.
 * Uses CONFIRM on API 33+, falls back to double-pulse.
 */
private fun triggerHaptic(view: View) {
    try {
        if (Build.VERSION.SDK_INT >= 34) {
            view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstantsCompat.LONG_PRESS)
        }
    } catch (e: Exception) {
        SonicVaultLogger.d("[SuccessCelebration] haptic failed: ${e.message}")
    }
}

/**
 * Synthesizes and plays a C5->E5 ascending chirp (160ms).
 * Pure sine wave, no external dependencies.
 */
private fun playConfirmationChirp() {
    try {
        val sampleRate = 22050
        val durationMs = 160
        val numSamples = sampleRate * durationMs / 1000

        // C5 = 523.25 Hz, E5 = 659.25 Hz
        val freqStart = 523.25
        val freqEnd = 659.25

        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / numSamples
            val freq = freqStart + (freqEnd - freqStart) * progress
            // Fade envelope: attack 10ms, sustain, decay 20ms
            val envelope = when {
                i < sampleRate * 0.01 -> i / (sampleRate * 0.01)
                i > numSamples - sampleRate * 0.02 -> (numSamples - i) / (sampleRate * 0.02)
                else -> 1.0
            }
            val sample = (sin(2.0 * PI * freq * t) * 0.4 * envelope * Short.MAX_VALUE).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val bufSize = samples.size * 2
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(samples, 0, samples.size)
        audioTrack.play()
        Thread.sleep(durationMs.toLong() + 50)
        audioTrack.release()

        SonicVaultLogger.d("[SuccessCelebration] chirp played")
    } catch (e: Exception) {
        SonicVaultLogger.w("[SuccessCelebration] chirp failed: ${e.message}")
    }
}
