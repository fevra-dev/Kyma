package com.sonicvault.app.ui.screen.sonicrequest

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonicvault.app.ui.component.StatusBar
import com.sonicvault.app.ui.theme.Spacing
import com.sonicvault.app.ui.theme.TouchTargetMin

/**
 * SonicRequest screen: listens for acoustic Solana Pay URIs, shows bottom sheet on receive.
 *
 * Entry from Home (Receive) or Settings. Rams: useful, understandable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonicRequestScreen(
    onBack: () -> Unit,
    viewModel: SonicRequestViewModel = viewModel(),
    embedded: Boolean = false
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val requestRecordAudio = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startListening(context.applicationContext)
    }

    LaunchedEffect(Unit) {
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> viewModel.startListening(context.applicationContext)
            else -> requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(state) {
        when (state) {
            is SonicRequestViewModel.State.Received -> showSheet = true
            else -> { /* keep sheet state */ }
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopListening() }
    }

    val content = @Composable {
        Box(
            modifier = if (embedded) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (embedded) Spacing.xs.dp else Spacing.md.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatusBar(
                    status = when (state) {
                        is SonicRequestViewModel.State.Idle -> "LISTENING…"
                        is SonicRequestViewModel.State.Received -> "REQUEST RECEIVED"
                        is SonicRequestViewModel.State.Signing -> "SIGNING…"
                        is SonicRequestViewModel.State.Confirming -> "CONFIRMING…"
                        is SonicRequestViewModel.State.Success -> "PAID"
                        is SonicRequestViewModel.State.Error -> "ERROR"
                    },
                    isActive = state is SonicRequestViewModel.State.Received,
                    isError = state is SonicRequestViewModel.State.Error
                )
                Spacer(modifier = Modifier.height(Spacing.lg.dp))
                Text(
                    text = "Hold device near terminal speaker",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (embedded) {
        content()
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("SONIC REQUEST", style = MaterialTheme.typography.titleMedium) },
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
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                content()
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                viewModel.decline()
            },
            sheetState = sheetState
        ) {
            SonicRequestSheet(
                viewModel = viewModel,
                sheetState = sheetState,
                onDismiss = { showSheet = false }
            )
        }
    }
}
