/**
 * ggwave JNI bridge for SonicVault.
 * Exposes encode/decode to Kotlin via GgwaveNative.kt.
 * Uses I16 format @ 48 kHz for ultrasonic compatibility.
 */
#include <jni.h>
#include <android/log.h>
#include "ggwave/ggwave.h"
#include <algorithm>
#include <cstring>
#include <vector>

#define LOG_TAG "ggwave_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/** Volume 25–50% per spec; 25 avoids clipping. */
static const int kVolume = 25;

/** ggwave variable-length payload limit; we chunk at 138 bytes of data per chunk. */
static const int kChunkDataMax = 138;

/** Chunk header: index (1) + total (1) = 2 bytes. */
static const int kChunkHeaderSize = 2;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_sonicvault_app_data_sound_GgwaveNative_init(JNIEnv *env, jclass clazz, jint sampleRate, jboolean useDss) {
    ggwave_Parameters params = ggwave_getDefaultParameters();
    params.sampleFormatInp = GGWAVE_SAMPLE_FORMAT_I16;
    params.sampleFormatOut = GGWAVE_SAMPLE_FORMAT_I16;
    params.sampleRateInp = (float) sampleRate;
    params.sampleRateOut = (float) sampleRate;
    params.sampleRate = (float) sampleRate;
    params.operatingMode = GGWAVE_OPERATING_MODE_RX_AND_TX;
    if (useDss) {
        params.operatingMode |= GGWAVE_OPERATING_MODE_USE_DSS;
        LOGI("ggwave init: DSS enabled (ultrasonic robustness)");
    }

    ggwave_Instance instance = ggwave_init(params);
    if (instance < 0) {
        LOGE("ggwave_init failed");
        return -1;
    }

    /* Shift ultrasonic band to 17 kHz to avoid Android 10–16 kHz mic dead zone.
     * At 48 kHz, 1024 samples/frame: hzPerSample ≈ 46.875 Hz. 17 kHz ≈ 363 bins. */
    const int kFreqStart17kHz = 363;
    ggwave_txProtocolSetFreqStart(GGWAVE_PROTOCOL_ULTRASOUND_FAST, kFreqStart17kHz);
    ggwave_rxProtocolSetFreqStart(GGWAVE_PROTOCOL_ULTRASOUND_FAST, kFreqStart17kHz);
    ggwave_txProtocolSetFreqStart(GGWAVE_PROTOCOL_ULTRASOUND_FASTEST, kFreqStart17kHz);
    ggwave_rxProtocolSetFreqStart(GGWAVE_PROTOCOL_ULTRASOUND_FASTEST, kFreqStart17kHz);
    LOGI("ggwave ultrasonic freqStart=%d (~17kHz)", kFreqStart17kHz);

    return (jlong) instance;
}

JNIEXPORT void JNICALL
Java_com_sonicvault_app_data_sound_GgwaveNative_free(JNIEnv *env, jclass clazz, jlong instanceHandle) {
    ggwave_Instance instance = (ggwave_Instance) instanceHandle;
    if (instance >= 0) {
        ggwave_free(instance);
    }
}

/**
 * Encode a single chunk (<= kChunkDataMax + kChunkHeaderSize bytes) to I16 PCM.
 * Returns number of bytes in waveform, or -1 on error.
 */
static int encodeChunk(
    ggwave_Instance instance,
    const jbyte *payload,
    jint payloadSize,
    int protocolId,
    std::vector<int16_t> &outSamples
) {
    int nBytes = ggwave_encode(
        instance,
        payload,
        payloadSize,
        (ggwave_ProtocolId) protocolId,
        kVolume,
        nullptr,
        1
    );
    if (nBytes <= 0) return -1;

    outSamples.resize(nBytes / 2);
    int ret = ggwave_encode(
        instance,
        payload,
        payloadSize,
        (ggwave_ProtocolId) protocolId,
        kVolume,
        outSamples.data(),
        0
    );
    if (ret <= 0) return -1;
    return ret;
}

JNIEXPORT jshortArray JNICALL
Java_com_sonicvault_app_data_sound_GgwaveNative_encode(
    JNIEnv *env,
    jclass clazz,
    jlong instanceHandle,
    jbyteArray payload,
    jint protocolId
) {
    ggwave_Instance instance = (ggwave_Instance) instanceHandle;
    if (instance < 0 || payload == nullptr) {
        LOGE("encode: invalid instance or payload");
        return nullptr;
    }

    jsize payloadLen = env->GetArrayLength(payload);
    if (payloadLen <= 0) {
        LOGE("encode: empty payload");
        return nullptr;
    }

    jbyte *payloadPtr = env->GetByteArrayElements(payload, nullptr);
    if (!payloadPtr) return nullptr;

    std::vector<int16_t> totalSamples;
    const int maxChunkPayload = kChunkDataMax;
    int offset = 0;
    int chunkIndex = 0;
    const int totalChunks = (payloadLen + maxChunkPayload - 1) / maxChunkPayload;

    while (offset < payloadLen) {
        uint8_t header[2] = {
            (uint8_t) chunkIndex,
            (uint8_t) totalChunks
        };
        int chunkDataSize = std::min(maxChunkPayload, payloadLen - offset);
        std::vector<uint8_t> chunk(kChunkHeaderSize + chunkDataSize);
        memcpy(chunk.data(), header, kChunkHeaderSize);
        memcpy(chunk.data() + kChunkHeaderSize, payloadPtr + offset, chunkDataSize);

        std::vector<int16_t> chunkSamples;
        int ret = encodeChunk(
            instance,
            (const jbyte *) chunk.data(),
            (jint) chunk.size(),
            protocolId,
            chunkSamples
        );
        if (ret <= 0) {
            env->ReleaseByteArrayElements(payload, payloadPtr, JNI_ABORT);
            LOGE("encode: chunk %d failed", chunkIndex);
            return nullptr;
        }

        totalSamples.insert(
            totalSamples.end(),
            chunkSamples.begin(),
            chunkSamples.end()
        );

        offset += chunkDataSize;
        chunkIndex++;
    }

    /** Wipe raw payload bytes after encoding — may contain encrypted seed data. */
    memset(payloadPtr, 0, payloadLen);
    env->ReleaseByteArrayElements(payload, payloadPtr, JNI_ABORT);

    jshortArray result = env->NewShortArray((jsize) totalSamples.size());
    if (result) {
        env->SetShortArrayRegion(result, 0, (jsize) totalSamples.size(), totalSamples.data());
    }
    return result;
}

/**
 * Decode I16 PCM stream. ggwave_decode is called repeatedly with sliding windows.
 * Collects chunked payloads and reassembles.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_sonicvault_app_data_sound_GgwaveNative_decode(
    JNIEnv *env,
    jclass clazz,
    jlong instanceHandle,
    jshortArray samples,
    jint sampleRate
) {
    ggwave_Instance instance = (ggwave_Instance) instanceHandle;
    if (instance < 0 || samples == nullptr) {
        LOGE("decode: invalid instance or samples");
        return nullptr;
    }

    jsize numSamples = env->GetArrayLength(samples);
    if (numSamples <= 0) return nullptr;

    jshort *samplesPtr = env->GetShortArrayElements(samples, nullptr);
    if (!samplesPtr) return nullptr;

    const int samplesPerFrame = 1024;
    int waveformSizeBytes = numSamples * 2;
    char *waveformPtr = (char *) samplesPtr;

    std::vector<std::pair<int, std::vector<uint8_t>>> chunks;
    std::vector<bool> seen(256, false);
    int totalChunks = -1;
    int pos = 0;

    while (pos + samplesPerFrame * 2 <= waveformSizeBytes) {
        char payloadBuf[256];
        int ret = ggwave_decode(
            instance,
            waveformPtr + pos,
            samplesPerFrame * 2,
            payloadBuf
        );

        if (ret > 0 && ret <= 256 && ret >= kChunkHeaderSize) {
            int idx = (uint8_t) payloadBuf[0];
            int total = (uint8_t) payloadBuf[1];
            if (total > 0 && total <= 256 && idx < total && !seen[idx]) {
                seen[idx] = true;
                std::vector<uint8_t> chunkData(ret - kChunkHeaderSize);
                memcpy(chunkData.data(), payloadBuf + kChunkHeaderSize, ret - kChunkHeaderSize);
                chunks.push_back({idx, chunkData});
                if (totalChunks < 0) totalChunks = total;
            }
        }
        /** Wipe payload buffer after each decode — may contain seed phrase fragments. */
        memset(payloadBuf, 0, sizeof(payloadBuf));

        pos += samplesPerFrame * 2;
    }

    env->ReleaseShortArrayElements(samples, samplesPtr, JNI_ABORT);

    if (chunks.empty()) return nullptr;

    /* Sort by chunk index and reassemble; require all chunks if multi-chunk */
    std::sort(chunks.begin(), chunks.end(),
        [](const auto &a, const auto &b) { return a.first < b.first; });

    if (totalChunks > 0 && (int) chunks.size() < totalChunks) {
        LOGI("decode: incomplete - got %zu of %d chunks", chunks.size(), totalChunks);
        return nullptr;
    }

    int totalSize = 0;
    for (const auto &p : chunks) totalSize += p.second.size();

    jbyteArray result = env->NewByteArray(totalSize);
    if (!result) return nullptr;

    jsize written = 0;
    for (const auto &p : chunks) {
        env->SetByteArrayRegion(result, written, (jsize) p.second.size(), (const jbyte *) p.second.data());
        written += p.second.size();
    }

    /** Wipe reassembled chunk data — may contain decrypted seed payload. */
    for (auto &p : chunks) {
        memset(p.second.data(), 0, p.second.size());
    }

    return result;
}

} // extern "C"
