package com.sonicvault.app.ui.screen.presence

import android.Manifest
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonicvault.app.data.solana.AcousticCertificate
import com.sonicvault.app.ui.component.SoundHandshakeIndicator
import com.sonicvault.app.ui.component.ConnectionState
import com.sonicvault.app.ui.component.StatusBar
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing
import com.sonicvault.app.ui.theme.TouchTargetMin
import com.sonicvault.app.MainActivity

/**
 * Acoustic Presence Oracle screen: listen for 40B event payload, assemble 208-byte dual-sign certificate.
 *
 * Receiver: listen → assemble certificate (event_sig from demo key, claimer_sig via MWA).
 * Transmitter: broadcast 40B for testing (optional).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresenceOracleScreen(
    onBack: () -> Unit,
    viewModel: PresenceOracleViewModel = viewModel()
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
                title = { Text("PRESENCE ORACLE", style = MaterialTheme.typography.titleMedium) },
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
                .padding(Spacing.md.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val s = state) {
                is PresenceOracleViewModel.State.Idle,
                is PresenceOracleViewModel.State.Listening -> {
                    StatusBar(
                        status = "LISTENING…",
                        isActive = true,
                        isError = false
                    )
                    Spacer(modifier = Modifier.height(Spacing.lg.dp))
                    SoundHandshakeIndicator(connectionState = ConnectionState.LISTENING)
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Text(
                        text = "Hold device near event transmitter",
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
                            val demoPubkey = ByteArray(32) { it.toByte() }
                            viewModel.broadcastPresenceEvent(context.applicationContext, broadcastEventId, demoPubkey)
                        }
                    ) {
                        Text("BROADCAST EVENT")
                    }
                }
                is PresenceOracleViewModel.State.Received -> {
                    StatusBar(
                        status = "EVENT RECEIVED",
                        isActive = true,
                        isError = false
                    )
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Text(
                        text = "Event: ${s.eventId}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Event pubkey: ${s.eventPubkeyHex}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.lg.dp))
                    Button(
                        onClick = {
                            (context as? MainActivity)?.activityResultSender?.let {
                                viewModel.assembleCertificate(it)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ASSEMBLE CERTIFICATE", style = LabelUppercaseStyle)
                    }
                }
                is PresenceOracleViewModel.State.Assembling -> {
                    StatusBar(status = "ASSEMBLING…", isActive = true, isError = false)
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Text(
                        text = "Sign with wallet…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is PresenceOracleViewModel.State.CertReady -> {
                    LaunchedEffect(s) {
                        (context as? android.view.View)?.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM)
                    }
                    StatusBar(status = "CERTIFICATE READY", isActive = true, isError = false)
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    CertificateCard(cert = s.cert)
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    OutlinedButton(onClick = { viewModel.reset(); viewModel.startListening(context.applicationContext) }) {
                        Text("LISTEN AGAIN")
                    }
                }
                is PresenceOracleViewModel.State.Error -> {
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
                    OutlinedButton(onClick = { viewModel.reset(); viewModel.startListening(context.applicationContext) }) {
                        Text("TRY AGAIN")
                    }
                }
            }
        }
    }
}

@Composable
private fun CertificateCard(cert: AcousticCertificate) {
    val display = cert.toDisplayMap()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)
        ) {
            Text(
                text = "Dual-signature certificate (208 bytes)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            display.forEach { (key, value) ->
                Text(
                    text = "$key: $value",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
