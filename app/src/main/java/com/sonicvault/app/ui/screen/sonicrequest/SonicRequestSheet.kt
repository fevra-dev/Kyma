package com.sonicvault.app.ui.screen.sonicrequest

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.compose.ui.unit.dp
import com.sonicvault.app.MainActivity
import com.sonicvault.app.data.solana.SolanaPayUri
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing

/**
 * Bottom sheet for SonicRequest payment confirmation.
 * Displays: label, amount, recipient, memo. Actions: Approve / Decline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonicRequestSheet(
    viewModel: SonicRequestViewModel,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val mainActivity = context as? MainActivity

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.md.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)
    ) {
        when (val s = state) {
            is SonicRequestViewModel.State.Received -> {
                PaymentRequestContent(uri = s.uri)
                Spacer(modifier = Modifier.height(Spacing.md.dp))
                Button(
                    onClick = {
                        mainActivity?.let { viewModel.approve(it.activityResultSender) }
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
                    Text("APPROVE PAYMENT", style = LabelUppercaseStyle)
                }
                OutlinedButton(
                    onClick = {
                        viewModel.decline()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = androidx.compose.ui.graphics.RectangleShape
                ) {
                    Text("DECLINE")
                }
            }
            is SonicRequestViewModel.State.Signing,
            is SonicRequestViewModel.State.Confirming -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(Spacing.xs.dp))
                Text(
                    text = if (s is SonicRequestViewModel.State.Signing) "Signing payment…" else "Confirming…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is SonicRequestViewModel.State.Success -> {
                LaunchedEffect(s) { view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM) }
                Text(
                    text = "PAID",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Sig: ${truncateSignature(s.signature)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.xs.dp))
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.explorerUrl)))
                }) {
                    Text("VIEW IN EXPLORER")
                }
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Signature", s.signature))
                }) {
                    Text("COPY SIGNATURE")
                }
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                OutlinedButton(onClick = { viewModel.reset(); onDismiss() }) {
                    Text("DONE")
                }
            }
            is SonicRequestViewModel.State.Error -> {
                LaunchedEffect(s) { view.performHapticFeedback(HapticFeedbackConstantsCompat.REJECT) }
                Text(
                    text = mapPaymentError(s.message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(Spacing.xs.dp))
                OutlinedButton(onClick = { viewModel.reset(); onDismiss() }) {
                    Text("TRY AGAIN")
                }
            }
            is SonicRequestViewModel.State.Idle -> {
                Text(
                    text = "No payment request",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PaymentRequestContent(uri: SolanaPayUri) {
    val amountStr = uri.amount?.let { "$it SOL" } ?: "—"
    val recipientShort = truncateAddress(uri.recipient)

    Text(
        text = "PAYMENT REQUEST",
        style = LabelUppercaseStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(Spacing.xs.dp))
    uri.label?.let { label ->
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    Text(
        text = amountStr,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = "To: $recipientShort",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    uri.memo?.let { memo ->
        Text(
            text = "Memo: $memo",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 12 + ... + 8 format for better verification. */
private fun truncateAddress(address: String): String {
    if (address.length <= 24) return address
    return "${address.take(12)}…${address.takeLast(8)}"
}

/** First 24 chars + ellipsis for signatures. */
private fun truncateSignature(sig: String): String {
    if (sig.length <= 28) return sig
    return "${sig.take(24)}…"
}

/** Maps technical errors to actionable guidance. */
private fun mapPaymentError(raw: String): String = when {
    raw.contains("wallet", ignoreCase = true) || raw.contains("connection", ignoreCase = true) ->
        "Wallet connection failed. Make sure your wallet app is open and try again."
    raw.contains("insufficient", ignoreCase = true) || raw.contains("balance", ignoreCase = true) ->
        "Insufficient balance. Add SOL to your wallet and try again."
    raw.contains("sign", ignoreCase = true) || raw.contains("reject", ignoreCase = true) ->
        "Payment was cancelled or rejected."
    raw.contains("timeout", ignoreCase = true) ->
        "Request timed out. Check your connection and try again."
    raw.contains("rpc", ignoreCase = true) || raw.contains("network", ignoreCase = true) ->
        "Network error. Check your internet connection and try again."
    else -> raw
}
