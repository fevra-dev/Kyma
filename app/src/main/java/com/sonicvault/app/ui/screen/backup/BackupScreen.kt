package com.sonicvault.app.ui.screen.backup

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonicvault.app.SonicVaultApplication
import com.sonicvault.app.domain.model.BackupState
import com.sonicvault.app.ui.component.StatusBar
import com.sonicvault.app.ui.theme.Spacing
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.util.shareStegoFile
import kotlinx.coroutines.launch

/**
 * Backup flow: clear structure, 8dp grid, unobtrusive bar. Rams: understandable, thorough.
 * Progressive disclosure: Duress, Timelock, Geolock under "More options" to keep main path simple.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    savedStateHandle: SavedStateHandle? = null
) {
    val app = LocalContext.current.applicationContext as SonicVaultApplication
    /* Cache factory in remember to avoid recomposition-triggered recreation (frame jank mitigation) */
    val factory = remember(app) {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return BackupViewModel(app.createBackupUseCase, app.audioRecorder, app.userPreferences) as T
            }
        }
    }
    val viewModel: BackupViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val coverUri by viewModel.coverUri.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingElapsedSeconds by viewModel.recordingElapsedSeconds.collectAsState()
    var seedPhrase by remember { mutableStateOf("") }
    var showPhrase by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    /** OpenDocument gives users the full system file browser (Music, Downloads, etc.) */
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        viewModel.setCoverUri(uri)
    }
    val scope = rememberCoroutineScope()

    /* When Error, back resets to form instead of exiting; otherwise back pops to Home. */
    BackHandler {
        if (state is BackupState.Error) viewModel.reset() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CREATE BACKUP", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = { if (state is BackupState.Error) viewModel.reset() else onBack() },
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
                .imePadding()
        ) {
            /* Scrollable form area: scrolls naturally when keyboard opens. Rams: thorough, useful. */
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md.dp)
                    .padding(top = Spacing.sm.dp)
                    .padding(bottom = Spacing.xs.dp)
            ) {
            when (state) {
                is BackupState.Idle,
                is BackupState.Validating,
                is BackupState.Encrypting,
                is BackupState.Embedding,
                is BackupState.WritingFile -> {
                    val inProgress = state is BackupState.Encrypting || state is BackupState.Embedding || state is BackupState.WritingFile
                    if (inProgress) {
                        StatusBar(status = "Creating…", isActive = true)
                        Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    }
                    /* Step 1: Seed phrase — primary input. */
                    EnterSeedStep(
                        seedPhrase = seedPhrase,
                        onSeedPhraseChange = {
                            seedPhrase = it
                            viewModel.setSeedPhrase(it)
                        },
                        showPhrase = showPhrase,
                        onShowPhraseChange = { showPhrase = it },
                        scrollable = false,
                        compactForFixedLayout = true
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    /* Step 2: Encryption password (required — enables cross-device recovery). */
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val pwMinLen = com.sonicvault.app.domain.usecase.CreateBackupUseCase.MIN_PASSWORD_LENGTH
                        val passwordTooShort = password.isNotEmpty() && password.length < pwMinLen
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                viewModel.setPassword(if (it.length >= pwMinLen) it else null)
                            },
                            label = { Text("Encryption password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = passwordTooShort,
                            supportingText = if (passwordTooShort) {
                                { Text("Minimum $pwMinLen characters", color = MaterialTheme.colorScheme.error) }
                            } else null,
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
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    /* Step 3: Cover audio — the container for the embedded secret. */
                    PickCoverStep(
                        selectedUri = coverUri,
                        isRecording = isRecording,
                        recordingElapsedSeconds = recordingElapsedSeconds,
                        onPickCover = { picker.launch(arrayOf("audio/*")) },
                        onRecordCover = { viewModel.recordCover() },
                        onStopRecording = { viewModel.stopRecording() }
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    val activity = LocalContext.current
                    if (activity is androidx.fragment.app.FragmentActivity && inProgress) {
                        CircularProgressIndicator(modifier = Modifier.padding(Spacing.sm.dp))
                    }
                }
                is BackupState.Success -> {
                    val success = state as BackupState.Success
                    /* Haptic confirmation on successful backup. Rams: thorough feedback. */
                    val view = LocalView.current
                    LaunchedEffect(success) {
                        view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM)
                    }
                    StatusBar(status = "Complete")
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    val ctx = LocalContext.current
                    BackupResultStep(
                        stegoUri = success.stegoUri,
                        errorMessage = null,
                        checksum = success.checksum,
                        fingerprint = success.fingerprint,
                        shortId = success.shortId,
                        onShare = { format ->
                            scope.launch {
                                try {
                                    shareStegoFile(ctx, success.stegoUri, format, app.flacExporter)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(ctx, "Export failed. Try WAV.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDone = { }
                    )
                }
                is BackupState.Error -> {
                    StatusBar(status = (state as BackupState.Error).message)
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    BackupResultStep(
                        stegoUri = null,
                        errorMessage = (state as BackupState.Error).message,
                        onDone = { }
                    )
                }
            }
            }
            /* Sticky footer: primary action always visible (Rams: useful, understandable) */
            val activity = LocalContext.current
            if (activity is androidx.fragment.app.FragmentActivity) {
                val minPasswordLength = com.sonicvault.app.domain.usecase.CreateBackupUseCase.MIN_PASSWORD_LENGTH
                val passwordValid = password.length >= minPasswordLength
                val wordCount = seedPhrase.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                val seedValid = wordCount == 12 || wordCount == 24
                val coverSelected = coverUri != null
                val canCreate = seedValid && coverSelected && passwordValid
                val inProgress = state is BackupState.Encrypting || state is BackupState.Embedding || state is BackupState.WritingFile
                Button(
                    onClick = when (state) {
                        is BackupState.Success -> { { viewModel.reset(); onBack() } }
                        is BackupState.Error -> { { viewModel.reset() } }
                        else -> { { viewModel.startBackup(activity) } }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md.dp),
                    enabled = when (state) {
                        is BackupState.Success, is BackupState.Error -> true
                        else -> !inProgress && canCreate
                    }
                ) {
                    Text(
                        when (state) {
                            is BackupState.Success -> "DONE"
                            is BackupState.Error -> "TRY AGAIN"
                            else -> if (inProgress) "CREATING…" else "CREATE BACKUP"
                        }
                    )
                }
            }
        }
    }
}
