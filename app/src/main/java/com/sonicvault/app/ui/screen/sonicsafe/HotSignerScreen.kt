package com.sonicvault.app.ui.screen.sonicsafe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.sonicvault.app.data.solana.SolanaTransactionBuilder
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing
import com.sonicvault.app.ui.theme.TouchTargetMin

/**
 * SEND SOL form content: recipient, amount, primary button. Reusable in DeadDropScreen or standalone.
 * Rams: useful, understandable. Ma: breathing room. Use compact=true when embedded in scrollable layout.
 *
 * @param compact When true, does not use fillMaxSize — for embedding in verticalScroll Column.
 */
/**
 * @param contentAboveButton Optional composable slot above the primary button (e.g. Protocol row, SoundHandshakeIndicator).
 */
@Composable
fun SendSolFormContent(
    viewModel: SonicSafeHotViewModel,
    compact: Boolean = false,
    primaryButtonLabel: String = "CONFIRM SEND",
    contentAboveButton: @Composable (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    val view = LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val mainActivity = context as? com.sonicvault.app.MainActivity

    var recipient by remember { mutableStateOf("") }
    var amountSol by remember { mutableStateOf("0.001") }

    Column(
        modifier = if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize().fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)
    ) {
        when (val s = state) {
            is SonicSafeHotViewModel.State.Idle,
            is SonicSafeHotViewModel.State.Building,
            is SonicSafeHotViewModel.State.Transmitting,
            is SonicSafeHotViewModel.State.Listening -> {
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("To") },
                    placeholder = { Text("Recipient SOL address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = s is SonicSafeHotViewModel.State.Idle,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
                OutlinedTextField(
                    value = amountSol,
                    onValueChange = { amountSol = it },
                    label = { Text("Amount (SOL)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = s is SonicSafeHotViewModel.State.Idle,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                val statusText = when (s) {
                    is SonicSafeHotViewModel.State.Building -> "Building transaction…"
                    is SonicSafeHotViewModel.State.Transmitting -> "Transmitting…"
                    is SonicSafeHotViewModel.State.Listening -> "Waiting for signed TX…"
                    else -> null
                }
                if (statusText != null) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.height(24.dp).padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (contentAboveButton != null) contentAboveButton()
                Button(
                    onClick = {
                        if (mainActivity != null && recipient.isNotBlank()) {
                            val lamports = (amountSol.toDoubleOrNull() ?: 0.0) * SolanaTransactionBuilder.LAMPORTS_PER_SOL
                            if (lamports > 0) {
                                viewModel.sendForSigning(
                                    sender = mainActivity.activityResultSender,
                                    appContext = mainActivity.applicationContext,
                                    recipient = recipient.trim(),
                                    lamports = lamports.toLong()
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = s is SonicSafeHotViewModel.State.Idle && recipient.isNotBlank()
                ) {
                    Text(primaryButtonLabel, style = LabelUppercaseStyle)
                }
            }
            is SonicSafeHotViewModel.State.Success -> {
                LaunchedEffect(s) { view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM) }
                Text(
                    text = "Broadcast successful",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Sig: ${s.signature.take(24)}…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = {
                    view.context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(s.explorerUrl)))
                }) {
                    Text("VIEW IN EXPLORER")
                }
                OutlinedButton(onClick = {
                    val ctx = view.context
                    val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Signature", s.signature))
                }) {
                    Text("COPY SIGNATURE")
                }
                OutlinedButton(onClick = { viewModel.reset() }) {
                    Text("DONE")
                }
            }
            is SonicSafeHotViewModel.State.Error -> {
                LaunchedEffect(s) { view.performHapticFeedback(HapticFeedbackConstantsCompat.REJECT) }
                Text(
                    text = s.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedButton(onClick = { viewModel.reset() }) {
                    Text("TRY AGAIN")
                }
            }
        }
    }
}

/**
 * Hot broadcaster: build TX, transmit chunked, listen for signed TX, broadcast.
 *
 * Rams: useful, understandable. Ma: breathing room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotSignerScreen(
    viewModel: SonicSafeHotViewModel,
    onBack: () -> Unit,
    embedded: Boolean = false
) {
    val content: @Composable (PaddingValues) -> Unit = { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.md.dp)
        ) {
            /* compact=false when in SonicSafe: fill available space. DeadDropScreen uses compact=true for scroll. */
            SendSolFormContent(
                viewModel = viewModel,
                compact = false,
                primaryButtonLabel = "SEND FOR SIGNING"
            )
        }
    }

    if (embedded) {
        content(PaddingValues())
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("SONIC SEND — HOT", style = MaterialTheme.typography.titleMedium) },
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
        ) { content(it) }
    }
}
