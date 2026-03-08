package com.sonicvault.app.ui.screen.vote

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonicvault.app.data.solana.GovernanceConstants
import com.sonicvault.app.ui.component.SoundHandshakeIndicator
import com.sonicvault.app.ui.component.ConnectionState
import com.sonicvault.app.ui.component.StatusBar
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing
import com.sonicvault.app.ui.theme.TouchTargetMin
import com.sonicvault.app.MainActivity

/**
 * Guardian Voting Demo: two-tab screen.
 *
 * KIOSK tab: proposal pubkey input, vote direction selector, BROADCAST, listen for returned signatures.
 * VOTE tab: listen for proposal, APPROVE/DECLINE, cast vote via SPL Memo, return signature acoustically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianVoteScreen(
    onBack: () -> Unit,
    viewModel: GuardianVoteViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val state by viewModel.state.collectAsState()
    val kioskState by viewModel.kioskState.collectAsState()
    val context = LocalContext.current

    val requestRecordAudio = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && selectedTab == 1) {
            viewModel.startListening(context.applicationContext)
        }
    }

    LaunchedEffect(Unit) {
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> { /* both tabs ready */ }
            else -> requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            1 -> {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.startListening(context.applicationContext)
                }
            }
            else -> viewModel.stopListening()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopListening() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GUARDIAN VOTING", style = MaterialTheme.typography.titleMedium) },
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
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("KIOSK") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("VOTE") }
                )
            }
            when (selectedTab) {
                0 -> KioskTab(
                    viewModel = viewModel,
                    kioskState = kioskState,
                    context = context
                )
                1 -> VoteTab(
                    viewModel = viewModel,
                    state = state,
                    context = context
                )
            }
        }
    }
}

@Composable
private fun KioskTab(
    viewModel: GuardianVoteViewModel,
    kioskState: GuardianVoteViewModel.KioskState,
    context: android.content.Context
) {
    var proposalPubkey by remember { mutableStateOf(GovernanceConstants.KYMA_DEMO_PROPOSAL) }
    var direction by remember { mutableStateOf(GovernanceConstants.VoteDirection.YES) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Proposal pubkey",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.xs.dp))
        OutlinedTextField(
            value = proposalPubkey,
            onValueChange = { proposalPubkey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Base58 proposal address") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(Spacing.md.dp))
        Text(
            text = "Vote direction",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.xs.dp))
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)
        ) {
            listOf(
                GovernanceConstants.VoteDirection.YES,
                GovernanceConstants.VoteDirection.NO,
                GovernanceConstants.VoteDirection.ABSTAIN
            ).forEach { d ->
                OutlinedButton(
                    onClick = { direction = d },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        d.name,
                        modifier = Modifier.padding(vertical = 4.dp),
                        style = if (direction == d) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.lg.dp))
        Button(
            onClick = {
                viewModel.broadcastProposal(context.applicationContext, proposalPubkey, direction)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("BROADCAST PROPOSAL", style = LabelUppercaseStyle)
        }
        Spacer(modifier = Modifier.height(Spacing.xl.dp))
        Text(
            text = "Votes collected: ${kioskState.votesCollected}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        kioskState.lastSignature?.let { sig ->
            Text(
                text = "Last sig: $sig",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VoteTab(
    viewModel: GuardianVoteViewModel,
    state: GuardianVoteViewModel.State,
    context: android.content.Context
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val s = state) {
            is GuardianVoteViewModel.State.Idle,
            is GuardianVoteViewModel.State.Listening -> {
                StatusBar(status = "LISTENING…", isActive = true, isError = false)
                Spacer(modifier = Modifier.height(Spacing.lg.dp))
                SoundHandshakeIndicator(connectionState = ConnectionState.LISTENING)
                Spacer(modifier = Modifier.height(Spacing.md.dp))
                Text(
                    text = "Hold device near kiosk",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is GuardianVoteViewModel.State.Received -> {
                StatusBar(status = "PROPOSAL RECEIVED", isActive = true, isError = false)
                Spacer(modifier = Modifier.height(Spacing.md.dp))
                Text(
                    text = "Proposal: ${s.proposalPubkey.take(16)}…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Direction: ${s.direction.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.lg.dp))
                Button(
                    onClick = {
                        (context as? MainActivity)?.activityResultSender?.let {
                            viewModel.castVote(it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("APPROVE & VOTE", style = LabelUppercaseStyle)
                }
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                OutlinedButton(
                    onClick = { viewModel.reset(); viewModel.startListening(context.applicationContext) }
                ) {
                    Text("DECLINE")
                }
            }
            is GuardianVoteViewModel.State.Signing -> {
                StatusBar(status = "SIGNING…", isActive = true, isError = false)
                Text(
                    text = "Sign with wallet…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is GuardianVoteViewModel.State.VoteSubmitted,
            is GuardianVoteViewModel.State.TransmittingReturn -> {
                StatusBar(status = "SUBMITTING…", isActive = true, isError = false)
                Text(
                    text = "Returning signature…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is GuardianVoteViewModel.State.Success -> {
                LaunchedEffect(s) {
                    (context as? android.view.View)?.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM)
                }
                StatusBar(status = "VOTE SUCCESS", isActive = true, isError = false)
                Spacer(modifier = Modifier.height(Spacing.md.dp))
                Text(
                    text = "Vote submitted and signature returned",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(Spacing.md.dp))
                OutlinedButton(onClick = { viewModel.reset(); viewModel.startListening(context.applicationContext) }) {
                    Text("VOTE AGAIN")
                }
            }
            is GuardianVoteViewModel.State.Error -> {
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
