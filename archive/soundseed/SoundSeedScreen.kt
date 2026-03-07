package com.sonicvault.app.ui.screen.soundseed

import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonicvault.app.SonicVaultApplication
import com.sonicvault.app.ui.component.RadiatingRingsAnimation
import com.sonicvault.app.ui.component.StatusBar
import com.sonicvault.app.ui.screen.recovery.ShowSeedStep
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing

/**
 * Sound as Seed: generate BIP39 mnemonics from ambient audio entropy.
 *
 * Record ambient sound, extract entropy from multiple audio features,
 * mix with SecureRandom, and produce a valid BIP39 seed phrase.
 *
 * Rams: innovative (novel key generation), honest (shows entropy quality),
 * understandable (live meter shows recording progress).
 */
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundSeedScreen(
    onBack: () -> Unit,
    onUseForBackup: ((String) -> Unit)? = null
) {
    val app = LocalContext.current.applicationContext as SonicVaultApplication
    val factory = remember(app) {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SoundSeedViewModel(app.bip39Validator.wordList) as T
            }
        }
    }
    val viewModel: SoundSeedViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val quality by viewModel.entropyQuality.collectAsState()
    val wordCount by viewModel.wordCount.collectAsState()
    val useLocation by viewModel.useLocation.collectAsState()
    val vanityEnabled by viewModel.vanityEnabled.collectAsState()
    val vanityPrefix by viewModel.vanityPrefix.collectAsState()
    val context = LocalContext.current
    val locationClient = remember {
        try {
            LocationServices.getFusedLocationProviderClient(context)
        } catch (e: Exception) {
            null
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val client = locationClient
        if (client == null) {
            viewModel.startRecording(context)
            return@rememberLauncherForActivityResult
        }
        client.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null && granted) {
                    viewModel.startRecording(context, loc.latitude, loc.longitude)
                } else {
                    viewModel.startRecording(context)
                }
            }
            .addOnFailureListener { viewModel.startRecording(context) }
    }

    fun proceedWithRecording() {
        if (!useLocation || locationClient == null) {
            viewModel.startRecording(context)
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        locationClient!!.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    viewModel.startRecording(context, loc.latitude, loc.longitude)
                } else {
                    viewModel.startRecording(context)
                }
            }
            .addOnFailureListener { viewModel.startRecording(context) }
    }

    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            viewModel.setPermissionDenied()
            return@rememberLauncherForActivityResult
        }
        proceedWithRecording()
    }

    fun onStartRecordingClick() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        proceedWithRecording()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SOUND AS SEED", style = MaterialTheme.typography.titleMedium) },
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
                .padding(horizontal = Spacing.md.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(Spacing.md.dp))

            Text(
                text = "Generate a seed phrase from the sounds around you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.lg.dp))

            when (val s = state) {
                is SoundSeedState.Idle -> {
                    /* Radiating rings: dormant, showing ready state */
                    RadiatingRingsAnimation(isActive = false, size = 140.dp)
                    Spacer(modifier = Modifier.height(Spacing.lg.dp))

                    /* Word count selector */
                    Text("WORD COUNT", style = LabelUppercaseStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        listOf(12, 24).forEach { count ->
                            Button(
                                onClick = { viewModel.setWordCount(count) },
                                modifier = Modifier.padding(horizontal = 8.dp),
                                colors = if (wordCount == count) {
                                    androidx.compose.material3.ButtonDefaults.buttonColors()
                                } else {
                                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                                }
                            ) {
                                Text("$count WORDS")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))

                    /* Location toggle */
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = useLocation,
                            onCheckedChange = { viewModel.setUseLocation(it) }
                        )
                        Text(
                            "Mix GPS location into entropy",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))

                    /* Vanity address: optional prefix/suffix for Solana address */
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = vanityEnabled,
                            onCheckedChange = { viewModel.setVanityEnabled(it) }
                        )
                        Text(
                            "Search for vanity address",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (vanityEnabled) {
                        Spacer(modifier = Modifier.height(Spacing.xs.dp))
                        OutlinedTextField(
                            value = vanityPrefix,
                            onValueChange = { newValue -> viewModel.setVanityPrefix(newValue) },
                            label = { Text("Prefix (e.g. solana)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("solana") }
                        )
                        Text(
                            text = "Address will start with this (case-sensitive). Longer = slower search.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.lg.dp))

                    Button(
                        onClick = { onStartRecordingClick() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("START RECORDING (10s)")
                    }
                }

                is SoundSeedState.Recording -> {
                    /* Active recording with radiating rings and entropy meter */
                    RadiatingRingsAnimation(isActive = true, size = 140.dp)
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))

                    Text(
                        "Recording… ${s.elapsedSeconds}s / 10s",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.md.dp))

                    /* Entropy quality meter */
                    EntropyQualityMeter(quality = s.quality)
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Text(
                        "Entropy quality: ${s.quality}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Text(
                        "Move your device, talk, play music — diverse sounds produce better entropy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is SoundSeedState.Processing -> {
                    RadiatingRingsAnimation(isActive = true, size = 140.dp)
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    StatusBar(status = "Extracting entropy…", isActive = true)
                }

                is SoundSeedState.Success -> {
                    StatusBar(status = "Seed generated from sound")
                    Spacer(modifier = Modifier.height(Spacing.md.dp))

                    /* Show final entropy quality from recording session */
                    if (quality > 0) {
                        EntropyQualityMeter(quality = quality)
                        Spacer(modifier = Modifier.height(Spacing.xs.dp))
                        Text(
                            "Final entropy quality: ${quality}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Spacing.md.dp))
                    }

                    ShowSeedStep(seedPhrase = s.mnemonic)
                    Spacer(modifier = Modifier.height(Spacing.md.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "This seed was generated from ambient sound + SecureRandom. " +
                                    "It is cryptographically valid and unique.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(Spacing.sm.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.md.dp))

                    /* Use for backup: when navigated from Backup flow */
                    if (onUseForBackup != null) {
                        Button(
                            onClick = { onUseForBackup(s.mnemonic) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("USE FOR BACKUP")
                        }
                        Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    }

                    Button(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (onUseForBackup != null)
                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                        else
                            androidx.compose.material3.ButtonDefaults.buttonColors()
                    ) {
                        Text("GENERATE ANOTHER")
                    }
                }

                is SoundSeedState.Error -> {
                    StatusBar(status = s.message)
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Button(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("TRY AGAIN")
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg.dp))
        }
    }
}

/**
 * Horizontal bar showing entropy quality from 0 (red) to 100 (green).
 */
@Composable
private fun EntropyQualityMeter(
    quality: Int,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = quality / 100f,
        animationSpec = tween(300),
        label = "entropy_progress"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
    ) {
        // Background track
        drawRoundRect(
            color = Color.Gray.copy(alpha = 0.2f),
            size = size
        )

        // Filled portion with color gradient from red -> yellow -> green
        val fillColor = when {
            quality < 30 -> Color(0xFFE53935) // red
            quality < 60 -> Color(0xFFFFA726) // amber
            else -> Color(0xFF4CAF50)          // green
        }
        drawRoundRect(
            color = fillColor,
            size = Size(size.width * animatedProgress, size.height)
        )
    }
}
