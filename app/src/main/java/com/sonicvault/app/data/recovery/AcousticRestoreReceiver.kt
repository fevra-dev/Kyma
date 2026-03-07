package com.sonicvault.app.data.recovery

import android.content.Context
import com.sonicvault.app.data.sound.AcousticChunkReceiver
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Receives chunked backup payload via acoustic transmission.
 *
 * Reuses AcousticChunkReceiver for chunk protocol. Emits reassembled payload bytes
 * when a complete chunk set is received. Caller decrypts via BackupRepository.
 */
object AcousticRestoreReceiver {

    /**
     * Flow that emits reassembled backup payload when received.
     * No session filter: accept any chunked backup transmission.
     *
     * @param context Application or Activity context for AudioRecord source selection
     */
    fun receiveFlow(context: Context): Flow<ByteArray> =
        AcousticChunkReceiver.receiveFlow(context, sessionIdFilter = null)
            .onEach { SonicVaultLogger.i("[AcousticRestoreReceiver] received ${it.size} bytes") }
}
