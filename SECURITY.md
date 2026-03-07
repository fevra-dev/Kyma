# Kyma Security

This document summarizes key security properties of SonicVault. Implementation follows OWASP MASVS and Android Keystore best practices.

## Key Storage

- **Android Keystore:** Encryption keys are stored in hardware-backed storage (TEE or StrongBox when available).
- **No key exposure:** Key material never leaves secure hardware; the app layer only receives cipher references.
- **StrongBox fallback:** When available (API 28+), StrongBox is used for stronger protection. Otherwise TEE is used. If StrongBox fails, the app falls back to TEE. Check debug logs for `backend=StrongBox` or `backend=TEE`.

## Authentication

- **Biometric or device credential:** Encrypt or decrypt requires fingerprint, face, or PIN/pattern/password.
- **Auth validity:** 15 seconds after successful authentication. Existing keys created before this change retain their original validity (e.g. 30s).
- **Per-operation flow:** BiometricPrompt.CryptoObject ensures cryptographic operations only proceed after user authentication.

## Memory Wiping

- **Best-effort only:** OWASP MASVS recommends overwriting sensitive data after use. Kyma wipes plaintext and decrypted byte arrays with `ByteArray.fill(0)` in `finally` blocks.
- **No JVM guarantee:** The JVM does not guarantee that overwritten values are gone from memory; GC may retain copies. This is a defense-in-depth measure.
- **Scope:** Plaintext seed bytes, derived keys, and decrypted payloads are wiped in BackupRepository, PasswordSeedVaultCrypto, QrExportScreen, and Slip39ShamirImpl.

## Dead Drop Broadcast

- **Ephemeral ECDH:** Each broadcast generates a fresh EC key pair (secp256r1). No persistent keys stored.
- **AES-256-GCM:** Payload encrypted with derived shared secret via `KeyAgreement`. IV generated from `SecureRandom`.
- **Packet framing:** Dead Drop packets identified by 4-byte magic header `DDRP`. Unknown packets are silently discarded.
- **Reed-Solomon ECC:** Transmitted data is RS-encoded before ggwave modulation for error correction.
- **No key persistence:** Ephemeral keys are discarded after transmission. Receiver must be actively listening during broadcast.

## Geolock

- **GPS quantization:** Coordinates quantized to ~100m grid (`floor(lat / 0.001) * 0.001`) before key derivation. Exact position is never stored.
- **HKDF-SHA256:** Geo-key derived from quantized coordinates via HKDF with application-specific salt and info parameters.
- **Key combination:** Geo-key and time-key combined via byte-wise XOR followed by HKDF to produce the final encryption key.
- **Spoofing detection:** `GpsSpoofingDetector` checks mock provider flag, mock location API (API 31+), accuracy sanity (<1m), altitude sanity (-500m to 9000m), and GPS provider availability. Confidence score reported to UI.

## Timelock (Enhanced)

- **NTP verification:** `SntpTimeVerifier` queries multiple NTP servers (pool.ntp.org) to detect clock manipulation.
- **Clock drift detection:** If NTP drift exceeds threshold, system clock is treated as unreliable and NTP time is used.
- **Day granularity:** Time keys derived at day-level precision. Unlock timestamps stored as Unix epoch seconds.
- **Fallback:** If NTP is unavailable (offline device), system clock is used with a warning logged. Backdated clocks (before 2024-01-01) are rejected outright.

## ZK Sound Passport

- **Commit-challenge-response:** Prover creates commitment = HMAC-SHA256(seed, salt), responds to verifier's challenge with HMAC-SHA256(seed, challenge).
- **No seed transmission:** Only the commitment and challenge-response are transmitted. The seed phrase never leaves the device.
- **Salt:** 16 bytes from `SecureRandom` per commitment. Challenge: 32 bytes from `SecureRandom`.
- **Packet format:** ZK packets use typed magic headers for CHALLENGE, RESPONSE, and COMMITMENT over ultrasonic channel.
- **Production path:** Reference Circom circuit (`sound_passport.circom`) implements SHA256-based ZK proof for future on-chain verification.

## Reed-Solomon Error Correction

- **RS(n, k, t=16):** GF(2^8) implementation corrects up to 16 symbol errors per codeblock.
- **Packet framing:** RS-encoded data prefixed with 4-byte magic `SVRS` for identification.
- **Use cases:** All ultrasonic data-over-sound transmissions (backup, Dead Drop, ZK Passport) are RS-encoded before ggwave modulation.

## Matryoshka Steganography

- **3-layer nesting:** Seed → audio LSB steganography → spectrogram image generation → image LSB steganography → Griffin-Lim audio reconstruction → spectrogram art branding.
- **Griffin-Lim:** Iterative phase reconstruction (32 iterations default) recovers audio from magnitude-only spectrogram. This is intentionally lossy for the carrier audio but preserves the embedded payload.
- **Image LSB capacity:** 2 bits per channel, 3 channels (RGB) = 6 bits per pixel. Payload capacity = (width × height × 6) / 8 bytes.
- **Magic framing:** Image LSB payloads prefixed with `MTRY` magic for identification.

## Sound as Seed (Audio Entropy)

- **Multi-feature extraction:** Entropy harvested from raw PCM samples, spectral magnitudes, spectral deltas, statistical moments, and zero-crossing rates.
- **SecureRandom mixing:** All audio features mixed with `SecureRandom` bytes via HMAC-SHA256. Audio entropy is additive to system entropy, never the sole source.
- **Entropy quality estimation:** UI displays estimated entropy bits based on sample variance and spectral spread. Users see real-time quality feedback during recording.
- **BIP39 conversion:** Final entropy bytes mapped to BIP39 mnemonic via standard checksum algorithm.

## Manual Verification

- **Memory wiping:** Heap dump after backup/recovery; search for sensitive strings. Exposure window should be minimal.
- **Auth validity:** Authenticate, wait 16+ seconds, attempt decrypt—expect re-auth or failure.
- **StrongBox:** Run on emulator (no StrongBox) and real device (StrongBox if available); check logs for `backend=StrongBox` or `backend=TEE`.
