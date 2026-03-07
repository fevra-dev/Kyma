package com.sonicvault.app.ui.screen.recovery

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonicvault.app.SonicVaultApplication
import com.sonicvault.app.ui.component.StatusBar
import com.sonicvault.app.ui.theme.Spacing

/**
 * Acoustic backup transmit: pick backup file → extract payload → play over speaker.
 *
 * New device runs RestoreCeremonyScreen (Listen) to receive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreBroadcastScreen(
    viewModel: RestoreBroadcastViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.pickAndTransmit(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TRANSMIT BACKUP", style = MaterialTheme.typography.titleMedium) },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)
        ) {
            when (val s = state) {
                is RestoreBroadcastViewModel.State.Idle -> {
                    StatusBar(status = "Select backup file")
                    Text(
                        text = "Pick a backup file (WAV or FLAC). It will be played over the speaker for the new device to receive.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Button(
                        onClick = { filePicker.launch("audio/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SELECT BACKUP FILE")
                    }
                }
                is RestoreBroadcastViewModel.State.Extracting -> {
                    StatusBar(status = "Extracting payload…", isActive = true)
                    CircularProgressIndicator(modifier = Modifier.padding(Spacing.sm.dp))
                }
                is RestoreBroadcastViewModel.State.Transmitting -> {
                    StatusBar(
                        status = if (s.total > 0) "Transmitting ${s.chunk}/${s.total}…" else "Transmitting…",
                        isActive = true
                    )
                    Text(
                        text = "Hold devices close. New device should be on Listen screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CircularProgressIndicator(modifier = Modifier.padding(Spacing.sm.dp))
                }
                is RestoreBroadcastViewModel.State.Done -> {
                    StatusBar(status = "Transmit complete")
                    Text(
                        text = "Backup transmitted. New device should have received it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    OutlinedButton(onClick = { viewModel.reset() }) {
                        Text("TRANSMIT AGAIN")
                    }
                }
                is RestoreBroadcastViewModel.State.Error -> {
                    StatusBar(status = s.message)
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Button(
                        onClick = { viewModel.reset(); filePicker.launch("audio/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("TRY AGAIN")
                    }
                }
            }
        }
    }
}
