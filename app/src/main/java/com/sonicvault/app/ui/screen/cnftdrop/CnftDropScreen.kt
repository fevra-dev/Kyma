package com.sonicvault.app.ui.screen.cnftdrop

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonicvault.app.ui.component.ConnectionState
import com.sonicvault.app.ui.component.SoundHandshakeIndicator
import com.sonicvault.app.ui.component.StatusBar
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing
import com.sonicvault.app.ui.theme.TouchTargetMin
import com.sonicvault.app.MainActivity

/**
 * cNFT Acoustic Airdrop screen: listen for event_id, claim mintToCollectionV1.
 *
 * Transmitter mode: broadcast event_id for others to claim.
 * Receiver mode: listen, then claim cNFT to wallet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CnftDropScreen(
    onBack: () -> Unit,
    viewModel: CnftDropViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var broadcastEventId by remember { mutableLongStateOf(System.currentTimeMillis() and 0x7FFFFFFFFFFFFFFFL) }

    val requestRecordAudio = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startListening(context.applicationContext)
    }

    LaunchedEffect(Unit) {
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> viewModel.startListening(context.applicationContext)
            else -> requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopListening() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("cNFT DROP", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .sizeIn(minWidth = TouchTargetMin, minHeight = TouchTargetMin)
                            .padding(Spacing.xs.dp)
                    ) {
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
            verticalArrangement = Arrangement.Center
        ) {
            when (val s = state) {
                is CnftDropViewModel.State.Idle,
                is CnftDropViewModel.State.Listening -> {
                    StatusBar(
                        status = "LISTENING…",
                        isActive = true,
                        isError = false
                    )
                    Spacer(modifier = Modifier.height(Spacing.lg.dp))
                    SoundHandshakeIndicator(connectionState = ConnectionState.LISTENING)
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Text(
                        text = "Hold device near transmitter",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xl.dp))
                    Text(
                        text = "BROADCAST (transmitter)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    OutlinedButton(
                        onClick = {
                            broadcastEventId = System.currentTimeMillis() and 0x7FFFFFFFFFFFFFFFL
                            viewModel.broadcastDrop(context.applicationContext, broadcastEventId)
                        },
                        shape = RectangleShape
                    ) {
                        Text("BROADCAST DROP")
                    }
                }
                is CnftDropViewModel.State.Received -> {
                    StatusBar(
                        status = "DROP RECEIVED",
                        isActive = true,
                        isError = false
                    )
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Text(
                        text = "Event: ${s.eventId}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.lg.dp))
                    Button(
                        onClick = {
                            (context as? MainActivity)?.activityResultSender?.let {
                                viewModel.claimDrop(it)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = RectangleShape
                    ) {
                        Text("CLAIM cNFT", style = LabelUppercaseStyle)
                    }
                }
                is CnftDropViewModel.State.Minting -> {
                    StatusBar(status = "MINTING…", isActive = true, isError = false)
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Text(
                        text = "Sign with wallet…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is CnftDropViewModel.State.Success -> {
                    LaunchedEffect(s) {
                        (context as? android.view.View)?.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM)
                    }
                    StatusBar(status = "MINTED", isActive = true, isError = false)
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Text(
                        text = "cNFT minted",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Sig: ${s.signature.take(24)}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(s.explorerUrl)))
                        },
                        shape = RectangleShape
                    ) {
                        Text("VIEW IN EXPLORER")
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    OutlinedButton(onClick = { viewModel.reset(); viewModel.startListening(context.applicationContext) }, shape = RectangleShape) {
                        Text("LISTEN AGAIN")
                    }
                }
                is CnftDropViewModel.State.Error -> {
                    LaunchedEffect(s) {
                        (context as? android.view.View)?.performHapticFeedback(HapticFeedbackConstantsCompat.REJECT)
                    }
                    StatusBar(status = "ERROR", isActive = false, isError = true)
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    OutlinedButton(onClick = { viewModel.reset(); viewModel.startListening(context.applicationContext) }, shape = RectangleShape) {
                        Text("TRY AGAIN")
                    }
                }
            }
        }
    }
}
