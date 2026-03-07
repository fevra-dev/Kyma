package com.sonicvault.app.ui.screen.sonicsafe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonicvault.app.SonicVaultApplication
import com.sonicvault.app.ui.theme.Spacing

/**
 * SonicSafe mode selector: Cold (listen & sign) or Hot (send TX & broadcast).
 *
 * Rams: useful, understandable. Ma: breathing room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonicSafeScreen(
    onBack: () -> Unit
) {
    /** Rams: Hot (crypto send) as default; Cold (air-gapped sign) secondary. */
    var mode by remember { mutableStateOf("hot") }
    val context = LocalContext.current
    val app = context.applicationContext as SonicVaultApplication
    val hotViewModelFactory = remember(app) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SonicSafeHotViewModel(app.noncePoolManager, app.userPreferences) as T
        }
    }
    val coldViewModelFactory = remember(app) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SonicSafeViewModel(app.userPreferences) as T
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SONIC SAFE", style = MaterialTheme.typography.titleMedium) },
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
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)
            ) {
                FilterChip(
                    selected = mode == "cold",
                    onClick = { mode = "cold" },
                    label = { Text("COLD") }
                )
                FilterChip(
                    selected = mode == "hot",
                    onClick = { mode = "hot" },
                    label = { Text("HOT") }
                )
            }
            when (mode) {
                "cold" -> ColdSignerScreen(
                    viewModel = viewModel(factory = coldViewModelFactory),
                    onBack = onBack,
                    embedded = true
                )
                "hot" -> HotSignerScreen(
                    viewModel = viewModel(factory = hotViewModelFactory),
                    onBack = onBack,
                    embedded = true
                )
            }
        }
    }
}
