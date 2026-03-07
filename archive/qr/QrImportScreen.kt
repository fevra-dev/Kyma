package com.sonicvault.app.ui.screen.qr

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.sonicvault.app.data.crypto.EncryptedPayload
import com.sonicvault.app.data.crypto.PasswordSeedVaultCrypto
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.ui.component.StatusBar
import com.sonicvault.app.ui.screen.recovery.ShowSeedStep
import com.sonicvault.app.ui.screen.recovery.copyToClipboard
import com.sonicvault.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Base64

/**
 * Import and decrypt a QR code backup image.
 * Flow: pick image -> decode QR -> parse JSON -> enter password -> decrypt -> show seed.
 * Inverse of QrExportScreen. Rams: useful, honest, understandable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrImportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }

    var qrPayload by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var decryptedSeed by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isDecrypting by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                errorMessage = null
                qrPayload = null
                decryptedSeed = null
                val decoded = withContext(Dispatchers.Default) {
                    decodeQrFromImage(context, uri)
                }
                if (decoded != null) {
                    qrPayload = decoded
                    SonicVaultLogger.i("[QrImport] QR decoded successfully")
                } else {
                    errorMessage = "Could not read QR code from image."
                    SonicVaultLogger.w("[QrImport] QR decode failed")
                }
            }
        }
    }

    /** Auto-open picker on first mount so user goes straight to file selection. */
    var hasOpenedPicker by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasOpenedPicker) {
            hasOpenedPicker = true
            imagePicker.launch("image/*")
        }
    }

    fun decrypt() {
        val payload = qrPayload ?: return
        if (password.length < 8) {
            errorMessage = "Password must be at least 8 characters"
            return
        }
        errorMessage = null
        isDecrypting = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                try {
                    val json = JSONObject(payload)
                    val version = json.optInt("v", 0)
                    if (version != 1) return@withContext null
                    val c = json.getString("c")
                    val raw = Base64.getDecoder().decode(c)
                    /* Parse: salt(16 or 17) + iv(12) + ciphertext. Detect PBKDF2 marker. */
                    val saltSize = if (raw.size > 17 && raw[0] == 1.toByte()) 17 else 16
                    if (raw.size < saltSize + 12 + 1) return@withContext null
                    val salt = raw.copyOfRange(0, saltSize)
                    val iv = raw.copyOfRange(saltSize, saltSize + 12)
                    val ciphertext = raw.copyOfRange(saltSize + 12, raw.size)
                    val encPayload = EncryptedPayload(iv = iv, ciphertextWithTag = ciphertext, salt = salt)
                    PasswordSeedVaultCrypto.decryptWithPassword(encPayload, password)
                } catch (e: Exception) {
                    SonicVaultLogger.e("[QrImport] decrypt failed", e)
                    null
                }
            }
            isDecrypting = false
            if (result != null) {
                decryptedSeed = String(result, Charsets.UTF_8)
                result.fill(0)
                view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM)
                SonicVaultLogger.i("[QrImport] seed decrypted successfully")
            } else {
                errorMessage = "Decryption failed. Check your password."
                view.performHapticFeedback(HapticFeedbackConstantsCompat.REJECT)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("IMPORT QR", style = MaterialTheme.typography.titleMedium) },
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
            when {
                /* Seed successfully recovered */
                decryptedSeed != null -> {
                    StatusBar(status = "Verified")
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    ShowSeedStep(seedPhrase = decryptedSeed!!)
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    OutlinedButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM)
                            copyToClipboard(context, "Kyma", decryptedSeed!!)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Seed copied — auto-clears in 60s",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("COPY TO CLIPBOARD")
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    Button(
                        onClick = { onBack() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("DONE")
                    }
                }

                /* QR decoded, waiting for password */
                qrPayload != null -> {
                    StatusBar(status = "QR decoded — enter password to decrypt")
                    Spacer(modifier = Modifier.height(Spacing.sm.dp))
                    Text(
                        text = "This QR contains an encrypted seed. Enter the password used during export.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(Spacing.sm.dp))
                        StatusBar(status = errorMessage!!)
                    }
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Button(
                        onClick = { decrypt() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isDecrypting && password.isNotBlank()
                    ) {
                        if (isDecrypting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.size(Spacing.sm.dp))
                        }
                        Text(if (isDecrypting) "DECRYPTING…" else "DECRYPT")
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs.dp))
                    OutlinedButton(
                        onClick = {
                            qrPayload = null
                            password = ""
                            errorMessage = null
                            imagePicker.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SELECT ANOTHER IMAGE")
                    }
                }

                /* Initial state: pick image */
                else -> {
                    Text(
                        text = "Select a QR code image exported from Kyma to recover your seed phrase.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(Spacing.sm.dp))
                        StatusBar(status = errorMessage!!)
                    }
                    Spacer(modifier = Modifier.height(Spacing.md.dp))
                    Button(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SELECT QR IMAGE")
                    }
                }
            }
        }
    }
}

/**
 * Decodes a QR code from an image Uri using ZXing MultiFormatReader.
 * @return The decoded text content, or null if no QR found.
 */
private fun decodeQrFromImage(context: android.content.Context, uri: Uri): String? {
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        /**
         * Downsample large images to avoid OOM on high-res photos.
         * QR codes decode fine at 1024px; no need for full 4000x3000 bitmaps.
         */
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        input.use { BitmapFactory.decodeStream(it, null, opts) }
        val maxDim = maxOf(opts.outWidth, opts.outHeight)
        val sampleSize = if (maxDim > 2048) (maxDim / 1024) else 1
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val input2 = context.contentResolver.openInputStream(uri) ?: return null
        val bitmap = input2.use { BitmapFactory.decodeStream(it, null, decodeOpts) } ?: return null
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val result = MultiFormatReader().decode(binaryBitmap)
        result.text
    } catch (e: Throwable) {
        SonicVaultLogger.w("[QrImport] decode error: ${e.message}")
        null
    }
}
