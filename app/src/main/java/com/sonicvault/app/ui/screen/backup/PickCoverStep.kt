package com.sonicvault.app.ui.screen.backup

import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonicvault.app.ui.component.CardSection
import com.sonicvault.app.ui.theme.LabelUppercaseStyle
import com.sonicvault.app.ui.theme.Spacing

/**
 * Cover audio picker: upload a file or record in-app with selectable duration.
 * Rams: useful (clear purpose), understandable (duration options are self-explanatory),
 * honest (waveform shows real recording feedback, filename confirms selection).
 */
@Composable
fun PickCoverStep(
    selectedUri: Uri?,
    isRecording: Boolean = false,
    recordingElapsedSeconds: Int = 0,
    onPickCover: () -> Unit,
    onRecordCover: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    /** Minimum 5s before STOP is enabled (ggwave compatibility). */
    val minRecordingSeconds = 5

    CardSection(modifier = modifier) {
        Text(
            text = "COVER AUDIO",
            style = LabelUppercaseStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.xs.dp))
        Text(
            text = if (selectedUri == null)
                "Select an audio file or record in-app."
            else
                "Change or re-record below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.sm.dp))

        /* Upload + Record buttons; Play/Pause when file selected. Rams: feedback for every action. */
        val context = LocalContext.current
        val player = remember { mutableStateOf<MediaPlayer?>(null) }
        var isPlaying by remember { mutableStateOf(false) }
        DisposableEffect(Unit) {
            onDispose { player.value?.release(); player.value = null }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onPickCover,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                enabled = !isRecording,
                shape = RectangleShape
            ) {
                Icon(
                    Icons.Filled.UploadFile,
                    contentDescription = if (selectedUri != null) "Change file" else "Select file",
                    modifier = Modifier.height(20.dp)
                )
            }
            if (selectedUri != null && !isRecording) {
                if (isPlaying) {
                    IconButton(
                        onClick = {
                            player.value?.let { p ->
                                try { p.pause(); isPlaying = false } catch (_: Exception) { }
                            }
                        },
                        modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause")
                    }
                } else {
                    IconButton(
                        onClick = {
                            player.value?.let { p ->
                                try { p.start(); isPlaying = true; return@IconButton } catch (_: Exception) { }
                            }
                            player.value?.release()
                            player.value = MediaPlayer().apply {
                                setDataSource(context, selectedUri)
                                prepare()
                                setOnCompletionListener { release(); player.value = null; isPlaying = false }
                                start()
                                isPlaying = true
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    try { if (isPlaying) { stop(); release() } } catch (_: Exception) { }
                                    player.value = null
                                    isPlaying = false
                                }, 10_000)
                            }
                        },
                        modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play preview")
                    }
                }
            }
            if (isRecording && recordingElapsedSeconds >= minRecordingSeconds) {
                OutlinedButton(
                    onClick = onStopRecording,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    shape = RectangleShape
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop recording", modifier = Modifier.height(20.dp))
                }
            } else {
                OutlinedButton(
                    onClick = onRecordCover,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    enabled = !isRecording,
                    shape = RectangleShape
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = if (isRecording) "Recording… ${recordingElapsedSeconds}s" else "Record audio",
                        modifier = Modifier.height(20.dp)
                    )
                }
            }
        }

        if (isRecording && recordingElapsedSeconds < minRecordingSeconds) {
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            Text(
                text = "Recording… ${recordingElapsedSeconds}s (min ${minRecordingSeconds}s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        /* Show selected file info. ContentResolver DISPLAY_NAME often null for content:// URIs; try path segments as fallback. */
        if (selectedUri != null && !isRecording) {
            Spacer(modifier = Modifier.height(Spacing.xs.dp))
            val displayName = remember(selectedUri) {
                context.contentResolver.query(selectedUri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && cursor.moveToFirst()) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }
                    } else null
                } ?: selectedUri.pathSegments?.lastOrNull()?.let { seg ->
                    if (seg.contains(".")) seg else null
                } ?: selectedUri.lastPathSegment?.substringAfterLast("/")?.takeIf { it.isNotBlank() }
            }
            Text(
                text = displayName ?: "Audio file selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }

    }
}
