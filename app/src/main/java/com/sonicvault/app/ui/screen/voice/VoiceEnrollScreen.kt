package com.sonicvault.app.ui.screen.voice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonicvault.app.SonicVaultApplication
import com.sonicvault.app.ui.component.ConfirmationDialog
import com.sonicvault.app.ui.component.StatusBar
import com.sonicvault.app.ui.component.MinimalAmplitudeVisualizer
import com.sonicvault.app.ui.theme.Spacing

/**
 * Voice biometric enrollment + verification test.
 * Concept: WhisperX/pyannote speaker embedding — unique characteristics of your voice.
 * Rams: honest (clear feedback), useful (test confirms it works), understandable (simple flow).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceEnrollScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as SonicVaultApplication
    /* Cache factory in remember to avoid recomposition-triggered recreation (frame jank mitigation) */
    val factory = remember(app) {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return VoiceEnrollViewModel(app.voiceBiometricAuth) as T
            }
        }
    }
    val viewModel: VoiceEnrollViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val amplitudeHistory by viewModel.amplitudeHistory.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        ConfirmationDialog(
            title = "Clear voice enrollment?",
            message = "You will need to re-enroll to use voice unlock.",
            confirmLabel = "Clear",
            dismissLabel = "Cancel",
            onConfirm = {
                viewModel.clearEnrollment()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VOICE UNLOCK", style = MaterialTheme.typography.titleMedium) },
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
            Icon(
                Icons.Default.Mic,
                contentDescription = "Voice enrollment",
                modifier = Modifier.padding(Spacing.lg.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Voice biometric",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Spacing.sm.dp))
            Text(
                text = "Your voice creates a unique voiceprint stored on-device only. " +
                        "Record 4 seconds in a quiet place — say a phrase or hum. " +
                        "Use the same conditions when verifying.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            Text(
                text = "Voiceprint never leaves your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.lg.dp))
            when (val s = state) {
                is VoiceEnrollState.Idle -> {
                    Button(
                        onClick = { viewModel.enroll() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (viewModel.hasEnrolledVoice()) "RE-ENROLL VOICE (4 s)" else "ENROLL VOICE (4 s)")
                    }
                    if (viewModel.hasEnrolledVoice()) {
                        Spacer(modifier = Modifier.height(Spacing.sm.dp))
                        /* Test: records 3s, compares to stored voiceprint. Rams: honest feedback. */
                        OutlinedButton(
                            onClick = { viewModel.testVoice() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("TEST VOICE (3 s)")
                        }
                        Spacer(modifier = Modifier.height(Spacing.sm.dp))
                        OutlinedButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("CLEAR ENROLLMENT")
                        }
                    }
                }
                is VoiceEnrollState.Recording -> {
                    Text(
                        "Recording… (4 s)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    /** Amplitude visualizer: real-time mic amplitude during voice enrollment. */
                    MinimalAmplitudeVisualizer(
                        amplitudeHistory = amplitudeHistory,
                        height = 80.dp,
                        barColor = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Text(
                        "Speak clearly…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is VoiceEnrollState.LivenessChallenge -> {
                    /** Liveness challenge: show random BIP39 word user must speak. */
                    Text(
                        text = "SAY THIS WORD:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Text(
                        text = "\"${s.word}\"",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Text(
                        text = "Speak clearly when ready. This proves you are live (not a replay).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Button(
                        onClick = { viewModel.verifyAfterChallenge() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("REC (3 s)")
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CANCEL")
                    }
                }
                is VoiceEnrollState.Testing -> {
                    Text(
                        "Verifying… (3 s)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    /** Amplitude visualizer: real-time mic amplitude during verification. */
                    MinimalAmplitudeVisualizer(
                        amplitudeHistory = amplitudeHistory,
                        height = 80.dp,
                        barColor = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Text(
                        "Listening…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is VoiceEnrollState.Success -> {
                    Text(
                        text = "Voice enrolled successfully.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Text(
                        text = "Use \"Test Voice\" to verify it recognises you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    OutlinedButton(
                        onClick = { viewModel.testVoice() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("TEST VOICE (3 s)")
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Button(onClick = { viewModel.reset(); onBack() }, modifier = Modifier.fillMaxWidth()) {
                        Text("DONE")
                    }
                }
                is VoiceEnrollState.TestPassed -> {
                    Text(
                        text = "Voice matched.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Text(
                        text = "Your voiceprint was recognised. Voice unlock is ready.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Button(onClick = { viewModel.reset(); onBack() }, modifier = Modifier.fillMaxWidth()) {
                        Text("DONE")
                    }
                }
                is VoiceEnrollState.TestFailed -> {
                    Text(
                        text = "Voice did not match.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Text(
                        text = "Try again in a quiet room using the same phrase. If it keeps failing, re-enroll.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    OutlinedButton(
                        onClick = { viewModel.testVoice() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("TEST AGAIN (3 s)")
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Button(onClick = { viewModel.enroll() }, modifier = Modifier.fillMaxWidth()) {
                        Text("RE-ENROLL VOICE (4 s)")
                    }
                }
                is VoiceEnrollState.Error -> {
                    StatusBar(status = s.message)
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
                        Text("TRY AGAIN")
                    }
                }
            }
        }
    }
}
