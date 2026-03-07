package com.sonicvault.app.ui.screen.recovery

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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.sonicvault.app.data.recovery.RestoreVerifier
import com.sonicvault.app.ui.component.MnemonicTeleprompter
import com.sonicvault.app.ui.theme.Spacing
import com.sonicvault.app.ui.theme.TouchTargetMin
import com.sonicvault.app.util.createSeedVaultImportDeeplink

/**
 * Acoustic vault restore: listen for chunked backup, decrypt, show teleprompter.
 *
 * User types each word into Seed Vault Import Existing. Rams: useful, understandable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreCeremonyScreen(
    viewModel: RestoreCeremonyViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? FragmentActivity

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

    LaunchedEffect(state) {
        if (state is RestoreCeremonyViewModel.State.ReceivedPayload && activity != null) {
            val s = state as RestoreCeremonyViewModel.State.ReceivedPayload
            viewModel.processPayload(s.bytes, activity)
        }
    }

    val seedVaultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ACOUSTIC RESTORE", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.sizeIn(minWidth = TouchTargetMin, minHeight = TouchTargetMin)
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
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)
        ) {
            when (val s = state) {
                is RestoreCeremonyViewModel.State.Idle,
                is RestoreCeremonyViewModel.State.Listening -> {
                    Text(
                        text = "Listening for acoustic backup…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is RestoreCeremonyViewModel.State.ReceivedPayload,
                is RestoreCeremonyViewModel.State.Decrypting -> {
                    Text(
                        text = "Decrypting…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                is RestoreCeremonyViewModel.State.Teleprompter -> {
                    MnemonicTeleprompter(
                        words = s.words,
                        currentIndex = s.currentIndex,
                        onNext = { viewModel.nextWord() },
                        onDone = { viewModel.allDone() },
                        onBack = { viewModel.prevWord() }
                    )
                }
                is RestoreCeremonyViewModel.State.ShowDerivedAddress -> {
                    Text(
                        text = "Your derived address:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = RestoreVerifier.truncateAddress(s.derivedAddress),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Open Seed Vault to import. After import, your Seed Vault will show this address.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.proceedToSeedVault()
                            createSeedVaultImportDeeplink(context)?.let { intent ->
                                seedVaultLauncher.launch(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("OPEN SEED VAULT TO IMPORT")
                    }
                }
                is RestoreCeremonyViewModel.State.Done -> {
                    LaunchedEffect(s) { view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM) }
                    Text(
                        text = "Enter words into Seed Vault",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(onClick = { viewModel.restart() }) {
                        Text("RESTART")
                    }
                }
                is RestoreCeremonyViewModel.State.Error -> {
                    LaunchedEffect(s) { view.performHapticFeedback(HapticFeedbackConstantsCompat.REJECT) }
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(onClick = { viewModel.restart() }) {
                        Text("RESTART")
                    }
                }
            }
        }
    }
}
