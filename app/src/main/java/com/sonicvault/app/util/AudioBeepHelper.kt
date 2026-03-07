package com.sonicvault.app.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import com.sonicvault.app.logging.SonicVaultLogger

/**
 * Plays short audible beeps for transmit start/end confirmation.
 * Callers decide when to play based on the selected protocol.
 * Runs on calling thread; call from Dispatchers.IO to avoid blocking UI.
 */
object AudioBeepHelper {

    private const val BEEP_DURATION_MS = 150

    /** Plays a short beep (440 Hz) using ToneGenerator. */
    fun playBeep(context: Context) {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS)
            Thread.sleep(BEEP_DURATION_MS.toLong())
            toneGen.release()
            SonicVaultLogger.d("[AudioBeepHelper] Beep played")
        } catch (e: Exception) {
            SonicVaultLogger.w("[AudioBeepHelper] Beep failed: ${e.message}")
        }
    }

    /** Plays two short beeps (start/end confirmation) with a brief pause. */
    fun playDoubleBeep(context: Context) {
        playBeep(context)
        Thread.sleep(100)
        playBeep(context)
    }
}
