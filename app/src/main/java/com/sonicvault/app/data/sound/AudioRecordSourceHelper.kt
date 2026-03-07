package com.sonicvault.app.data.sound

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import com.sonicvault.app.logging.SonicVaultLogger

/**
 * Selects the best AudioRecord source for ultrasonic reception.
 *
 * DEFAULT/MIC applies AGC which destroys ultrasonic. Use UNPROCESSED (no AGC) or
 * VOICE_RECOGNITION (no AGC, no noise suppression) for ggwave ultrasonic decode.
 *
 * Logs which source is used for debugging and security checklist verification.
 */
object AudioRecordSourceHelper {

    /**
     * Returns the best audio source for ultrasonic reception.
     * UNPROCESSED if supported, else VOICE_RECOGNITION. Never DEFAULT for ultrasonic.
     *
     * @param context Application or Activity context (for AudioManager)
     * @return MediaRecorder.AudioSource value
     */
    fun getAudioSourceForUltrasonic(context: Context): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: run {
                SonicVaultLogger.w("[AudioRecord] AudioManager unavailable, using VOICE_RECOGNITION")
                return MediaRecorder.AudioSource.VOICE_RECOGNITION
            }

        val supportsNearUltrasound = audioManager.getProperty(
            AudioManager.PROPERTY_SUPPORT_MIC_NEAR_ULTRASOUND
        )?.let { it == "true" } ?: false

        val supportsUnprocessed = audioManager.getProperty(
            AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED
        )?.let { it == "true" } ?: false

        val source = when {
            supportsUnprocessed -> {
                SonicVaultLogger.i("[AudioRecord] source=UNPROCESSED, nearUltrasound=$supportsNearUltrasound")
                MediaRecorder.AudioSource.UNPROCESSED
            }
            else -> {
                SonicVaultLogger.i("[AudioRecord] source=VOICE_RECOGNITION (UNPROCESSED unavailable), nearUltrasound=$supportsNearUltrasound")
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            }
        }
        return source
    }
}
