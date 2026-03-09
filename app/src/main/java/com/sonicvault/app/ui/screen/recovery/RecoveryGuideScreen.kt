package com.sonicvault.app.ui.screen.recovery

import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sonicvault.app.ui.theme.Spacing
import com.sonicvault.app.ui.theme.TouchTargetMin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Recovery guide as readable document. Buttons: Share, Save as .md, Save as PDF.
 * Rams: useful, understandable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val content = remember {
        runCatching {
            context.assets.open("recovery_checklist.md").bufferedReader().use { it.readText() }
        }.getOrNull() ?: "Recovery guide not available."
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("RECOVERY GUIDE", style = MaterialTheme.typography.titleMedium) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.md.dp)
        ) {
            /* Readable document content — markdown-style formatting: headers, bold, dividers */
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                RecoveryGuideContent(
                    content = content,
                    bodyStyle = MaterialTheme.typography.bodyMedium,
                    titleStyle = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md.dp))
            /* Action buttons */
            OutlinedButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Kyma Recovery Checklist")
                        putExtra(Intent.EXTRA_TEXT, content)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share recovery guide"))
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                shape = RectangleShape
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Share recovery guide", modifier = Modifier.height(20.dp))
                Spacer(modifier = Modifier.width(Spacing.xs.dp))
                Text("SHARE")
            }
            Spacer(modifier = Modifier.height(Spacing.sm.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            saveAsMd(context, content)
                        }
                        snackbarHostState.showSnackbar(
                            if (result != null) "Saved as $result" else "Could not save file",
                            duration = SnackbarDuration.Short
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                shape = RectangleShape
            ) {
                Icon(Icons.Filled.Download, contentDescription = "Download as Markdown", modifier = Modifier.height(20.dp))
                Spacer(modifier = Modifier.width(Spacing.xs.dp))
                Text("DOWNLOAD AS .MD")
            }
            Spacer(modifier = Modifier.height(Spacing.sm.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            saveAsPdf(context, content)
                        }
                        snackbarHostState.showSnackbar(
                            if (result != null) "Saved as $result" else "Could not save PDF",
                            duration = SnackbarDuration.Short
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                shape = RectangleShape
            ) {
                Icon(Icons.Filled.Download, contentDescription = "Download as PDF", modifier = Modifier.height(20.dp))
                Spacer(modifier = Modifier.width(Spacing.xs.dp))
                Text("DOWNLOAD AS PDF")
            }
        }
    }
}

/**
 * Parses markdown-style bold (**text**) into [AnnotatedString] for display.
 * Used so the recovery guide shows bold labels instead of raw "***" in the UI.
 */
private fun parseBoldToAnnotatedString(line: String): AnnotatedString {
    val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
    return buildAnnotatedString {
        var lastEnd = 0
        for (match in boldRegex.findAll(line)) {
            append(line.substring(lastEnd, match.range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            lastEnd = match.range.last + 1
        }
        append(line.substring(lastEnd))
    }
}

/**
 * Renders recovery guide content with markdown-style formatting:
 * # headers as title, **bold** as bold text, --- as horizontal divider.
 * Raw *** and ** are stripped from display so the guide reads cleanly.
 */
@Composable
private fun RecoveryGuideContent(
    content: String,
    bodyStyle: TextStyle,
    titleStyle: TextStyle,
    color: Color,
    onSurfaceVariant: Color
) {
    val lines = content.split("\n")
    for (i in lines.indices) {
        val line = lines[i]
        when {
            line.trim() == "---" -> {
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.xs.dp),
                    color = onSurfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
            }
            line.startsWith("# ") -> {
                if (i > 0) Spacer(modifier = Modifier.height(Spacing.md.dp))
                Text(
                    text = parseBoldToAnnotatedString(line.removePrefix("# ")),
                    style = titleStyle,
                    color = color
                )
                Spacer(modifier = Modifier.height(Spacing.xs.dp))
            }
            line.isBlank() -> {
                Spacer(modifier = Modifier.height(Spacing.xs.dp))
            }
            else -> {
                Text(
                    text = parseBoldToAnnotatedString(line),
                    style = bodyStyle,
                    color = color
                )
                Spacer(modifier = Modifier.height(Spacing.xs.dp))
            }
        }
    }
}

/** Saves content to Downloads as .md. Returns filename or null. */
private fun saveAsMd(context: android.content.Context, content: String): String? {
    return try {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "Kyma_Recovery_Guide.md")
                    put(MediaStore.Downloads.MIME_TYPE, "text/markdown")
                }
            )
            uri?.let {
                resolver.openOutputStream(it)?.use { out -> out.write(content.toByteArray()) }
                "Kyma_Recovery_Guide.md"
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, "Kyma_Recovery_Guide.md")
            file.writeText(content)
            file.name
        }
    } catch (e: Exception) {
        null
    }
}

/** Saves content to Downloads as PDF. Returns filename or null. */
private fun saveAsPdf(context: android.content.Context, content: String): String? {
    val pdf = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = pdf.startPage(pageInfo)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 12f
    }
    val lines = content.split("\n")
    var y = 40f
    for (line in lines) {
        if (y > 800f) break
        page.canvas.drawText(line, 40f, y, paint)
        y += 18f
    }
    pdf.finishPage(page)
    return try {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "Kyma_Recovery_Guide.pdf")
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                }
            )
            uri?.let {
                resolver.openOutputStream(it)?.use { out -> pdf.writeTo(out) }
                pdf.close()
                "Kyma_Recovery_Guide.pdf"
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, "Kyma_Recovery_Guide.pdf")
            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()
            file.name
        }
    } catch (e: Exception) {
        null
    }
}
