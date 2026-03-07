package com.sonicvault.app.domain.model

/**
 * Backup flow state machine: IDLE -> VALIDATING -> ENCRYPTING -> EMBEDDING -> WRITING_FILE -> SUCCESS | ERROR
 */
sealed class BackupState {
    data object Idle : BackupState()
    data object Validating : BackupState()
    data object Encrypting : BackupState()
    data object Embedding : BackupState()
    data object WritingFile : BackupState()
    data class Success(
        val stegoUri: android.net.Uri,
        val checksum: String? = null,
        val fingerprint: android.graphics.Bitmap? = null,
        val shortId: String? = null
    ) : BackupState()
    data class Error(val message: String) : BackupState()
}
