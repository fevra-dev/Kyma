package com.sonicvault.app.data.sound

/**
 * JNI bridge to ggwave C++ library.
 * Handles encode (payload → PCM) and decode (PCM → payload) with chunking for
 * payloads exceeding ggwave's 140-byte limit.
 *
 * Loads libggwave_jni.so via System.loadLibrary.
 */
object GgwaveNative {

    init {
        System.loadLibrary("ggwave_jni")
    }

    /**
     * Creates a ggwave instance for the given sample rate.
     * @param useDss When true, enables Direct Sequence Spread for ultrasonic robustness (~20% better BER).
     *              Use false for Message/Solana Pay (web receiver compatibility).
     * @return Instance handle (>= 0) or -1 on failure
     */
    external fun init(sampleRate: Int, useDss: Boolean = false): Long

    /**
     * Frees a ggwave instance. No-op if handle < 0.
     */
    external fun free(instance: Long)

    /**
     * Encodes payload to 16-bit mono PCM.
     * Supports chunking for payloads > 140 bytes.
     *
     * @param instance ggwave instance handle.
     * @param payload Raw bytes to encode.
     * @param protocolId ggwave protocol ID (0=Audible, 3–5=Ultrasonic, 6–8=DT).
     * @return ShortArray of PCM samples, or null on failure.
     */
    external fun encode(instance: Long, payload: ByteArray, protocolId: Int): ShortArray?

    /**
     * Decodes 16-bit mono PCM to payload.
     * Reassembles chunked payloads.
     *
     * @param instance ggwave instance handle.
     * @param samples PCM samples (16-bit mono).
     * @param sampleRate Sample rate (must match recording).
     * @return Decoded payload, or null if no valid payload.
     */
    external fun decode(instance: Long, samples: ShortArray, sampleRate: Int): ByteArray?
}
