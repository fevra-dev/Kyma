package com.sonicvault.app.ui.screen.qr

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.sonicvault.app.data.crypto.EncryptedPayload
import com.sonicvault.app.data.crypto.PasswordSeedVaultCrypto
import com.sonicvault.app.util.wipe
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.ui.component.SeedInputCard
import com.sonicvault.app.ui.component.StatusBar
import com.sonicvault.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Base64

/**
 * Export seed as encrypted QR code. Payload: {"v":1,"c":"base64..."}.
 * User enters seed + password; encrypted with Argon2id + AES-GCM.
 * Display QR with Save Image / Print options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrExportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var seedPhrase by remember { mutableStateOf("") }
    var showPhrase by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    /** Eye toggles for password visibility. */
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordConfirmVisible by remember { mutableStateOf(false) }
    /** Controls the fullscreen QR overlay dialog. Rams: show result immediately, clearly. */
    var showQrDialog by remember { mutableStateOf(false) }

    val saveImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null && qrBitmap != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                qrBitmap!!.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }
    val savePdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && qrBitmap != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                writeQrToPdf(qrBitmap!!, out)
            }
        }
    }

    /** Runs encryption off the main thread to prevent UI jank (Argon2id / PBKDF2 is CPU-heavy). */
    fun generateQr() {
        errorMessage = null
        when {
            seedPhrase.isBlank() -> errorMessage = "Enter seed phrase"
            password.length < 8 -> errorMessage = "Password must be at least 8 characters"
            password != passwordConfirm -> errorMessage = "Passwords do not match"
            else -> {
                isGenerating = true
                val trimmedSeed = seedPhrase.trim()
                val pw = password
                scope.launch {
                    val plaintext = trimmedSeed.toByteArray(Charsets.UTF_8)
                    try {
                        val result = withContext(Dispatchers.Default) {
                            val payload = PasswordSeedVaultCrypto.encryptWithPassword(plaintext, pw)
                                ?: return@withContext null
                            val c = encodePayloadToBase64(payload)
                            val json = JSONObject().apply {
                                put("v", 1)
                                put("c", c)
                            }.toString()
                            generateQrBitmap(json, 512)
                        }
                        if (result != null) {
                            qrBitmap = result
                            showQrDialog = true
                            SonicVaultLogger.i("[QrExport] QR generated successfully")
                        } else {
                            errorMessage = "Encryption or QR generation failed"
                        }
                    } finally {
                        plaintext.wipe()
                    }
                    isGenerating = false
                }
            }
        }
    }

    /**
     * Fullscreen QR overlay dialog — shows QR code with all save/share/print actions.
     * No scrolling needed. Rams: useful, understandable, as little design as possible.
     */
    if (showQrDialog && qrBitmap != null) {
        Dialog(
            onDismissRequest = { showQrDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(Spacing.md.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "YOUR QR CODE",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { showQrDialog = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Image(
                    bitmap = qrBitmap!!.asImageBitmap(),
                    contentDescription = "QR code",
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .aspectRatio(1f)
                )
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                Text(
                    text = "Scan with Kyma + your password to recover.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)
                ) {
                    OutlinedButton(
                        onClick = { saveImageLauncher.launch("backup_qr.png") },
                        modifier = Modifier.weight(1f)
                    ) { Text("IMAGE") }
                    OutlinedButton(
                        onClick = { savePdfLauncher.launch("backup_qr.pdf") },
                        modifier = Modifier.weight(1f)
                    ) { Text("PDF") }
                }
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val activity = context as? androidx.fragment.app.FragmentActivity
                            if (activity != null) {
                                androidx.print.PrintHelper(activity).apply {
                                    scaleMode = androidx.print.PrintHelper.SCALE_MODE_FIT
                                }.printBitmap("QR Backup", qrBitmap!!)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("PRINT") }
                    OutlinedButton(
                        onClick = {
                            cleanupQrCacheFiles(context)
                            val file = File(context.cacheDir, "sonicvault_qr_${System.currentTimeMillis()}.png")
                            qrBitmap!!.compress(Bitmap.CompressFormat.PNG, 100, file.outputStream())
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share via"))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("SHARE") }
                }
                Spacer(modifier = Modifier.height(Spacing.md.dp))
                Button(
                    onClick = { showQrDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("DONE") }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EXPORT AS QR", style = MaterialTheme.typography.titleMedium) },
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
                .padding(Spacing.md.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Encrypt your seed with a password and export as QR. Store securely; scan with Kyma to recover.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.md.dp))
            SeedInputCard(
                seedPhrase = seedPhrase,
                onSeedPhraseChange = { seedPhrase = it },
                showPhrase = showPhrase,
                onShowPhraseChange = { showPhrase = it }
            )
            Spacer(modifier = Modifier.height(Spacing.md.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(modifier = Modifier.height(Spacing.sm.dp))
            OutlinedTextField(
                value = passwordConfirm,
                onValueChange = { passwordConfirm = it },
                label = { Text("Confirm password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordConfirmVisible = !passwordConfirmVisible }) {
                        Icon(
                            imageVector = if (passwordConfirmVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordConfirmVisible) "Hide password" else "Show password"
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(modifier = Modifier.height(Spacing.md.dp))
            if (errorMessage != null) {
                StatusBar(status = errorMessage!!)
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
            }
            Button(
                onClick = { generateQr() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isGenerating
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.size(Spacing.sm.dp))
                    }
                    Text(if (isGenerating) "GENERATING…" else "GENERATE QR")
                }
            }
        }
    }
}

/** Writes QR bitmap to a single-page PDF. */
private fun writeQrToPdf(bitmap: Bitmap, out: java.io.OutputStream) {
    val doc = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
    val page = doc.startPage(pageInfo)
    page.canvas.drawBitmap(bitmap, 0f, 0f, Paint().apply { isAntiAlias = true })
    doc.finishPage(page)
    doc.writeTo(out)
    doc.close()
}

/** Serializes EncryptedPayload to base64: salt(16) || iv(12) || ciphertext. */
private fun encodePayloadToBase64(payload: EncryptedPayload): String {
    val salt = payload.salt ?: throw IllegalArgumentException("Password payload requires salt")
    val bytes = salt + payload.iv + payload.ciphertextWithTag
    return Base64.getEncoder().encodeToString(bytes)
}

/**
 * SECURITY PATCH [SVA-008]: Securely deletes stale QR cache files from previous sessions.
 * Uses SecureFileDeleter to overwrite contents with random data before unlinking,
 * preventing forensic recovery of encrypted seed QR images.
 */
private fun cleanupQrCacheFiles(context: android.content.Context) {
    try {
        com.sonicvault.app.util.SecureFileDeleter.deleteMatching(
            directory = context.cacheDir,
            prefix = "sonicvault_qr_",
            suffix = ".png"
        )
    } catch (e: Exception) {
        SonicVaultLogger.w("[QrExport] cache cleanup failed: ${e.message}")
    }
}

private fun generateQrBitmap(text: String, sizePx: Int): Bitmap? {
    return try {
        val hints = hashMapOf<EncodeHintType, Any>(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        SonicVaultLogger.e("[QrExport] QR encode failed", e)
        null
    }
}

