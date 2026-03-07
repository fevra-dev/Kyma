package com.sonicvault.app.ui.screen.matryoshka

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonicvault.app.data.stego.MatryoshkaPipeline
import com.sonicvault.app.ui.component.StatusBar
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing

/**
 * Matryoshka steganography demo screen.
 *
 * Demonstrates the 3-layer nesting pipeline:
 * Audio -> Spectrogram Image -> LSB Stego -> Reconstructed Audio.
 *
 * In the full production flow this integrates with BackupRepository,
 * but this screen provides a standalone demo of the concept.
 */
private sealed class MatryoshkaState {
    data object Idle : MatryoshkaState()
    data class Encoding(val progress: String) : MatryoshkaState()
    data class Decoding(val progress: String) : MatryoshkaState()
    data class EncodeSuccess(val originalSize: Int, val outputSize: Int) : MatryoshkaState()
    data class DecodeSuccess(val payloadSize: Int) : MatryoshkaState()
    data class Error(val message: String) : MatryoshkaState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatryoshkaScreen(onBack: () -> Unit) {
    var state by remember { mutableStateOf<MatryoshkaState>(MatryoshkaState.Idle) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MATRYOSHKA STEGO", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.md.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(Spacing.md.dp))

            Text(
                text = "3-layer steganography pipeline",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            Text(
                text = "Data is hidden in the spectrogram image of audio, not in the audio signal itself. " +
                        "Even if audio stego is suspected, analysis of the audio waveform reveals nothing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.lg.dp))

            /* Pipeline diagram */
            Text("ENCODE PIPELINE", style = LabelUppercaseStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            PipelineStep("1", "Audio", "PCM cover audio input")
            PipelineStep("2", "STFT", "Audio -> Spectrogram image")
            PipelineStep("3", "LSB Embed", "Payload hidden in image pixels")
            PipelineStep("4", "Griffin-Lim", "Modified spectrogram -> Audio")
            PipelineStep("5", "Art Layer", "Brand logo in 11-18 kHz band")

            Spacer(modifier = Modifier.height(Spacing.md.dp))

            Text("DECODE PIPELINE", style = LabelUppercaseStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            PipelineStep("1", "STFT", "Audio -> Spectrogram image")
            PipelineStep("2", "LSB Extract", "Read payload from pixels")

            Spacer(modifier = Modifier.height(Spacing.lg.dp))

            /* Status area */
            when (val s = state) {
                is MatryoshkaState.Idle -> {
                    Text(
                        "This feature is integrated into the backup pipeline. " +
                                "When creating a backup, the Matryoshka option adds an extra layer of plausible deniability.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))

                    val demoSamples = 44100 * 5 // 5 seconds
                    val capacity = MatryoshkaPipeline.estimateCapacity(demoSamples)
                    Text(
                        "Estimated capacity for 5s audio: ~${capacity / 1024} KB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                is MatryoshkaState.Encoding -> StatusBar(status = s.progress)
                is MatryoshkaState.Decoding -> StatusBar(status = s.progress)
                is MatryoshkaState.EncodeSuccess -> {
                    StatusBar(status = "Encoded: ${s.originalSize} -> ${s.outputSize} samples")
                }
                is MatryoshkaState.DecodeSuccess -> {
                    StatusBar(status = "Decoded ${s.payloadSize} bytes from audio")
                }
                is MatryoshkaState.Error -> {
                    StatusBar(status = s.message, isError = true)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg.dp))

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("BACK")
            }

            Spacer(modifier = Modifier.height(Spacing.lg.dp))
        }
    }
}

/** Simple pipeline step display row. */
@Composable
private fun PipelineStep(number: String, title: String, description: String) {
    Column(modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
        Text(
            text = "$number. $title",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
