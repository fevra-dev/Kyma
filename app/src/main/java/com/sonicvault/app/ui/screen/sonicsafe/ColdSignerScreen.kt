package com.sonicvault.app.ui.screen.sonicsafe

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.sonicvault.app.MainActivity
import com.sonicvault.app.data.solana.SolanaTxParser
import com.sonicvault.app.data.solana.SolanaTransactionBuilder
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing
import com.sonicvault.app.ui.theme.TouchTargetMin

/**
 * Cold signer: receives TX via acoustic chunks, displays review, signs via Seed Vault.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColdSignerScreen(
    viewModel: SonicSafeViewModel,
    onBack: () -> Unit,
    embedded: Boolean = false
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val mainActivity = context as? MainActivity

    val requestRecordAudio = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && viewModel.state.value is SonicSafeViewModel.State.Idle) {
            viewModel.startListening(context.applicationContext)
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.state.value !is SonicSafeViewModel.State.Idle) return@LaunchedEffect
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> viewModel.startListening(context.applicationContext)
            else -> requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopListening() }
    }

    val content: @Composable (PaddingValues) -> Unit = { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.md.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)
        ) {
            when (val s = state) {
                is SonicSafeViewModel.State.Idle -> {
                    Text(
                        text = "Listening for cold sign request…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is SonicSafeViewModel.State.Received -> {
                    val parsed = SolanaTxParser.parseForDisplay(s.txBytes)
                    Text(
                        text = if (parsed != null) {
                            val sol = parsed.amountLamports.toDouble() / SolanaTransactionBuilder.LAMPORTS_PER_SOL
                            "Transfer ${"%.6f".format(sol).trimEnd('0').trimEnd('.')} SOL"
                        } else {
                            "Transaction received (${s.txBytes.size} bytes)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (parsed != null) {
                        Spacer(modifier = Modifier.height(Spacing.xs.dp))
                        Text(
                            text = "From: ${truncateAddress(parsed.fromAddress)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "To: ${truncateAddress(parsed.toAddress)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (parsed.isDurableNonce) {
                            Text(
                                text = "Durable nonce — no expiry",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Button(
                        onClick = {
                            mainActivity?.let { viewModel.signAndTransmit(it.activityResultSender) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("APPROVE & SIGN", style = LabelUppercaseStyle)
                    }
                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                        shape = androidx.compose.ui.graphics.RectangleShape
                    ) {
                        Text("DECLINE")
                    }
                }
                is SonicSafeViewModel.State.Signing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Text(
                        text = "Signing transaction…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is SonicSafeViewModel.State.Success -> {
                    LaunchedEffect(s) { view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM) }
                    Text(
                        text = "Signed and transmitted",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Sig: ${truncateSignature(s.signature)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Signature", s.signature))
                    }) {
                        Text("COPY SIGNATURE")
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    OutlinedButton(onClick = { viewModel.reset() }) {
                        Text("DONE")
                    }
                }
                is SonicSafeViewModel.State.Error -> {
                    LaunchedEffect(s) { view.performHapticFeedback(HapticFeedbackConstantsCompat.REJECT) }
                    Text(
                        text = mapErrorMessage(s.message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    OutlinedButton(onClick = { viewModel.reset() }) {
                        Text("TRY AGAIN")
                    }
                }
            }
        }
    }

    if (embedded) {
        content(PaddingValues())
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("SONIC SAFE", style = MaterialTheme.typography.titleMedium) },
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

/** 12 + ... + 8 format for better verification than 8+4. */
private fun truncateAddress(address: String): String {
    if (address.length <= 24) return address
    return "${address.take(12)}…${address.takeLast(8)}"
}

/** First 24 chars + ... for signatures. */
private fun truncateSignature(sig: String): String {
    if (sig.length <= 28) return sig
    return "${sig.take(24)}…"
}

/** Maps technical errors to user-friendly guidance. */
private fun mapErrorMessage(raw: String): String = when {
    raw.contains("wallet", ignoreCase = true) || raw.contains("connection", ignoreCase = true) ->
        "Wallet connection failed. Make sure your wallet app is open and try again."
    raw.contains("sign", ignoreCase = true) || raw.contains("reject", ignoreCase = true) ->
        "Signing was cancelled or rejected. Try again when ready."
    raw.contains("timeout", ignoreCase = true) || raw.contains("timed out", ignoreCase = true) ->
        "Request timed out. Move devices closer together and try again."
    raw.contains("microphone", ignoreCase = true) || raw.contains("audio", ignoreCase = true) ->
        "Microphone unavailable. Check permissions and use a physical device."
    else -> raw
}
