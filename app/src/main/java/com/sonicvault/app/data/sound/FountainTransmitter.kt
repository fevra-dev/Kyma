package com.sonicvault.app.data.sound

import android.media.AudioFormat
import android.media.AudioTrack
import com.sonicvault.app.domain.model.Protocol
import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Transmits payload as LT fountain droplets via ggwave.
 *
 * Each droplet: [0xFC][seed:2][data:16] = 19 bytes.
 * Loops until cancelled or 2x symbol count reached.
 */
object FountainTransmitter {

    private val FOUNTAIN_MAGIC: Byte = 0xFC.toByte()

    /**
     * Transmits payload as fountain droplets.
     *
     * @param payload Encrypted bytes to transmit (will be fountain-encoded)
     * @param protocol ggwave protocol (ULTRASONIC recommended)
     * @param applyFingerprintRandomization when true and protocol is Ultrasonic, randomizes spectral envelope
     * @param onDropletSent Optional callback (droplet index)
     */
    suspend fun transmitFountain(
        payload: ByteArray,
        protocol: Protocol,
        applyFingerprintRandomization: Boolean = false,
        onDropletSent: ((Int) -> Unit)? = null
    ) {
        val codec = LtFountainCodec()
        val encoder = codec.createEncoder(payload)
        val targetDroplets = (encoder.symbolCount * 2).coerceAtLeast(15)
        var count = 0

        SonicVaultLogger.i("[FountainTransmitter] transmitting ${payload.size} bytes, target $targetDroplets droplets")
        while (coroutineContext.isActive && count < targetDroplets) {
            val droplet = encoder.nextDroplet()
            val packet = byteArrayOf(FOUNTAIN_MAGIC) + droplet
            val pcm = withContext(Dispatchers.IO) {
                GgwaveDataOverSound.encode(packet, protocol, applyFingerprintRandomization = applyFingerprintRandomization)
            }
            if (pcm != null) {
                playSamples(pcm)
                count++
                onDropletSent?.invoke(count)
            }
            delay(100)
        }
        SonicVaultLogger.i("[FountainTransmitter] transmitted $count droplets")
    }

    private suspend fun playSamples(samples: ShortArray) = withContext(Dispatchers.IO) {
        val bufferSize = AudioTrack.getMinBufferSize(
            GgwaveDataOverSound.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(GgwaveDataOverSound.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(bufferSize, samples.size * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            track.play()
            track.write(samples, 0, samples.size)
            val durationMs = samples.size * 1000L / GgwaveDataOverSound.SAMPLE_RATE
            delay(durationMs + 200)
            track.stop()
        } finally {
            track.release()
        }
    }
}
