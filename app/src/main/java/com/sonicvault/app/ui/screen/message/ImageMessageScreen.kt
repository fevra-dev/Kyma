package com.sonicvault.app.ui.screen.message

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import android.app.Application
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonicvault.app.ui.component.ProtocolSelector
import com.sonicvault.app.ui.component.StatusBar
import com.sonicvault.app.ui.theme.Spacing

/**
 * Pick an image, encrypt it, and transmit via sound steganography.
 * Note: ggwave payload is limited (~140 bytes), so full image transmission
 * requires the backup flow with cover audio. This screen is a placeholder
 * for future image compression + chunk transfer.
 * Rams: honest — clearly communicates current limitations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageMessageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val factory = remember(context) {
        ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
    }
    val viewModel: MessageViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val protocol by viewModel.selectedProtocol.collectAsState()
    val imageUri by viewModel.imageUri.collectAsState()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        viewModel.setImageUri(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IMAGE MESSAGE", style = MaterialTheme.typography.titleMedium) },
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
                text = "Select an image to encrypt and transmit via sound.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.sm.dp))
            Text(
                text = "Image data is limited by ggwave payload size (~140 bytes). Full image transfer uses the backup steganography flow.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.lg.dp))

            /* Image picker button */
            OutlinedButton(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.ui.graphics.RectangleShape
            ) {
                Text(if (imageUri != null) "Image selected ✓" else "PICK IMAGE")
            }

            if (imageUri != null) {
                Spacer(modifier = Modifier.height(Spacing.xs.dp))
                Text(
                    text = imageUri.toString().takeLast(40),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                        viewModel.transmitImage()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = imageUri != null && state !is MessageState.Encoding && state !is MessageState.Playing,
                shape = androidx.compose.ui.graphics.RectangleShape
            ) {
                Text(
                    when (state) {
                        is MessageState.Success -> "DONE"
                        is MessageState.Error -> "TRY AGAIN"
                        else -> "TRANSMIT IMAGE"
                    }
                )
            }
        }
    }
}
