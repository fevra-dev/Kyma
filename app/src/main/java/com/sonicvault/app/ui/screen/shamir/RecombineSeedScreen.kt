package com.sonicvault.app.ui.screen.shamir

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonicvault.app.SonicVaultApplication
import kotlinx.coroutines.launch
import com.sonicvault.app.ui.component.ConfirmationDialog
import com.sonicvault.app.ui.component.StatusBar
import com.sonicvault.app.ui.screen.recovery.CLIPBOARD_CLEAR_DELAY_SECONDS
import com.sonicvault.app.ui.screen.recovery.ShowSeedStep
import com.sonicvault.app.ui.screen.recovery.copyToClipboard
import com.sonicvault.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecombineSeedScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as SonicVaultApplication
    /* Cache factory in remember to avoid recomposition-triggered recreation (frame jank mitigation) */
    val factory = remember(app) {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return RecombineSeedViewModel(app.recombineSeedUseCase) as T
            }
        }
    }
    val viewModel: RecombineSeedViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val selectedUris by viewModel.selectedUris.collectAsState()
    val context = LocalContext.current

    var hasReturnedFromPicker by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addUris(uris)
        } else {
            hasReturnedFromPicker = true
        }
    }

    /* Auto-open file picker on mount; avoid brief flash of "SELECT SHARE FILES" */
    LaunchedEffect(Unit) {
        if (state is RecombineSeedState.Idle && selectedUris.isEmpty() && !hasReturnedFromPicker) {
            picker.launch("audio/*")
        }
    }

    val showPickerPlaceholder = state is RecombineSeedState.Idle && selectedUris.isEmpty() && !hasReturnedFromPicker

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showClearUrisConfirm by remember { mutableStateOf(false) }

    if (showClearUrisConfirm) {
        ConfirmationDialog(
            title = "Remove all shares?",
            message = "You will need to add them again to recover.",
            confirmLabel = "Remove all",
            dismissLabel = "Cancel",
            onConfirm = {
                viewModel.clearUris()
                showClearUrisConfirm = false
            },
            onDismiss = { showClearUrisConfirm = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("RECOVER SHARES", style = MaterialTheme.typography.titleMedium) },
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
                .padding(Spacing.md.dp)
        ) {
            when {
                showPickerPlaceholder -> {
                    Box(modifier = Modifier.fillMaxSize()) {}
                }
                state is RecombineSeedState.Idle ||
                state is RecombineSeedState.Combining -> {
                    val inProgress = state is RecombineSeedState.Combining
                    if (inProgress) {
                        StatusBar(status = "Combining shares…", isActive = true)
                        Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    }
                    Text(
                        text = "Add your share files — you need at least the threshold amount.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Text(
                        text = "Selected: ${selectedUris.size} file(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    if (inProgress) {
                        CircularProgressIndicator(modifier = Modifier.padding(Spacing.sm.dp))
                    }
                    Button(
                        onClick = { picker.launch("audio/*") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !inProgress,
                        shape = RectangleShape
                    ) {
                        Text(if (selectedUris.isNotEmpty()) "ADD MORE FILES" else "SELECT SHARE FILES")
                    }
                    if (selectedUris.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.xs.dp))
                        OutlinedButton(
                            onClick = { showClearUrisConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !inProgress,
                            shape = RectangleShape
                        ) {
                            Text("CLEAR ALL")
                        }
                        Spacer(modifier = Modifier.height(Spacing.sm.dp))
                        if (context is androidx.fragment.app.FragmentActivity) {
                            Button(
                                onClick = { viewModel.startRecombine(context) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !inProgress,
                                shape = RectangleShape
                            ) {
                                Text(if (inProgress) "COMBINING…" else "RECOVER SEED")
                            }
                        }
                    }
                }
                state is RecombineSeedState.Success -> {
                    val s = state as RecombineSeedState.Success
                    val view = LocalView.current
                    LaunchedEffect(s.seedPhrase) { view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM) }
                    StatusBar(status = "Verified")
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    ShowSeedStep(
                        seedPhrase = s.seedPhrase,
                        onCopy = {
                            view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM)
                            copyToClipboard(context, "Kyma", s.seedPhrase)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Seed copied — auto-clears in ${CLIPBOARD_CLEAR_DELAY_SECONDS}s",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Button(onClick = { viewModel.reset(); onBack() }, modifier = Modifier.fillMaxWidth(), shape = RectangleShape) {
                        Text("DONE")
                    }
                }
                state is RecombineSeedState.Error -> {
                    val s = state as RecombineSeedState.Error
                    val view = LocalView.current
                    LaunchedEffect(s.message) { view.performHapticFeedback(HapticFeedbackConstantsCompat.REJECT) }
                    StatusBar(status = s.message)
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth(), shape = RectangleShape) {
                        Text("TRY AGAIN")
                    }
                }
            }
        }
    }
}
