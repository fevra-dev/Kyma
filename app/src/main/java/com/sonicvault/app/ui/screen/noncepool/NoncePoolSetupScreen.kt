package com.sonicvault.app.ui.screen.noncepool

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonicvault.app.MainActivity
import com.sonicvault.app.SonicVaultApplication
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing

/**
 * Nonce pool setup: discover/import existing nonce accounts.
 *
 * Rams: useful, understandable. Ma: breathing room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoncePoolSetupScreen(
    onBack: () -> Unit,
    viewModel: NoncePoolSetupViewModel = viewModel(
        factory = NoncePoolSetupViewModelFactory(
            (LocalContext.current.applicationContext as SonicVaultApplication).noncePoolManager
        )
    )
) {
    val state by viewModel.state.collectAsState()
    var authorityPubkey by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NONCE POOL", style = MaterialTheme.typography.titleMedium) },
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
                .padding(Spacing.md.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)
        ) {
            Text(
                text = "Import existing nonce accounts for SonicSafe.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.sm.dp))

            OutlinedTextField(
                value = authorityPubkey,
                onValueChange = { authorityPubkey = it },
                label = { Text("Wallet address (authority)") },
                placeholder = { Text("Your Phantom wallet address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = state is NoncePoolSetupViewModel.State.Idle ||
                    state is NoncePoolSetupViewModel.State.Success ||
                    state is NoncePoolSetupViewModel.State.Error
            )

            val isDiscovering = state is NoncePoolSetupViewModel.State.Discovering
            OutlinedButton(
                onClick = { viewModel.discoverAndImport(authorityPubkey) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium,
                enabled = !isDiscovering
            ) {
                Text(
                    text = if (isDiscovering) "DISCOVERING…" else "DISCOVER & IMPORT",
                    style = LabelUppercaseStyle
                )
            }

            Text(
                text = viewModel.poolCostEstimate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val isCreating = state is NoncePoolSetupViewModel.State.Creating
            val mainActivity = LocalContext.current as? MainActivity
            OutlinedButton(
                onClick = {
                    mainActivity?.let { viewModel.setupPool(it.activityResultSender) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium,
                enabled = !isCreating && mainActivity != null
            ) {
                Text(
                    text = if (isCreating) "CREATING…" else "SETUP POOL (3)",
                    style = LabelUppercaseStyle
                )
            }

            Text(
                text = viewModel.costEstimate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = {
                    mainActivity?.let { viewModel.createNonceAccount(it.activityResultSender) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium,
                enabled = !isCreating && mainActivity != null
            ) {
                Text(
                    text = if (isCreating) "CREATING…" else "CREATE NONCE ACCOUNT",
                    style = LabelUppercaseStyle
                )
            }

            when (val s = state) {
                is NoncePoolSetupViewModel.State.Success -> {
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(onClick = { viewModel.reset() }) {
                        Text("DONE")
                    }
                }
                is NoncePoolSetupViewModel.State.Error -> {
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(onClick = { viewModel.reset() }) {
                        Text("DISMISS")
                    }
                }
                else -> {}
            }
        }
    }
}

private class NoncePoolSetupViewModelFactory(
    private val noncePoolManager: com.sonicvault.app.data.nonce.NoncePoolManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        NoncePoolSetupViewModel(noncePoolManager) as T
}
