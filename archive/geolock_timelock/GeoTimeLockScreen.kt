package com.sonicvault.app.ui.screen.lock

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.sonicvault.app.data.geolock.GeoKeyDerivation
import com.sonicvault.app.data.geolock.GpsSpoofingDetector
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * GeoTimeLock configuration screen.
 * ARCHIVED: See archive/geolock_timelock/README.md
 */
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoTimeLockScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var enableGeolock by remember { mutableStateOf(false) }
    var enableTimelock by remember { mutableStateOf(true) }
    var showDatePicker by remember { mutableStateOf(false) }
    var gpsStatus by remember { mutableStateOf("Not acquired") }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var spoofWarning by remember { mutableStateOf<String?>(null) }
    var quantizedDisplay by remember { mutableStateOf<String?>(null) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000
    )
    val locationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (!granted) {
            gpsStatus = "Permission denied"
            SonicVaultLogger.w("[GeoTimeLock] location permission denied")
        }
    }

    LaunchedEffect(enableGeolock, hasLocationPermission) {
        if (!enableGeolock) return@LaunchedEffect
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return@LaunchedEffect
        }
        gpsStatus = "Acquiring…"
        SonicVaultLogger.i("[GeoTimeLock] requesting current location")
        try {
            val cts = CancellationTokenSource()
            locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        latitude = location.latitude
                        longitude = location.longitude
                        gpsStatus = "Acquired"
                        val (qLat, qLon) = GeoKeyDerivation.quantize(location.latitude, location.longitude)
                        quantizedDisplay = "Grid: ${String.format("%.3f", qLat)}, ${String.format("%.3f", qLon)}"
                        val spoofResult = GpsSpoofingDetector.check(context, location)
                        spoofWarning = if (spoofResult.isSuspicious) {
                            "Spoofing detected (confidence: ${spoofResult.confidence}%). " +
                                    "Signals: ${spoofResult.signals.joinToString(", ")}"
                        } else null
                        SonicVaultLogger.i("[GeoTimeLock] location acquired lat=${String.format("%.4f", location.latitude)} " +
                                "lon=${String.format("%.4f", location.longitude)} spoofing=${spoofResult.isSuspicious}")
                    } else {
                        gpsStatus = "Location unavailable. Enable GPS."
                        SonicVaultLogger.w("[GeoTimeLock] getCurrentLocation returned null")
                    }
                }
                .addOnFailureListener { e ->
                    gpsStatus = "GPS failed: ${e.message}"
                    SonicVaultLogger.e("[GeoTimeLock] location request failed", e)
                }
        } catch (e: Exception) {
            gpsStatus = "GPS error: ${e.message}"
            SonicVaultLogger.e("[GeoTimeLock] unexpected GPS error", e)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GEOLOCK + TIMELOCK", style = MaterialTheme.typography.titleMedium) },
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
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(Spacing.md.dp))
            Text(
                text = "Lock your backup to a physical location and/or date. " +
                        "The backup can only be decrypted at the right place, at the right time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.lg.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { enableGeolock = !enableGeolock },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = enableGeolock, onCheckedChange = { enableGeolock = it })
                Column(modifier = Modifier.weight(1f)) {
                    Text("Location lock", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Only decrypt at this GPS location (~100m)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AnimatedVisibility(visible = enableGeolock, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(start = 48.dp)) {
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(modifier = Modifier.padding(Spacing.sm.dp)) {
                            Text("GPS STATUS", style = LabelUppercaseStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(Spacing.xs.dp))
                            Text(
                                if (latitude != null) "Acquired: ${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}"
                                else gpsStatus,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (quantizedDisplay != null) {
                                Text(quantizedDisplay!!, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Spacing.xs.dp))
                            }
                        }
                    }
                    if (spoofWarning != null) {
                        Spacer(modifier = Modifier.height(Spacing.xs.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(spoofWarning!!, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(Spacing.sm.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Text(
                        "GPS coordinates are quantized to ~100m grid. Your exact position is not stored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.md.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { enableTimelock = !enableTimelock },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = enableTimelock, onCheckedChange = { enableTimelock = it })
                Column(modifier = Modifier.weight(1f)) {
                    Text("Time lock", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Only decrypt after a specific date",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AnimatedVisibility(visible = enableTimelock, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(start = 48.dp)) {
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    ) {
                        Text(
                            datePickerState.selectedDateMillis?.let { ts ->
                                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ts))
                            } ?: "Pick unlock date"
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Text(
                        "Time verified via NTP. Prevents clock manipulation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } },
                    dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
                ) {
                    DatePicker(state = datePickerState)
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xl.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Recovery will show specific errors:\n" +
                            "• Wrong location: \"You are not at the unlock location.\"\n" +
                            "• Too early: \"Unlocks on [date].\"\n" +
                            "• Both: \"Wrong location AND too early.\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(Spacing.sm.dp)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md.dp))
            Button(
                onClick = { onBack() },
                modifier = Modifier.fillMaxWidth(),
                enabled = enableGeolock || enableTimelock
            ) {
                Text("APPLY LOCK")
            }
            Spacer(modifier = Modifier.height(Spacing.lg.dp))
        }
    }
}
