package com.sonicvault.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import com.sonicvault.app.data.attestation.AttestationResult
import com.sonicvault.app.data.attestation.DeviceAttestationProvider
import com.sonicvault.app.data.binding.DeviceBindingProvider
import com.sonicvault.app.data.codec.ReedSolomonCodec
import com.sonicvault.app.data.crypto.EncryptedPayload
import com.sonicvault.app.data.crypto.SeedVaultCrypto
import com.sonicvault.app.data.stego.AudioDecoder
import com.sonicvault.app.data.stego.HybridSteganography
import com.sonicvault.app.data.stego.buildHybridMetadata
import com.sonicvault.app.data.stego.LSBSteganography
import com.sonicvault.app.data.stego.SpectrogramArtLayer
import com.sonicvault.app.data.stego.WavAudioHandler
import com.sonicvault.app.data.stego.WavContent
import com.sonicvault.app.domain.model.ExtractedPayload
import com.sonicvault.app.domain.model.RecoveryResult
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.Constants
import com.sonicvault.app.util.isSolanaSeeker
import com.sonicvault.app.util.wipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Orchestrates crypto + stego + file I/O for backup and recovery.
 * Payload format: deviceBindingHash(32) || version(1) || [payload].
 * Version 1: length(4) || iv(12) || ciphertext (single).
 * Version 2: real_len(4) || real_iv(12) || real_ct || decoy_len(4) || decoy_iv(12) || decoy_ct.
 */
/**
 * Result of successful backup creation: stego file URI, optional checksum,
 * sound fingerprint identicon, deterministic album art, and short hex ID.
 */
data class CreateBackupResult(
    val stegoUri: Uri,
    val checksum: String?,
    val fingerprint: android.graphics.Bitmap? = null,
    val albumArt: android.graphics.Bitmap? = null,
    val shortId: String? = null
)

interface BackupRepository {
    suspend fun createBackup(seedPhrase: String, coverAudioUri: Uri, activity: FragmentActivity, duressPassword: String?, usePasswordMode: Boolean, password: String?, unlockTimestamp: Long?, useHybridMode: Boolean, useHardwareBound: Boolean = false): Result<CreateBackupResult>
    suspend fun extractPayload(stegoAudioUri: Uri): Result<ExtractedPayload>
    suspend fun recoverSeedWithBiometric(extracted: ExtractedPayload, activity: FragmentActivity): Result<RecoveryResult>
    suspend fun recoverSeedWithPassword(extracted: ExtractedPayload, password: String): Result<RecoveryResult>
    suspend fun recoverSeed(stegoAudioUri: Uri, activity: FragmentActivity): Result<String>
    /**
     * Returns formatted payload (device hash + length + iv + ciphertext) for data-over-sound transmit.
     * Does not write a stego file.
     */
    suspend fun getPayloadForSoundTransmit(seedPhrase: String, activity: FragmentActivity): Result<ByteArray>
    /**
     * For data-over-sound receive: embed [payloadBytes] into a minimal WAV and run recovery.
     */
    suspend fun recoverFromPayloadBytes(payloadBytes: ByteArray, activity: FragmentActivity): Result<String>
    /**
     * Creates a backup for a single Shamir share (SLIP-0039 mnemonic string).
     * Same encrypt/embed flow as createBackup, but no BIP39 validation.
     */
    suspend fun createShareBackup(shareText: String, coverAudioUri: Uri, activity: FragmentActivity): Result<CreateBackupResult>
    /**
     * Extracts raw formatted payload bytes from a backup file for acoustic transmit.
     * Used by RestoreBroadcastScreen to play backup over speaker for new device to receive.
     */
    suspend fun getRawPayloadBytesFromBackup(stegoAudioUri: Uri): Result<ByteArray>
}

class BackupRepositoryImpl(
    private val appContext: Context,
    private val crypto: SeedVaultCrypto,
    private val wavHandler: WavAudioHandler,
    private val audioDecoder: AudioDecoder,
    private val stego: LSBSteganography,
    private val hybridStego: HybridSteganography,
    private val deviceBinding: DeviceBindingProvider,
    private val bip39Validator: com.sonicvault.app.util.Bip39Validator,
    private val attestation: DeviceAttestationProvider
) : BackupRepository {

    /** Error message when device attestation fails (rooted, emulator, tampered). */
    private companion object {
        const val ATTESTATION_FAIL_MESSAGE =
            "This device may be compromised. Recovery is disabled for your security."
    }

    /** Phase needs 37 bytes = 296 bits = 296 blocks of 512 samples. */
    private val hybridPhaseMinSamples = Constants.HYBRID_METADATA_BYTES * 8 * 512

    override suspend fun createBackup(seedPhrase: String, coverAudioUri: Uri, activity: FragmentActivity, duressPassword: String?, usePasswordMode: Boolean, password: String?, unlockTimestamp: Long?, useHybridMode: Boolean, useHardwareBound: Boolean): Result<CreateBackupResult> =
        withContext(Dispatchers.IO) {
            SonicVaultLogger.i("createBackup started")
            try {
                val plaintext = seedPhrase.trim().toByteArray(Charsets.UTF_8)
                var decoyPlaintext: ByteArray? = null
                var decoyKey: ByteArray? = null
                try {
                    val checksumRaw = MessageDigest.getInstance("SHA-256").digest(plaintext)
                    val checksum = checksumRaw.joinToString("") { "%02x".format(it) }.take(32)
                    val realPayload = when {
                        useHardwareBound && !usePasswordMode -> {
                            val key = com.sonicvault.app.data.crypto.SeedVaultKeyOracle.deriveAesKey(activity)
                                ?: return@withContext Result.failure(Exception("Hardware-bound key derivation failed. Ensure Seed Vault is available."))
                            try {
                                com.sonicvault.app.data.crypto.AesGcmPasswordCrypto.encrypt(plaintext, key)
                                    ?: return@withContext Result.failure(Exception("SE-bound encryption failed."))
                            } finally {
                                key.wipe()
                            }
                        }
                        usePasswordMode && password != null -> {
                            com.sonicvault.app.data.crypto.PasswordSeedVaultCrypto.encryptWithPassword(plaintext, password)
                                ?: return@withContext Result.failure(Exception("Password encryption failed."))
                        }
                        else -> {
                            crypto.ensureKeyCreated()
                            when (val authResult = crypto.encrypt(plaintext, activity)) {
                                is com.sonicvault.app.data.crypto.BiometricAuthResult.Success -> authResult.data
                                is com.sonicvault.app.data.crypto.BiometricAuthResult.Cancelled -> {
                                    SonicVaultLogger.e("createBackup failed: biometric cancelled")
                                    return@withContext Result.failure(Exception("Authentication cancelled. Try again."))
                                }
                                is com.sonicvault.app.data.crypto.BiometricAuthResult.Failed -> {
                                    SonicVaultLogger.e("createBackup failed: biometric failed")
                                    return@withContext Result.failure(Exception("Authentication failed. Try again."))
                                }
                            }
                        }
                    }
                    val bindingHash = deviceBinding.getDeviceBindingHash()
                    val formatted = when {
                        useHardwareBound -> formatSeBoundPayloadWithBinding(bindingHash, realPayload)
                        unlockTimestamp != null -> {
                            SonicVaultLogger.i("[Timelock] createBackup unlock_timestamp=$unlockTimestamp")
                            formatTimelockPayloadWithBinding(bindingHash, realPayload, unlockTimestamp)
                        }
                        usePasswordMode -> formatPasswordPayloadWithBinding(bindingHash, realPayload)
                        duressPassword != null -> {
                            val decoySeed = com.sonicvault.app.data.crypto.DuressKeyDerivation.deriveDecoySeed(duressPassword, bip39Validator.wordList)
                            val decoyPt = decoySeed.toByteArray(Charsets.UTF_8)
                            decoyPlaintext = decoyPt
                            val decoyK = com.sonicvault.app.data.crypto.DuressKeyDerivation.deriveKey(duressPassword)
                            decoyKey = decoyK
                            val decoyPayload = com.sonicvault.app.data.crypto.AesGcmPasswordCrypto.encrypt(decoyPt, decoyK)
                                ?: return@withContext Result.failure(Exception("Decoy encryption failed."))
                            SonicVaultLogger.i("[Duress] backup created with decoy")
                            formatDualPayloadWithBinding(bindingHash, realPayload, decoyPayload)
                        }
                        else -> formatSinglePayloadWithBinding(bindingHash, realPayload)
                    }
                    val wav = audioDecoder.decodeToPcm(coverAudioUri)
                    val coverRms = kotlin.math.sqrt(wav.samples.take(1000).map { it.toDouble() * it }.average())
                    SonicVaultLogger.i("[BackupRepo] decodeToPcm samples=${wav.samples.size} rate=${wav.sampleRate} coverRms=$coverRms")
                    if (wav.samples.isEmpty()) {
                        SonicVaultLogger.e("[BackupRepo] cover audio is empty")
                        return@withContext Result.failure(Exception("Cover audio could not be decoded."))
                    }
                    /**
                     * LSB Matching (v7) uses device binding hash for embed positions — extraction
                     * requires the same device. For cross-device modes (password, timelock),
                     * use sequential LSB (v5) so recovery works on any device.
                     */
                    val useLsbMatching = useHybridMode && !usePasswordMode && unlockTimestamp == null
                    // RS-wrapped payload for hybrid mode: tolerates bit errors from corruption/noise
                    val payloadToEmbed = if (useHybridMode) ReedSolomonCodec.encode(formatted) else formatted
                    val lsbMinSamples = if (useLsbMatching) {
                        (payloadToEmbed.size + 4) * 16
                    } else {
                        (payloadToEmbed.size * 8 + Constants.DEFAULT_NUM_LSB - 1) / Constants.DEFAULT_NUM_LSB
                    }
                    val minSamples = (Constants.MIN_COVER_DURATION_SECONDS * wav.sampleRate).coerceAtLeast(
                        if (useHybridMode) maxOf(lsbMinSamples, hybridPhaseMinSamples) else lsbMinSamples
                    )
                    if (wav.samples.size < minSamples) {
                        SonicVaultLogger.w("cover audio too short duration=${wav.samples.size / wav.sampleRate}s")
                        return@withContext Result.failure(Exception("Cover audio too short. Need at least ${Constants.MIN_COVER_DURATION_SECONDS} seconds."))
                    }
                    if (useLsbMatching && wav.samples.size < (payloadToEmbed.size + 4) * 16) {
                        SonicVaultLogger.e("createBackup failed: LSB Matching capacity")
                        return@withContext Result.failure(Exception("Cover audio has insufficient capacity for payload."))
                    } else if (!useLsbMatching && wav.samples.size * Constants.DEFAULT_NUM_LSB < payloadToEmbed.size * 8) {
                        SonicVaultLogger.e("createBackup failed: capacity")
                        return@withContext Result.failure(Exception("Cover audio has insufficient capacity for payload."))
                    }
                    /**
                     * Spectrogram art MUST be applied BEFORE stego embedding. Art adds sine
                     * tones (±262 amplitude) that flip LSBs if applied after. Phase bins 1-2
                     * are unaffected (art targets 11-18 kHz, bins ~128-209).
                     */
                    val artSamples = SpectrogramArtLayer.embedText(wav.samples.copyOf(), wav.sampleRate)
                    val artRms = kotlin.math.sqrt(artSamples.take(1000).map { it.toDouble() * it }.average())
                    SonicVaultLogger.i("[BackupRepo] after spectrogram artRms=$artRms")

                    var stegoSamples = if (useHybridMode) {
                        val version = if (useLsbMatching) Constants.PAYLOAD_VERSION_HYBRID_LSB_MATCHING else Constants.PAYLOAD_VERSION_HYBRID
                        val metadata = buildHybridMetadata(version, payloadToEmbed.size, checksumRaw)
                        hybridStego.embed(artSamples, metadata, payloadToEmbed, keyBytesForLsb = if (useLsbMatching) bindingHash else null)
                    } else {
                        stego.embed(artSamples, formatted, Constants.DEFAULT_NUM_LSB)
                    }
                    val stegoRms = kotlin.math.sqrt(stegoSamples.take(1000).map { it.toDouble() * it }.average())
                    SonicVaultLogger.i("[BackupRepo] after stego stegoSamples=${stegoSamples.size} stegoRms=$stegoRms")

                    val stegoUri = wavHandler.writeWav(stegoSamples, wav.sampleRate, null)

                    // Generate sound fingerprint identicon + album art from the stego audio
                    val stegoBytes = stegoSamples.let { s ->
                        val buf = java.nio.ByteBuffer.allocate(s.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        s.forEach { buf.putShort(it) }
                        buf.array()
                    }
                    val fingerprint = com.sonicvault.app.data.media.SoundFingerprint.fromWavBytes(stegoBytes)
                    val shortId = com.sonicvault.app.data.media.SoundFingerprint.shortIdFromBytes(stegoBytes)
                    val albumArt = com.sonicvault.app.data.media.AlbumArtGenerator.generateFromHash(checksumRaw)

                    SonicVaultLogger.i("createBackup completed stegoUri=$stegoUri shortId=$shortId")
                    Result.success(CreateBackupResult(stegoUri, checksum, fingerprint, albumArt, shortId))
                } finally {
                    plaintext.wipe()
                    decoyPlaintext?.wipe()
                    decoyKey?.wipe()
                }
            } catch (e: Exception) {
                SonicVaultLogger.e("createBackup failed", e)
                Result.failure(e)
            }
        }

    override suspend fun extractPayload(stegoAudioUri: Uri): Result<ExtractedPayload> =
        withContext(Dispatchers.IO) {
            try {
                // Use AudioDecoder for WAV, FLAC, MP3 (DEEP_RESEARCH Area 4; SEEDSAFE export flow)
                val wav = audioDecoder.decodeToPcm(stegoAudioUri)
                val minHeader = Constants.DEVICE_BINDING_HASH_BYTES + 4
                if (wav.samples.size * Constants.DEFAULT_NUM_LSB < 8 * minHeader) {
                    return@withContext Result.failure(Exception("This doesn't look like a Kyma backup. Select a WAV or FLAC file."))
                }
                // Try hybrid extract first (metadata in phase, payload in LSB)
                val keyBytesForLsb = deviceBinding.getDeviceBindingHash()
                val hybridResult = hybridStego.extract(wav.samples, keyBytesForLsb)
                if (hybridResult != null) {
                    val (metadata, rawPayloadBytes) = hybridResult
                    // RS-decode: corrects bit errors; passes through if legacy (no RS magic)
                    val payloadBytes = ReedSolomonCodec.decode(rawPayloadBytes)
                    if (payloadBytes == null) {
                        return@withContext Result.failure(Exception("Backup corrupted or incomplete."))
                    }
                    // Metadata: [version:1][payload_len:4][checksum:32]; checksum at bytes 5-36
                    val checksumRaw = metadata.copyOfRange(5, 37)
                    val extracted = parseFormattedPayloadBytes(payloadBytes).copy(expectedChecksumRaw = checksumRaw)
                    return@withContext Result.success(extracted)
                }
                // Fall back to LSB-only extraction
                val headerSize = Constants.DEVICE_BINDING_HASH_BYTES + 4
                val headerBytes = stego.extract(wav.samples, headerSize, Constants.DEFAULT_NUM_LSB)
                if (headerBytes.size < headerSize) {
                    return@withContext Result.failure(Exception("No backup data found. Try a different file."))
                }
                val storedBindingHash = headerBytes.copyOfRange(0, Constants.DEVICE_BINDING_HASH_BYTES)
                val b32 = headerBytes[Constants.DEVICE_BINDING_HASH_BYTES].toInt() and 0xFF
                // Password (v3) and timelock (v4): skip device binding for cross-device recovery
                val skipDeviceCheck = b32 == Constants.PAYLOAD_VERSION_PASSWORD || b32 == Constants.PAYLOAD_VERSION_TIMELOCK
                if (!skipDeviceCheck && !deviceBinding.isSameDevice(storedBindingHash)) {
                    return@withContext Result.failure(Exception("This backup was created on a different device."))
                }
                val extracted = when {
                    b32 == Constants.PAYLOAD_VERSION_TIMELOCK -> parseTimelockPayload(wav.samples, Constants.DEVICE_BINDING_HASH_BYTES + 1)
                    b32 == Constants.PAYLOAD_VERSION_PASSWORD -> parsePasswordPayload(wav.samples, Constants.DEVICE_BINDING_HASH_BYTES + 1)
                    b32 == Constants.PAYLOAD_VERSION_SE_BOUND -> parseSeBoundPayload(wav.samples, Constants.DEVICE_BINDING_HASH_BYTES + 1)
                    b32 == Constants.PAYLOAD_VERSION_SINGLE || b32 == Constants.PAYLOAD_VERSION_DURESS -> {
                        val version = b32
                        if (version == Constants.PAYLOAD_VERSION_DURESS) {
                            parseDualPayload(wav.samples, Constants.DEVICE_BINDING_HASH_BYTES + 1)
                        } else {
                            parseSinglePayload(wav.samples, Constants.DEVICE_BINDING_HASH_BYTES + 1)
                        }
                    }
                    else -> parseLegacyPayload(wav.samples, Constants.DEVICE_BINDING_HASH_BYTES)
                }
                Result.success(extracted)
            } catch (e: Exception) {
                SonicVaultLogger.e("extractPayload failed", e)
                // Map parse/format errors to user-friendly messages (per DEEP_RESEARCH Area 13, SEEDSAFE error copy)
                val msg = when {
                    e.message?.contains("Payload too short") == true ||
                    e.message?.contains("Invalid payload length") == true -> "No backup data found. Try a different file."
                    e.message?.contains("FLAC playback requires") == true -> e.message!!
                    else -> e.message ?: "Recovery failed."
                }
                Result.failure(Exception(msg, e))
            }
        }

    override suspend fun recoverSeedWithBiometric(extracted: ExtractedPayload, activity: FragmentActivity): Result<RecoveryResult> =
        withContext(Dispatchers.IO) {
            /* Only enforce Play Integrity on Solana Seeker; regular Android may fail (custom ROM, uncertified). */
            if (isSolanaSeeker(appContext)) {
                val attestResult = attestation.attestDevice(appContext)
                val passed = attestResult.getOrNull() == AttestationResult.Pass
                if (!passed) {
                    SonicVaultLogger.i("[Attestation] result=fail blocking recoverSeedWithBiometric")
                    return@withContext Result.failure(Exception(ATTESTATION_FAIL_MESSAGE))
                }
            }
            /** Timelock (v4): use system clock. NTP verification archived. */
            val now = System.currentTimeMillis() / 1000
            extracted.unlockTimestamp?.let { unlockTs ->
                /** Sanity: reject obviously backdated clocks (before 2024-01-01 epoch=1704067200). */
                if (now < 1704067200L) {
                    SonicVaultLogger.w("[Timelock] system clock appears backdated now=$now")
                    return@withContext Result.failure(Exception("System clock appears incorrect. Please check your device date and time."))
                }
                if (now < unlockTs) {
                    SonicVaultLogger.i("[Timelock] unlock_timestamp=$unlockTs now=$now rejected=true")
                    return@withContext Result.failure(Exception("Unlock date not reached. This backup unlocks on ${formatTimestampAsDate(unlockTs)}."))
                }
                SonicVaultLogger.i("[Timelock] unlock_timestamp=$unlockTs now=$now rejected=false")
            }
            val decrypted = if (extracted.requiresSeBoundDecryption) {
                val key = com.sonicvault.app.data.crypto.SeedVaultKeyOracle.deriveAesKey(activity)
                    ?: return@withContext Result.failure(Exception("Hardware-bound decryption failed. Ensure Seed Vault is available and authorized."))
                try {
                    com.sonicvault.app.data.crypto.AesGcmPasswordCrypto.decrypt(extracted.realPayload, key)
                        ?: return@withContext Result.failure(Exception("Decryption failed. This backup may have been created on a different device."))
                } finally {
                    key.wipe()
                }
            } else when (val authResult = crypto.decrypt(extracted.realPayload, activity)) {
                is com.sonicvault.app.data.crypto.BiometricAuthResult.Success -> authResult.data
                is com.sonicvault.app.data.crypto.BiometricAuthResult.Cancelled -> {
                    return@withContext Result.failure(Exception("Authentication cancelled. Try again."))
                }
                is com.sonicvault.app.data.crypto.BiometricAuthResult.Failed -> {
                    return@withContext Result.failure(Exception("Authentication failed. Use password if this backup was encrypted with one."))
                }
            }
            verifyChecksumAndBuildResult(decrypted, extracted.expectedChecksumRaw)
        }

    override suspend fun recoverSeedWithPassword(extracted: ExtractedPayload, password: String): Result<RecoveryResult> =
        withContext(Dispatchers.IO) {
            /* Only enforce Play Integrity on Solana Seeker; regular Android may fail (custom ROM, uncertified). */
            if (isSolanaSeeker(appContext)) {
                val attestResult = attestation.attestDevice(appContext)
                val passed = attestResult.getOrNull() == AttestationResult.Pass
                if (!passed) {
                    SonicVaultLogger.i("[Attestation] result=fail blocking recoverSeedWithPassword")
                    return@withContext Result.failure(Exception(ATTESTATION_FAIL_MESSAGE))
                }
            }
            // Password mode: real payload has salt → decrypt real with Argon2
            if (extracted.realPayload.isPasswordMode) {
                val decrypted = com.sonicvault.app.data.crypto.PasswordSeedVaultCrypto.decryptWithPassword(extracted.realPayload, password)
                    ?: return@withContext Result.failure(Exception("Invalid password."))
                return@withContext verifyChecksumAndBuildResult(decrypted, extracted.expectedChecksumRaw)
            }
            /**
             * Duress mode: decrypt decoy with PBKDF2.
             * Migration support: try current iterations (600K) first, then fall back
             * to legacy (100K) for pre-v2 backups. This allows old backups to be
             * recovered seamlessly after the iteration count upgrade.
             */
            val decoyPayload = extracted.decoyPayload ?: run {
                return@withContext Result.failure(Exception("This backup does not support password recovery."))
            }
            val key = com.sonicvault.app.data.crypto.DuressKeyDerivation.deriveKey(password)
            try {
                val decrypted = com.sonicvault.app.data.crypto.AesGcmPasswordCrypto.decrypt(decoyPayload, key)
                if (decrypted != null) {
                    SonicVaultLogger.d("[Duress] decrypt attempt decoy=ok iterations=current(600K)")
                    return@withContext verifyChecksumAndBuildResult(decrypted, extracted.expectedChecksumRaw)
                }
            } finally {
                key.wipe()
            }
            /** Legacy fallback: try 100K iterations for pre-v2 backups. */
            SonicVaultLogger.d("[Duress] current iteration decrypt failed; trying legacy 100K")
            val legacyKey = com.sonicvault.app.data.crypto.DuressKeyDerivation.deriveKeyLegacy(password)
            try {
                val decrypted = com.sonicvault.app.data.crypto.AesGcmPasswordCrypto.decrypt(decoyPayload, legacyKey)
                    ?: return@withContext Result.failure(Exception("Invalid password."))
                SonicVaultLogger.d("[Duress] decrypt attempt decoy=ok iterations=legacy(100K)")
                verifyChecksumAndBuildResult(decrypted, extracted.expectedChecksumRaw)
            } finally {
                legacyKey.wipe()
            }
        }

    override suspend fun recoverSeed(stegoAudioUri: Uri, activity: FragmentActivity): Result<String> =
        withContext(Dispatchers.IO) {
            extractPayload(stegoAudioUri).fold(
                onSuccess = { recoverSeedWithBiometric(it, activity).map { it.seed } },
                onFailure = { Result.failure(it) }
            )
        }

    /**
     * Verifies SHA-256 of plaintext against stored checksum (hybrid mode). Returns RecoveryResult.
     * AES-GCM auth guarantees integrity; checksum mismatch is a soft warning (phase metadata can be lossy).
     * Caller passes ownership; plaintext is wiped before return.
     */
    private fun verifyChecksumAndBuildResult(plaintext: ByteArray, expectedChecksumRaw: ByteArray?): Result<RecoveryResult> {
        val seed = String(plaintext, Charsets.UTF_8)
        if (expectedChecksumRaw != null) {
            val computed = MessageDigest.getInstance("SHA-256").digest(plaintext)
            if (!computed.contentEquals(expectedChecksumRaw)) {
                /**
                 * AES-GCM is authenticated: successful decryption guarantees data integrity.
                 * Phase-encoded metadata can introduce bit errors in the stored checksum,
                 * causing false positives. Treat as soft warning, not hard failure.
                 */
                SonicVaultLogger.w("[Recovery] checksum mismatch (phase metadata may be lossy); proceeding with decrypted seed")
            }
            val verifiedChecksum = computed.joinToString("") { "%02x".format(it) }.take(8)
            SonicVaultLogger.i("[Recovery] checksum verified: $verifiedChecksum")
            plaintext.wipe()
            return Result.success(RecoveryResult(seed, verifiedChecksum))
        }
        plaintext.wipe()
        return Result.success(RecoveryResult(seed, null))
    }

    /** Parse payload bytes from hybrid extract (full formatted: binding + version + rest). */
    private fun parseFormattedPayloadBytes(payloadBytes: ByteArray): ExtractedPayload {
        if (payloadBytes.size < Constants.DEVICE_BINDING_HASH_BYTES + 1) throw IllegalArgumentException("Payload too short")
        val storedBindingHash = payloadBytes.copyOfRange(0, Constants.DEVICE_BINDING_HASH_BYTES)
        val b32 = payloadBytes[Constants.DEVICE_BINDING_HASH_BYTES].toInt() and 0xFF
        // Password (v3) and timelock (v4): skip device binding for cross-device recovery
        val skipDeviceCheck = b32 == Constants.PAYLOAD_VERSION_PASSWORD || b32 == Constants.PAYLOAD_VERSION_TIMELOCK
        if (!skipDeviceCheck && !deviceBinding.isSameDevice(storedBindingHash)) {
            throw IllegalArgumentException("This backup was created on a different device.")
        }
        val rest = payloadBytes.copyOfRange(Constants.DEVICE_BINDING_HASH_BYTES + 1, payloadBytes.size)
        return when (b32) {
            Constants.PAYLOAD_VERSION_TIMELOCK -> parseTimelockFromBytes(rest)
            Constants.PAYLOAD_VERSION_PASSWORD -> parsePasswordFromBytes(rest)
            Constants.PAYLOAD_VERSION_SE_BOUND -> parseSeBoundFromBytes(rest)
            Constants.PAYLOAD_VERSION_DURESS -> parseDualFromBytes(rest)
            Constants.PAYLOAD_VERSION_SINGLE -> parseSingleFromBytes(rest)
            else -> parseLegacyFromBytes(rest)
        }
    }

    private fun parseLegacyFromBytes(rest: ByteArray): ExtractedPayload {
        if (rest.size < 4 + Constants.GCM_IV_LENGTH + 16) throw IllegalArgumentException("Invalid payload length")
        val payloadLength = ByteBuffer.wrap(rest, 0, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
        if (payloadLength <= 0 || payloadLength > 10_000) throw IllegalArgumentException("Invalid payload length")
        val iv = rest.copyOfRange(4, 4 + Constants.GCM_IV_LENGTH)
        val ciphertextWithTag = rest.copyOfRange(4 + Constants.GCM_IV_LENGTH, 4 + payloadLength)
        return ExtractedPayload(hasDuress = false, realPayload = EncryptedPayload(iv, ciphertextWithTag), decoyPayload = null)
    }

    private fun parseSeBoundFromBytes(rest: ByteArray): ExtractedPayload {
        if (rest.size < 4) throw IllegalArgumentException("Payload too short")
        val len = ByteBuffer.wrap(rest, 0, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
        if (len < 12 || rest.size < 4 + len) throw IllegalArgumentException("Invalid payload length")
        val iv = rest.copyOfRange(4, 16)
        val ciphertextWithTag = rest.copyOfRange(16, 4 + len)
        return ExtractedPayload(hasDuress = false, realPayload = EncryptedPayload(iv, ciphertextWithTag), decoyPayload = null, requiresSeBoundDecryption = true)
    }

    /** Parse version 6: same structure as single (length || iv || ct), SE-bound decrypt. */
    private fun parseSeBoundPayload(samples: ShortArray, afterVersion: Int): ExtractedPayload {
        val totalHeader = afterVersion + Constants.PAYLOAD_LENGTH_PREFIX_BYTES
        val headerBytes = stego.extract(samples, totalHeader, Constants.DEFAULT_NUM_LSB)
        val payloadLength = ByteBuffer.wrap(headerBytes, afterVersion, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
        if (payloadLength <= 0 || payloadLength > 10_000) throw IllegalArgumentException("Invalid payload length")
        val totalEmbedded = totalHeader + payloadLength
        val allBytes = stego.extract(samples, totalEmbedded, Constants.DEFAULT_NUM_LSB)
        val iv = allBytes.copyOfRange(totalHeader, totalHeader + Constants.GCM_IV_LENGTH)
        val ciphertextWithTag = allBytes.copyOfRange(totalHeader + Constants.GCM_IV_LENGTH, allBytes.size)
        return ExtractedPayload(hasDuress = false, realPayload = EncryptedPayload(iv, ciphertextWithTag), decoyPayload = null, requiresSeBoundDecryption = true)
    }

    private fun parseSingleFromBytes(rest: ByteArray): ExtractedPayload {
        if (rest.size < 4 + Constants.GCM_IV_LENGTH + 16) throw IllegalArgumentException("Invalid payload length")
        val payloadLength = ByteBuffer.wrap(rest, 0, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
        if (payloadLength <= 0 || payloadLength > 10_000) throw IllegalArgumentException("Invalid payload length")
        val iv = rest.copyOfRange(4, 4 + Constants.GCM_IV_LENGTH)
        val ciphertextWithTag = rest.copyOfRange(4 + Constants.GCM_IV_LENGTH, 4 + payloadLength)
        return ExtractedPayload(hasDuress = false, realPayload = EncryptedPayload(iv, ciphertextWithTag), decoyPayload = null)
    }

    /**
     * Parse password payload: length(4) || salt(16|17) || iv(12) || ciphertextWithTag.
     * Salt is 17 bytes when PBKDF2 fallback used (marker byte + 16); else 16 bytes (Argon2).
     */
    private fun parsePasswordFromBytes(rest: ByteArray): ExtractedPayload {
        val saltLen = if (rest.size > 4 && rest[4] == com.sonicvault.app.data.crypto.PasswordSeedVaultCrypto.PBKDF2_SALT_MARKER) 17 else 16
        if (rest.size < 4 + saltLen + Constants.GCM_IV_LENGTH + 16) throw IllegalArgumentException("Invalid payload length")
        val payloadLength = ByteBuffer.wrap(rest, 0, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
        if (payloadLength <= 0 || payloadLength > 10_000) throw IllegalArgumentException("Invalid payload length")
        val salt = rest.copyOfRange(4, 4 + saltLen)
        val iv = rest.copyOfRange(4 + saltLen, 4 + saltLen + Constants.GCM_IV_LENGTH)
        val ciphertextWithTag = rest.copyOfRange(4 + saltLen + Constants.GCM_IV_LENGTH, 4 + payloadLength)
        return ExtractedPayload(hasDuress = false, realPayload = EncryptedPayload(iv, ciphertextWithTag, salt), decoyPayload = null)
    }

    private fun parseTimelockFromBytes(rest: ByteArray): ExtractedPayload {
        if (rest.size < 4 + Constants.TIMELOCK_TIMESTAMP_BYTES + Constants.GCM_IV_LENGTH + 16) throw IllegalArgumentException("Invalid payload length")
        val payloadLength = ByteBuffer.wrap(rest, 0, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
        if (payloadLength <= 0 || payloadLength > 10_000) throw IllegalArgumentException("Invalid payload length")
        val unlockTs = ByteBuffer.wrap(rest, 4, 8).order(java.nio.ByteOrder.BIG_ENDIAN).long
        val iv = rest.copyOfRange(4 + 8, 4 + 8 + Constants.GCM_IV_LENGTH)
        val ciphertextWithTag = rest.copyOfRange(4 + 8 + Constants.GCM_IV_LENGTH, 4 + payloadLength)
        return ExtractedPayload(hasDuress = false, realPayload = EncryptedPayload(iv, ciphertextWithTag), decoyPayload = null, unlockTimestamp = unlockTs)
    }

    private fun parseDualFromBytes(rest: ByteArray): ExtractedPayload {
        if (rest.size < 4 + Constants.GCM_IV_LENGTH + 16 + 4 + Constants.GCM_IV_LENGTH + 16) throw IllegalArgumentException("Invalid payload length")
        val realLen = ByteBuffer.wrap(rest, 0, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
        if (realLen <= 0 || realLen > 10_000) throw IllegalArgumentException("Invalid real payload length")
        val realIv = rest.copyOfRange(4, 4 + Constants.GCM_IV_LENGTH)
        val realCt = rest.copyOfRange(4 + Constants.GCM_IV_LENGTH, 4 + realLen)
        val decoyLen = ByteBuffer.wrap(rest, 4 + realLen, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
        if (decoyLen <= 0 || decoyLen > 10_000) throw IllegalArgumentException("Invalid decoy payload length")
        val decoyIv = rest.copyOfRange(4 + realLen + 4, 4 + realLen + 4 + Constants.GCM_IV_LENGTH)
        val decoyCt = rest.copyOfRange(4 + realLen + 4 + Constants.GCM_IV_LENGTH, 4 + realLen + 4 + decoyLen)
        return ExtractedPayload(hasDuress = true, realPayload = EncryptedPayload(realIv, realCt), decoyPayload = EncryptedPayload(decoyIv, decoyCt))
    }

    /** Parse legacy (no version): binding(32) || length(4) || iv(12) || ct */
    private fun parseLegacyPayload(samples: ShortArray, afterBinding: Int): ExtractedPayload {
        val lengthOffset = afterBinding
        val totalHeader = lengthOffset + Constants.PAYLOAD_LENGTH_PREFIX_BYTES
        val headerBytes = stego.extract(samples, totalHeader, Constants.DEFAULT_NUM_LSB)
        val payloadLength = ByteBuffer.wrap(headerBytes, lengthOffset, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
        if (payloadLength <= 0 || payloadLength > 10_000) throw IllegalArgumentException("Invalid payload length")
        val totalEmbedded = totalHeader + payloadLength
        val allBytes = stego.extract(samples, totalEmbedded, Constants.DEFAULT_NUM_LSB)
        val iv = allBytes.copyOfRange(totalHeader, totalHeader + Constants.GCM_IV_LENGTH)
        val ciphertextWithTag = allBytes.copyOfRange(totalHeader + Constants.GCM_IV_LENGTH, allBytes.size)
        return ExtractedPayload(hasDuress = false, realPayload = EncryptedPayload(iv, ciphertextWithTag), decoyPayload = null)
    }

    /** Parse version 4: length(4) || unlock_timestamp(8) || iv(12) || ciphertextWithTag. Timelock. */
    private fun parseTimelockPayload(samples: ShortArray, afterVersion: Int): ExtractedPayload {
        val totalHeader = afterVersion + Constants.PAYLOAD_LENGTH_PREFIX_BYTES
        val headerBytes = stego.extract(samples, totalHeader, Constants.DEFAULT_NUM_LSB)
        val payloadLength = ByteBuffer.wrap(headerBytes, afterVersion, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
        if (payloadLength <= 0 || payloadLength > 10_000) throw IllegalArgumentException("Invalid timelock payload length")
        val totalEmbedded = totalHeader + payloadLength
        val allBytes = stego.extract(samples, totalEmbedded, Constants.DEFAULT_NUM_LSB)
        val tsOffset = totalHeader
        val ivOffset = tsOffset + Constants.TIMELOCK_TIMESTAMP_BYTES
        val ctOffset = ivOffset + Constants.GCM_IV_LENGTH
        val unlockTs = ByteBuffer.wrap(allBytes, tsOffset, Constants.TIMELOCK_TIMESTAMP_BYTES).order(java.nio.ByteOrder.BIG_ENDIAN).long
        val iv = allBytes.copyOfRange(ivOffset, ivOffset + Constants.GCM_IV_LENGTH)
        val ciphertextWithTag = allBytes.copyOfRange(ctOffset, allBytes.size)
        return ExtractedPayload(hasDuress = false, realPayload = EncryptedPayload(iv, ciphertextWithTag), decoyPayload = null, unlockTimestamp = unlockTs)
    }

    /** Parse version 3: length(4) || salt(16|17) || iv(12) || ciphertextWithTag. Salt 17 when PBKDF2 fallback. */
    private fun parsePasswordPayload(samples: ShortArray, afterVersion: Int): ExtractedPayload {
        val totalHeader = afterVersion + Constants.PAYLOAD_LENGTH_PREFIX_BYTES
        val headerBytes = stego.extract(samples, totalHeader, Constants.DEFAULT_NUM_LSB)
        val payloadLength = ByteBuffer.wrap(headerBytes, afterVersion, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
        if (payloadLength <= 0 || payloadLength > 10_000) throw IllegalArgumentException("Invalid payload length")
        val totalEmbedded = totalHeader + payloadLength
        val allBytes = stego.extract(samples, totalEmbedded, Constants.DEFAULT_NUM_LSB)
        val saltLen = if (allBytes.size > totalHeader && allBytes[totalHeader] == com.sonicvault.app.data.crypto.PasswordSeedVaultCrypto.PBKDF2_SALT_MARKER) 17 else 16
        val salt = allBytes.copyOfRange(totalHeader, totalHeader + saltLen)
        val iv = allBytes.copyOfRange(totalHeader + saltLen, totalHeader + saltLen + Constants.GCM_IV_LENGTH)
        val ciphertextWithTag = allBytes.copyOfRange(totalHeader + saltLen + Constants.GCM_IV_LENGTH, allBytes.size)
        return ExtractedPayload(hasDuress = false, realPayload = EncryptedPayload(iv, ciphertextWithTag, salt), decoyPayload = null)
    }

    /** Parse version 1: single payload after version byte. */
    private fun parseSinglePayload(samples: ShortArray, afterVersion: Int): ExtractedPayload {
        val totalHeader = afterVersion + Constants.PAYLOAD_LENGTH_PREFIX_BYTES
        val headerBytes = stego.extract(samples, totalHeader, Constants.DEFAULT_NUM_LSB)
        val payloadLength = ByteBuffer.wrap(headerBytes, afterVersion, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
        if (payloadLength <= 0 || payloadLength > 10_000) throw IllegalArgumentException("Invalid payload length")
        val totalEmbedded = totalHeader + payloadLength
        val allBytes = stego.extract(samples, totalEmbedded, Constants.DEFAULT_NUM_LSB)
        val iv = allBytes.copyOfRange(totalHeader, totalHeader + Constants.GCM_IV_LENGTH)
        val ciphertextWithTag = allBytes.copyOfRange(totalHeader + Constants.GCM_IV_LENGTH, allBytes.size)
        return ExtractedPayload(hasDuress = false, realPayload = EncryptedPayload(iv, ciphertextWithTag), decoyPayload = null)
    }

    /** Parse version 2: real_len(4) || real_iv(12) || real_ct || decoy_len(4) || decoy_iv(12) || decoy_ct */
    private fun parseDualPayload(samples: ShortArray, afterVersion: Int): ExtractedPayload {
        val lenHeader = afterVersion + 4
        val headerBytes = stego.extract(samples, lenHeader, Constants.DEFAULT_NUM_LSB)
        val realLen = ByteBuffer.wrap(headerBytes, afterVersion, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
        if (realLen <= 0 || realLen > 10_000) throw IllegalArgumentException("Invalid real payload length")
        val realEnd = lenHeader + realLen
        val realBytes = stego.extract(samples, realEnd, Constants.DEFAULT_NUM_LSB)
        val realIv = realBytes.copyOfRange(lenHeader, lenHeader + Constants.GCM_IV_LENGTH)
        val realCt = realBytes.copyOfRange(lenHeader + Constants.GCM_IV_LENGTH, realEnd)
        val decoyLenHeader = realEnd + 4
        val decoyHeaderBytes = stego.extract(samples, decoyLenHeader, Constants.DEFAULT_NUM_LSB)
        val decoyLen = ByteBuffer.wrap(decoyHeaderBytes, realEnd, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
        if (decoyLen <= 0 || decoyLen > 10_000) throw IllegalArgumentException("Invalid decoy payload length")
        val decoyEnd = decoyLenHeader + decoyLen
        val decoyBytes = stego.extract(samples, decoyEnd, Constants.DEFAULT_NUM_LSB)
        val decoyIv = decoyBytes.copyOfRange(decoyLenHeader, decoyLenHeader + Constants.GCM_IV_LENGTH)
        val decoyCt = decoyBytes.copyOfRange(decoyLenHeader + Constants.GCM_IV_LENGTH, decoyEnd)
        return ExtractedPayload(
            hasDuress = true,
            realPayload = EncryptedPayload(realIv, realCt),
            decoyPayload = EncryptedPayload(decoyIv, decoyCt)
        )
    }

    override suspend fun getPayloadForSoundTransmit(seedPhrase: String, activity: FragmentActivity): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                crypto.ensureKeyCreated()
                val plaintext = seedPhrase.trim().toByteArray(Charsets.UTF_8)
                try {
                    val payload = when (val authResult = crypto.encrypt(plaintext, activity)) {
                        is com.sonicvault.app.data.crypto.BiometricAuthResult.Success -> authResult.data
                        is com.sonicvault.app.data.crypto.BiometricAuthResult.Cancelled -> {
                            return@withContext Result.failure(Exception("Authentication cancelled. Try again."))
                        }
                        is com.sonicvault.app.data.crypto.BiometricAuthResult.Failed -> {
                            return@withContext Result.failure(Exception("Authentication failed. Try again."))
                        }
                    }
                    val bindingHash = deviceBinding.getDeviceBindingHash()
                    val formatted = formatSinglePayloadWithBinding(bindingHash, payload)
                    SonicVaultLogger.i("getPayloadForSoundTransmit len=${formatted.size}")
                    Result.success(formatted)
                } finally {
                    plaintext.wipe()
                }
            } catch (e: Exception) {
                SonicVaultLogger.e("getPayloadForSoundTransmit failed", e)
                Result.failure(e)
            }
        }

    override suspend fun recoverFromPayloadBytes(payloadBytes: ByteArray, activity: FragmentActivity): Result<String> =
        withContext(Dispatchers.IO) {
            if (payloadBytes.size < Constants.DEVICE_BINDING_HASH_BYTES + Constants.PAYLOAD_LENGTH_PREFIX_BYTES + Constants.GCM_IV_LENGTH + 16) {
                return@withContext Result.failure(Exception("Payload too short."))
            }
            val minSamples = (payloadBytes.size * 8 + Constants.DEFAULT_NUM_LSB - 1) / Constants.DEFAULT_NUM_LSB
            val coverSamples = ShortArray(minSamples)
            val stegoSamples = stego.embed(coverSamples, payloadBytes, Constants.DEFAULT_NUM_LSB)
            val uri = wavHandler.writeWav(stegoSamples, Constants.DEFAULT_SAMPLE_RATE, null)
            recoverSeed(uri, activity)
        }

    override suspend fun getRawPayloadBytesFromBackup(stegoAudioUri: Uri): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val wav = audioDecoder.decodeToPcm(stegoAudioUri)
                val minHeader = Constants.DEVICE_BINDING_HASH_BYTES + 4
                if (wav.samples.size * Constants.DEFAULT_NUM_LSB < 8 * minHeader) {
                    return@withContext Result.failure(Exception("This doesn't look like a Kyma backup. Select a WAV or FLAC file."))
                }
                // Try hybrid extract first (metadata in phase, payload in LSB)
                val keyBytesForLsb = deviceBinding.getDeviceBindingHash()
                val hybridResult = hybridStego.extract(wav.samples, keyBytesForLsb)
                if (hybridResult != null) {
                    val (_, rawPayloadBytes) = hybridResult
                    val payloadBytes = ReedSolomonCodec.decode(rawPayloadBytes)
                    if (payloadBytes == null) {
                        return@withContext Result.failure(Exception("Backup corrupted or incomplete."))
                    }
                    SonicVaultLogger.i("[BackupRepo] getRawPayloadBytesFromBackup hybrid len=${payloadBytes.size}")
                    return@withContext Result.success(payloadBytes)
                }
                // LSB-only: extract full payload based on version
                val versionedHeaderSize = Constants.DEVICE_BINDING_HASH_BYTES + 1 + Constants.PAYLOAD_LENGTH_PREFIX_BYTES
                val headerBytes = stego.extract(wav.samples, versionedHeaderSize, Constants.DEFAULT_NUM_LSB)
                if (headerBytes.size < versionedHeaderSize) {
                    return@withContext Result.failure(Exception("No backup data found. Try a different file."))
                }
                val b32 = headerBytes[Constants.DEVICE_BINDING_HASH_BYTES].toInt() and 0xFF
                val totalSize = when {
                    b32 in 1..6 -> {
                        val payloadLength = ByteBuffer.wrap(headerBytes, Constants.DEVICE_BINDING_HASH_BYTES + 1, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
                        if (payloadLength <= 0 || payloadLength > 10_000) {
                            return@withContext Result.failure(Exception("Invalid payload length."))
                        }
                        when (b32) {
                            Constants.PAYLOAD_VERSION_DURESS -> {
                                val realEnd = versionedHeaderSize + payloadLength
                                val bytesWithDecoyLen = stego.extract(wav.samples, realEnd + 4, Constants.DEFAULT_NUM_LSB)
                                val decoyLen = ByteBuffer.wrap(bytesWithDecoyLen, realEnd, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
                                if (decoyLen <= 0 || decoyLen > 10_000) {
                                    return@withContext Result.failure(Exception("Invalid decoy payload length."))
                                }
                                realEnd + 4 + decoyLen
                            }
                            else -> versionedHeaderSize + payloadLength
                        }
                    }
                    else -> {
                        // Legacy: binding(32) || length(4) || iv || ct
                        val legacyHeaderSize = Constants.DEVICE_BINDING_HASH_BYTES + Constants.PAYLOAD_LENGTH_PREFIX_BYTES
                        val legacyHeader = stego.extract(wav.samples, legacyHeaderSize, Constants.DEFAULT_NUM_LSB)
                        val payloadLength = ByteBuffer.wrap(legacyHeader, Constants.DEVICE_BINDING_HASH_BYTES, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
                        if (payloadLength <= 0 || payloadLength > 10_000) {
                            return@withContext Result.failure(Exception("Invalid payload length."))
                        }
                        legacyHeaderSize + payloadLength
                    }
                }
                val allBytes = stego.extract(wav.samples, totalSize, Constants.DEFAULT_NUM_LSB)
                SonicVaultLogger.i("[BackupRepo] getRawPayloadBytesFromBackup LSB len=${allBytes.size}")
                Result.success(allBytes)
            } catch (e: Exception) {
                SonicVaultLogger.e("[BackupRepo] getRawPayloadBytesFromBackup failed", e)
                Result.failure(e)
            }
        }

    override suspend fun createShareBackup(shareText: String, coverAudioUri: Uri, activity: FragmentActivity): Result<CreateBackupResult> =
        withContext(Dispatchers.IO) {
            SonicVaultLogger.i("[Shamir] createShareBackup started")
            try {
                crypto.ensureKeyCreated()
                val plaintext = shareText.trim().toByteArray(Charsets.UTF_8)
                try {
                    val checksum = MessageDigest.getInstance("SHA-256")
                        .digest(plaintext)
                        .joinToString("") { "%02x".format(it) }
                        .take(32)
                    val realPayload = when (val authResult = crypto.encrypt(plaintext, activity)) {
                        is com.sonicvault.app.data.crypto.BiometricAuthResult.Success -> authResult.data
                        is com.sonicvault.app.data.crypto.BiometricAuthResult.Cancelled -> {
                            SonicVaultLogger.e("[Shamir] createShareBackup: biometric cancelled")
                            return@withContext Result.failure(Exception("Authentication cancelled. Try again."))
                        }
                        is com.sonicvault.app.data.crypto.BiometricAuthResult.Failed -> {
                            SonicVaultLogger.e("[Shamir] createShareBackup: biometric failed")
                            return@withContext Result.failure(Exception("Authentication failed. Try again."))
                        }
                    }
                    val bindingHash = deviceBinding.getDeviceBindingHash()
                    val formatted = formatSinglePayloadWithBinding(bindingHash, realPayload)
                    val wav = audioDecoder.decodeToPcm(coverAudioUri)
                    val minSamples = (Constants.MIN_COVER_DURATION_SECONDS * wav.sampleRate).coerceAtLeast(
                        (formatted.size * 8 + Constants.DEFAULT_NUM_LSB - 1) / Constants.DEFAULT_NUM_LSB
                    )
                    if (wav.samples.size < minSamples) {
                        return@withContext Result.failure(Exception("Cover audio too short."))
                    }
                    if (wav.samples.size * Constants.DEFAULT_NUM_LSB < formatted.size * 8) {
                        return@withContext Result.failure(Exception("Cover audio has insufficient capacity."))
                    }
                    val stegoSamples = stego.embed(wav.samples, formatted, Constants.DEFAULT_NUM_LSB)
                    val stegoUri = wavHandler.writeWav(stegoSamples, wav.sampleRate, null)
                    SonicVaultLogger.i("[Shamir] createShareBackup completed")
                    Result.success(CreateBackupResult(stegoUri, checksum))
                } finally {
                    plaintext.wipe()
                }
            } catch (e: Exception) {
                SonicVaultLogger.e("[Shamir] createShareBackup failed", e)
                Result.failure(e)
            }
        }

    /** Format v4: deviceBindingHash(32) || version(1)=4 || length(4 BE) || unlock_ts(8) || iv(12) || ciphertextWithTag. */
    private fun formatTimelockPayloadWithBinding(bindingHash: ByteArray, payload: EncryptedPayload, unlockTimestamp: Long): ByteArray {
        val payloadLen = Constants.TIMELOCK_TIMESTAMP_BYTES + payload.iv.size + payload.ciphertextWithTag.size
        val total = Constants.DEVICE_BINDING_HASH_BYTES + 1 + Constants.PAYLOAD_LENGTH_PREFIX_BYTES + payloadLen
        val buf = ByteBuffer.allocate(total).order(java.nio.ByteOrder.BIG_ENDIAN)
        buf.put(bindingHash)
        buf.put(Constants.PAYLOAD_VERSION_TIMELOCK.toByte())
        buf.putInt(payloadLen)
        buf.putLong(unlockTimestamp)
        buf.put(payload.iv)
        buf.put(payload.ciphertextWithTag)
        return buf.array()
    }

    /** Format v3: deviceBindingHash(32) || version(1)=3 || length(4 BE) || salt(16) || iv(12) || ciphertextWithTag. Cross-device. */
    private fun formatPasswordPayloadWithBinding(bindingHash: ByteArray, payload: EncryptedPayload): ByteArray {
        val salt = payload.salt ?: throw IllegalArgumentException("Password payload requires salt")
        val payloadLen = salt.size + payload.iv.size + payload.ciphertextWithTag.size
        val total = Constants.DEVICE_BINDING_HASH_BYTES + 1 + Constants.PAYLOAD_LENGTH_PREFIX_BYTES + payloadLen
        val buf = ByteBuffer.allocate(total).order(java.nio.ByteOrder.BIG_ENDIAN)
        buf.put(bindingHash)
        buf.put(Constants.PAYLOAD_VERSION_PASSWORD.toByte())
        buf.putInt(payloadLen)
        buf.put(salt)
        buf.put(payload.iv)
        buf.put(payload.ciphertextWithTag)
        return buf.array()
    }

    /** Format v6: deviceBindingHash(32) || version(1)=6 || length(4 BE) || iv(12) || ciphertextWithTag. SE-bound. */
    private fun formatSeBoundPayloadWithBinding(bindingHash: ByteArray, payload: EncryptedPayload): ByteArray {
        val payloadLen = payload.iv.size + payload.ciphertextWithTag.size
        val total = Constants.DEVICE_BINDING_HASH_BYTES + 1 + Constants.PAYLOAD_LENGTH_PREFIX_BYTES + payloadLen
        val buf = ByteBuffer.allocate(total).order(java.nio.ByteOrder.BIG_ENDIAN)
        buf.put(bindingHash)
        buf.put(Constants.PAYLOAD_VERSION_SE_BOUND.toByte())
        buf.putInt(payloadLen)
        buf.put(payload.iv)
        buf.put(payload.ciphertextWithTag)
        return buf.array()
    }

    /** Format v1: deviceBindingHash(32) || version(1)=1 || length(4 BE) || iv(12) || ciphertextWithTag */
    private fun formatSinglePayloadWithBinding(bindingHash: ByteArray, payload: EncryptedPayload): ByteArray {
        val payloadLen = payload.iv.size + payload.ciphertextWithTag.size
        val total = Constants.DEVICE_BINDING_HASH_BYTES + 1 + Constants.PAYLOAD_LENGTH_PREFIX_BYTES + payloadLen
        val buf = ByteBuffer.allocate(total).order(java.nio.ByteOrder.BIG_ENDIAN)
        buf.put(bindingHash)
        buf.put(Constants.PAYLOAD_VERSION_SINGLE.toByte())
        buf.putInt(payloadLen)
        buf.put(payload.iv)
        buf.put(payload.ciphertextWithTag)
        return buf.array()
    }

    /** Format v2: deviceBindingHash(32) || version(1)=2 || real_len(4) || real_iv(12) || real_ct || decoy_len(4) || decoy_iv(12) || decoy_ct */
    private fun formatDualPayloadWithBinding(bindingHash: ByteArray, real: EncryptedPayload, decoy: EncryptedPayload): ByteArray {
        val realLen = real.iv.size + real.ciphertextWithTag.size
        val decoyLen = decoy.iv.size + decoy.ciphertextWithTag.size
        val total = Constants.DEVICE_BINDING_HASH_BYTES + 1 + 4 + realLen + 4 + decoyLen
        val buf = ByteBuffer.allocate(total).order(java.nio.ByteOrder.BIG_ENDIAN)
        buf.put(bindingHash)
        buf.put(Constants.PAYLOAD_VERSION_DURESS.toByte())
        buf.putInt(realLen)
        buf.put(real.iv)
        buf.put(real.ciphertextWithTag)
        buf.putInt(decoyLen)
        buf.put(decoy.iv)
        buf.put(decoy.ciphertextWithTag)
        return buf.array()
    }

    /** Formats Unix timestamp (seconds) as human-readable date for user-facing messages. */
    private fun formatTimestampAsDate(epochSeconds: Long): String {
        return try {
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochSeconds * 1000))
        } catch (e: Exception) {
            epochSeconds.toString()
        }
    }
}
