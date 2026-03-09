package com.sonicvault.app.ui.screen.shamir

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonicvault.app.SonicVaultApplication
import com.sonicvault.app.ui.component.StatusBar
import com.sonicvault.app.ui.screen.backup.EnterSeedStep
import com.sonicvault.app.ui.screen.backup.PickCoverStep
import com.sonicvault.app.ui.theme.Spacing
import com.sonicvault.app.util.ExportFormat
import com.sonicvault.app.util.shareStegoFile

/** Presets for M-of-N: 2-of-3, 2-of-5, 3-of-5. (threshold, total) */
private val SHAMIR_PRESETS = listOf(
    "2-of-3" to (2 to 3),
    "2-of-5" to (2 to 5),
    "3-of-5" to (3 to 5)
)

/**
 * Visual preset: N boxes, M filled when selected. Improves clarity per iancoleman/shamir39 UX.
 * Scale box size so all N squares fit (3-of-5 was cutting off on narrow screens).
 */
@Composable
private fun ShamirPresetRow(
    presetIndex: Int,
    presets: List<Pair<String, Pair<Int, Int>>>,
    onPresetChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEachIndexed { i, (label, _) ->
            val (m, n) = presets[i].second
            val isSelected = presetIndex == i
            /* Smaller boxes for n>=5 so all squares fit in 1/3 screen width. */
            val boxSizeDp = if (n <= 3) 20.dp else 12.dp
            val innerSizeDp = if (n <= 3) 16.dp else 10.dp
            Surface(
                onClick = { onPresetChange(i) },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.xs.dp / 2),
                shape = RectangleShape,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                ),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.sm.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(n) { boxIndex ->
                            Box(
                                modifier = Modifier
                                    .padding(1.dp)
                                    .size(boxSizeDp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RectangleShape,
                                    color = if (boxIndex < m) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(innerSizeDp)
                                ) {}
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitSeedScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as SonicVaultApplication
    /* Cache factory in remember to avoid recomposition-triggered recreation (frame jank mitigation) */
    val factory = remember(app) {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SplitSeedViewModel(app.splitSeedUseCase, app.audioRecorder) as T
            }
        }
    }
    val viewModel: SplitSeedViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val coverUri by viewModel.coverUri.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingElapsedSeconds by viewModel.recordingElapsedSeconds.collectAsState()
    var seedPhrase by remember { mutableStateOf("") }
    var showPhrase by remember { mutableStateOf(false) }
    var presetIndex by remember { mutableStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.setCoverUri(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SPLIT BACKUP", style = MaterialTheme.typography.titleMedium) },
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
                .imePadding()
        ) {
            when (val s = state) {
                SplitSeedState.Idle,
                SplitSeedState.Splitting -> {
                    val inProgress = s is SplitSeedState.Splitting
                    /* Scrollable form area so content + button are reachable on all screen sizes. */
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.md.dp)
                            .padding(top = Spacing.sm.dp)
                    ) {
                        if (inProgress) {
                            StatusBar(status = "Creating shares…", isActive = true)
                            Spacer(modifier = Modifier.height(Spacing.sm.dp))
                        }
                        EnterSeedStep(
                            seedPhrase = seedPhrase,
                            onSeedPhraseChange = { seedPhrase = it; viewModel.setSeedPhrase(it) },
                            showPhrase = showPhrase,
                            onShowPhraseChange = { showPhrase = it },
                            scrollable = false,
                            compactForFixedLayout = true
                        )
                        Spacer(modifier = Modifier.height(Spacing.md.dp))
                        Text(
                            "Share scheme:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs.dp))
                        ShamirPresetRow(
                            presetIndex = presetIndex,
                            presets = SHAMIR_PRESETS,
                            onPresetChange = { i ->
                                presetIndex = i
                                val (m, n) = SHAMIR_PRESETS[i].second
                                viewModel.setThreshold(m)
                                viewModel.setTotalShares(n)
                            }
                        )
                        Spacer(modifier = Modifier.height(Spacing.md.dp))
                        val totalShares = SHAMIR_PRESETS[presetIndex].second.second
                        Text(
                            text = "One cover audio is used for all shares. You'll get $totalShares different output files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs.dp))
                        PickCoverStep(
                            selectedUri = coverUri,
                            isRecording = isRecording,
                            recordingElapsedSeconds = recordingElapsedSeconds,
                            onPickCover = { picker.launch("audio/*") },
                            onRecordCover = { viewModel.recordCover() },
                            onStopRecording = { viewModel.stopRecording() }
                        )
                        Spacer(modifier = Modifier.height(Spacing.md.dp))
                    }
                    /* Sticky bottom button; always visible regardless of scroll position. */
                    val wordCount = seedPhrase.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                    val activity = LocalContext.current
                    if (activity is androidx.fragment.app.FragmentActivity) {
                        val seedValid = wordCount == 12 || wordCount == 24
                        val coverSelected = coverUri != null
                        val canCreate = seedValid && coverSelected
                        Button(
                            onClick = {
                                val (m, n) = SHAMIR_PRESETS[presetIndex].second
                                viewModel.setThreshold(m)
                                viewModel.setTotalShares(n)
                                viewModel.startSplit(activity)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md.dp),
                            enabled = !inProgress && canCreate,
                            shape = RectangleShape
                        ) {
                            Text(if (inProgress) "CREATING…" else "SPLIT & CREATE SHARES")
                        }
                    }
                }
                is SplitSeedState.Success -> {
                    StatusBar(status = "Complete")
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    /* Phase 4: Step-by-step guidance to avoid overwriting Share 1 with Share 2. */
                    Text(
                        "Store each share in a different location. Do not overwrite one with another.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    val ctx = LocalContext.current
                    val scope = rememberCoroutineScope()
                    var exportFormat by remember { mutableStateOf(ExportFormat.WAV) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Export as:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = Spacing.sm.dp)
                        )
                        OutlinedButton(
                            onClick = { exportFormat = ExportFormat.WAV },
                            modifier = Modifier.weight(1f).padding(horizontal = Spacing.xs.dp / 2),
                            shape = RectangleShape
                        ) {
                            Text("WAV", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { exportFormat = ExportFormat.FLAC },
                            modifier = Modifier.weight(1f).padding(horizontal = Spacing.xs.dp / 2),
                            shape = RectangleShape
                        ) {
                            Text("FLAC", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    val total = s.results.size
                    s.results.forEachIndexed { i, result ->
                        Text(
                            "Share ${i + 1} of $total: Tap to save or transmit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Spacing.xs.dp)
                        )
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    shareStegoFile(ctx, result.stegoUri, exportFormat, app.flacExporter)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RectangleShape
                        ) {
                            Text("TRANSMIT SHARE ${i + 1}")
                        }
                        Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Button(onClick = { viewModel.reset(); onBack() }, modifier = Modifier.fillMaxWidth(), shape = RectangleShape) {
                        Text("DONE")
                    }
                }
                is SplitSeedState.Error -> {
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
