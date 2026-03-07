package com.sonicvault.app.ui.screen.recovery

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonicvault.app.SonicVaultApplication
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.sonicvault.app.domain.model.RecoveryState
import kotlinx.coroutines.launch
import com.sonicvault.app.ui.component.EmptyState
import com.sonicvault.app.ui.component.StatusBar
import com.sonicvault.app.ui.theme.Spacing
import com.sonicvault.app.util.createImportSeedIntent
import com.sonicvault.app.util.isSeedVaultAvailable

/**
 * Recovery flow: one primary action (select file), clear states. Rams: useful, understandable.
 *
 * @param initialUri When non-null (e.g. from Share Target), start recovery with this file instead of opening picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryScreen(
    onBack: () -> Unit,
    initialUri: Uri? = null,
    onAcousticRestore: () -> Unit = {},
    onRestoreBroadcast: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as SonicVaultApplication
    /* Cache factory in remember to avoid recomposition-triggered recreation (frame jank mitigation) */
    val factory = remember(app) {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return RecoveryViewModel(
                    app.voiceBiometricAuth,
                    app.bip39Validator,
                    app.recoverSeedUseCase
                ) as T
            }
        }
    }
    val viewModel: RecoveryViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var hasReturnedFromPicker by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && context is androidx.fragment.app.FragmentActivity) {
            viewModel.pickFile(uri, context)
        } else {
            hasReturnedFromPicker = true
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    /* Seed Vault import: launch ACTION_IMPORT_SEED; user enters seed in Seed Vault secure UI */
    val seedVaultImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Seed saved to Seed Vault.",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    /* Share Target: when launched with shared audio, process it immediately */
    LaunchedEffect(initialUri) {
        if (initialUri != null && context is androidx.fragment.app.FragmentActivity) {
            viewModel.pickFile(initialUri, context)
        }
    }

    /* Auto-open file picker on mount (when no initialUri); avoid brief flash of "SELECT FILE" */
    LaunchedEffect(Unit) {
        if (initialUri == null && state is RecoveryState.Idle && !hasReturnedFromPicker) {
            picker.launch("audio/*")
        }
    }

    /* When Idle and awaiting picker, show blank until picker appears (no upload UI flash) */
    val showPickerPlaceholder = state is RecoveryState.Idle && !hasReturnedFromPicker

    val view = LocalView.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("RECOVER BACKUP", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
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
        ) {
            when {
                showPickerPlaceholder -> {
                    /* Blank/minimal until picker shows; avoids "SELECT FILE" flash */
                    Box(modifier = Modifier.fillMaxSize()) {}
                }
                state is RecoveryState.Idle -> {
                    EmptyState(
                        title = "No backup selected.",
                        subtitle = "Select your backup file to recover."
                    )
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Button(
                        onClick = { picker.launch("audio/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SELECT FILE")
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    OutlinedButton(
                        onClick = onAcousticRestore,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("LISTEN FOR ACOUSTIC BACKUP")
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    OutlinedButton(
                        onClick = onRestoreBroadcast,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("TRANSMIT BACKUP")
                    }
                }
                state is RecoveryState.Reading ||
                state is RecoveryState.Extracting ||
                state is RecoveryState.Decrypting -> {
                    val decrypting = state is RecoveryState.Decrypting
                    if (decrypting) {
                        StatusBar(status = "Decrypting…", isActive = true)
                        Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    }
                    Text(
                        text = "Select your backup file (WAV or FLAC).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    if (decrypting) {
                        CircularProgressIndicator(modifier = Modifier.padding(Spacing.sm.dp))
                    }
                    Button(
                        onClick = { picker.launch("audio/*") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !decrypting
                    ) {
                        Text(if (decrypting) "DECRYPTING…" else "SELECT FILE")
                    }
                }
                state is RecoveryState.AwaitingVoiceVerification -> {
                    val s = state as RecoveryState.AwaitingVoiceVerification
                    StatusBar(status = "Verify your voice")
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Text(
                        text = "Speak the word below to verify your identity.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Text(
                        text = s.challengeWord,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Button(
                        onClick = {
                            if (context is androidx.fragment.app.FragmentActivity) {
                                viewModel.unlockWithVoiceVerification(s.extracted, s.challengeWord, context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("VERIFY VOICE")
                    }
                }
                state is RecoveryState.VerifyingVoice -> {
                    val s = state as RecoveryState.VerifyingVoice
                    StatusBar(status = "Recording… Speak the word", isActive = true)
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Text(
                        text = "Say \"${s.challengeWord}\"",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    CircularProgressIndicator(modifier = Modifier.padding(Spacing.sm.dp))
                }
                state is RecoveryState.AwaitingUnlock -> {
                    val s = state as RecoveryState.AwaitingUnlock
                    val extracted = s.extracted
                    val passwordOnly = s.passwordOnly
                    var password by remember { mutableStateOf("") }
                    var passwordVisible by remember { mutableStateOf(false) }
                    StatusBar(status = if (passwordOnly) "Enter password" else "Verify device & unlock")
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Text(
                        text = if (passwordOnly) "This backup was encrypted with a password. Enter it to recover. Device integrity is verified before decrypt." else "Unlock with fingerprint or password. Device integrity is verified before decrypt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { newValue -> password = newValue },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Button(
                        onClick = { viewModel.unlockWithPassword(extracted, password) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = password.isNotBlank()
                    ) {
                        Text("UNLOCK WITH PASSWORD")
                    }
                    if (!passwordOnly) {
                        Spacer(modifier = Modifier.height(Spacing.xs.dp))
                        OutlinedButton(
                            onClick = {
                                if (context is androidx.fragment.app.FragmentActivity) {
                                    viewModel.unlockWithBiometric(extracted, context)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("UNLOCK WITH FINGERPRINT")
                        }
                    }
                }
                state is RecoveryState.TimelockNotReached -> {
                    val s = state as RecoveryState.TimelockNotReached
                    val unlockDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(s.unlockTimestamp * 1000))
                    StatusBar(status = "Unlock date not reached")
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Text(
                        text = "This backup unlocks on $unlockDate. Please wait.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Button(onClick = { viewModel.reset(); onBack() }, modifier = Modifier.fillMaxWidth()) {
                        Text("DONE")
                    }
                }
                state is RecoveryState.ShowSeed -> {
                    val s = state as RecoveryState.ShowSeed
                    LaunchedEffect(s.seedPhrase) { view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM) }
                    StatusBar(status = "Verified")
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    s.checksumVerified?.let { cs ->
                        Text(
                            text = "Checksum verified: $cs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    }
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
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    /* Save to Seed Vault: launch import flow; user enters seed in Seed Vault secure UI */
                    if (isSeedVaultAvailable(context)) {
                        val importIntent = createImportSeedIntent(context)
                        if (importIntent != null) {
                            Spacer(modifier = Modifier.height(Spacing.xs.dp))
                            OutlinedButton(
                                onClick = { seedVaultImportLauncher.launch(importIntent) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("SAVE TO SEED VAULT")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Button(
                        onClick = { viewModel.reset(); onBack() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("DONE")
                    }
                }
                state is RecoveryState.Error -> {
                    val s = state as RecoveryState.Error
                    LaunchedEffect(s.message) { view.performHapticFeedback(HapticFeedbackConstantsCompat.REJECT) }
                    StatusBar(status = s.message)
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Button(onClick = { viewModel.reset(); picker.launch("audio/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text("TRY AGAIN")
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    OutlinedButton(onClick = { viewModel.reset(); picker.launch("audio/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text("SELECT ANOTHER")
                    }
                }
            }
        }
    }
}
