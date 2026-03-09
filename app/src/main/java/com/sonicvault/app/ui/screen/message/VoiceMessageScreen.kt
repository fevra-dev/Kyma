package com.sonicvault.app.ui.screen.message

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import android.app.Application
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonicvault.app.ui.component.ProtocolSelector
import com.sonicvault.app.ui.component.StatusBar
import com.sonicvault.app.ui.component.WaveformView
import com.sonicvault.app.ui.theme.Spacing

/**
 * Record a voice message, encrypt it, and transmit via sound.
 * Voice recording is limited to 30s; the audio is compressed before encoding.
 * Note: Full voice transfer via ggwave is size-limited. This screen supports
 * short voice clips (~5s) or falls back to stego backup flow for longer clips.
 * Rams: honest — communicates limitations, useful — clear record/transmit flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceMessageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val factory = remember(context) {
        ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
    }
    val viewModel: MessageViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val protocol by viewModel.selectedProtocol.collectAsState()
    val amplitudeHistory by viewModel.amplitudeHistory.collectAsState()
    var isRecording by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VOICE MESSAGE", style = MaterialTheme.typography.titleMedium) },
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
                .padding(Spacing.md.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Record a short voice message to encrypt and transmit.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            Text(
                text = "Max 5 seconds for direct sound transmission. Longer recordings use the stego backup flow.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.lg.dp))

            /* Record button */
            OutlinedButton(
                onClick = { isRecording = !isRecording },
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.ui.graphics.RectangleShape
            ) {
                Text(if (isRecording) "STOP RECORDING" else "START RECORDING")
            }

            /* Waveform visualization during recording */
            if (amplitudeHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                WaveformView(amplitudeHistory = amplitudeHistory)
            }

            Spacer(modifier = Modifier.height(Spacing.sm.dp))

            /* Protocol selector — disabled during encoding/playing */
            ProtocolSelector(
                selectedProtocol = protocol,
                onProtocolChange = { viewModel.setProtocol(it) },
                compact = true,
                enabled = state !is MessageState.Encoding && state !is MessageState.Playing
            )

            Spacer(modifier = Modifier.height(Spacing.sm.dp))

            /* Status area */
            when (val s = state) {
                is MessageState.Error -> StatusBar(status = s.message)
                is MessageState.Success -> StatusBar(status = s.info)
                else -> { }
            }

            Spacer(modifier = Modifier.weight(1f))

            /* Transmit button */
            Button(
                onClick = {
                    if (state is MessageState.Success || state is MessageState.Error) {
                        viewModel.reset()
                    } else {
                        viewModel.transmitVoice()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state !is MessageState.Encoding && state !is MessageState.Playing,
                shape = androidx.compose.ui.graphics.RectangleShape
            ) {
                Text(
                    when (state) {
                        is MessageState.Success -> "DONE"
                        is MessageState.Error -> "TRY AGAIN"
                        else -> "TRANSMIT VOICE"
                    }
                )
            }
        }
    }
}
