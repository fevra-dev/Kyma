# SonicVault

**Pay by ear. Back up by song. Sign by silence.**

SonicVault is an Android app that turns sound into a hardware security module. It uses audio steganography, AES-256-GCM encryption, and ultrasonic data transfer to back up your Solana wallet seed phrase inside ordinary audio files. Backups appear as normal music tracks in your library. Recovery can be device-bound (biometric), password-based (cross-device), via encrypted QR code, or through air — transmitted acoustically between devices.

Built for the **Solana Seeker App Hackathon** and **Monolith Hackathon 2026**.

---

## Features

### Acoustic Solana (SonicRequest, SonicSafe)

- **SonicRequest — Acoustic Solana Pay** — Merchant terminal plays a WAV; Seeker receives the payment request via sound. No QR, no NFC, no internet on the terminal. Fingerprint to pay.
- **SonicSafe — Acoustic Cold Signing** — Hot device transmits unsigned TX acoustically → cold device (air-gapped) signs via Seed Vault → transmits 64-byte signature back → hot broadcasts. No radio, no pairing.
- **Durable Nonce Pool** — Room-backed pool of 3 nonce accounts; eliminates blockhash expiry during acoustic round-trip (~15–30s). Discover existing nonces or create new ones via MWA.
- **Nonce Pool Setup** — Settings screen: discover/import nonce accounts by authority, or create new nonce accounts via MWA sign + RPC send.
- **Replay protection** — Solana Pay URIs with `memo=ts:{unix}` rejected if older than 120 seconds.
- **Acoustic ACK** — 200ms 440Hz tone plays after TX confirm so merchant terminal hears physical confirmation.

### SE-Bound Encryption (Seeker-Exclusive)

- **Seed Vault Key Oracle** — Derives AES-256 key from `signMessage` + HKDF. Same seed → same key. Backup only decrypts on the same Seeker with the same seed.
- **Hardware-bound backups** — "🔒 Seeker Hardware-Bound" badge when creating backup on Seeker. Decrypt fails on stock Android (wrong device).
- **SeedVaultVerifier** — Verify mnemonic matches Seed Vault pubkey before backup.

### Acoustic Vault Restore

- **RestoreCeremonyScreen** — Receive encrypted backup acoustically → decrypt with SE-bound key → MnemonicTeleprompter (word-by-word) → Seed Vault import deeplink.
- **RestoreBroadcastScreen** — Transmit backup WAV acoustically to another device. Extract payload from stego file → AcousticTransmitter.transmitChunked.
- **MnemonicTeleprompter** — One word at a time, large font, "Next" preview. User manually enters into Seed Vault "Import Existing".

### Core Steganography
- **Audio steganography** — Embed encrypted seed phrases in audio using LSB + phase coding (hybrid mode)
- **Reed-Solomon error correction** — RS(n, k, t=16) over GF(2^8) ensures reliable transmission even in noisy environments
- **HMAC-based position derivation** — Cryptographically sound LSB scatter (replaces predictable Random)
- **Multi-bin phase redundancy** — Metadata in FFT bins 1+2 with majority-vote extraction
- **TPDF dither** — Triangular dither before LSB embedding; resists histogram-based steganalysis
- **Matryoshka steganography** — 3-layer nesting: seed hidden in audio, converted to spectrogram image, embedded via image LSB, reconstructed back to audio via Griffin-Lim
- **Spectrogram art** — Embed the SonicVault logo in the 11-18 kHz band, visible in Audacity or Sonic Visualiser

### Encryption
- **AES-256-GCM** — Hardware-backed via Android Keystore (TEE/StrongBox when available)
- **SE-bound (Seed Vault)** — HKDF from `signMessage` signature → 32-byte AES key. Seeker-exclusive; decrypt only on same device + same seed.
- **Argon2id key derivation** — Password mode for cross-device recovery
- **Duress password** — Optional decoy seed for coercion scenarios
- **Timelock** — Backup unlocks only after a chosen date (inheritance planning)
- **Geolock** — GPS-based conditional encryption; decryption requires being at the original location (~100m precision)
- **Combined geo+timelock** — Keys derived from both location and time, combined via XOR + HKDF-SHA256
- **GPS spoofing detection** — Multi-signal analysis (mock provider, accuracy, altitude, provider status)

### Sound Transfer
- **Ultrasonic data-over-sound** — Transmit/receive via ggwave multi-tone FSK (mFSK) in the 15–19.5 kHz near-ultrasonic band (~160 bps effective throughput) between devices. Custom JNI wrapper; 15 kHz base frequency.
- **Chunked transmission** — Payloads >140 bytes: AcousticChunker (MAGIC + SESSION_ID + SEQ + CRC16) → each chunk 2× with 300ms gap. AcousticChunkReceiver reassembles.
- **NACK retransmit** — AcousticNack: cold device sends NACK on decode/CRC fail; hot retransmits that chunk.
- **AcousticTransmitter / AcousticChunkReceiver** — Session-filtered flows for SonicSafe (TX session 1, signature session 2) and RestoreBroadcast (session 3).
- **Dead Drop broadcast** — One-to-many encrypted ultrasonic broadcast with ECDH + AES-256-GCM per recipient
- **Web receiver** — `docs/web/sonic_receive.html` receives Solana Pay URIs and Sonic data (plain text, Reed-Solomon, SVDD) in a browser via ggwave-wasm
- **AirDrop-style handshake** — Radiating rings animation with 7-state connection UX (Idle, Initializing, Broadcasting, Listening, Waiting for Ack, Complete, Failed, Timeout)
- **Live FFT spectrogram** — 4-band color-coded visualization with red highlight on 15–19.5 kHz during transmission

### Seed Generation
- **Sound as Seed** — Generate BIP39 mnemonics from ambient sound entropy (SHA-512 of raw + spectral + delta + statistical + ZCR features, mixed with SecureRandom via HMAC-SHA256)
- **Live entropy quality meter** — Real-time 0-100 quality score during recording
- **GPS entropy mixing** — Optional 100m-grid quantized coordinates mixed into audio entropy

### Identity & Verification
- **ZK Sound Passport** — Zero-knowledge proof of seed ownership via commit-challenge-response protocol, with ultrasonic transmission support
- **Sound fingerprint** — SHA-256 identicon (8x8 symmetric grid) for visual backup verification
- **Deterministic album art** — Unique 512x512 geometric art generated from seed hash
- **BIP-39 validation** — 12 or 24-word seed phrases, Solana derivation path `m/44'/501'/0'/0'`

### Music Player Disguise
- **MediaStore integration** — Backup WAVs appear as music tracks in Android music apps
- **Genre tagging** — Choose from Ambient, Electronic, Field Recording, Classical, Lo-Fi, Nature Sounds, Meditation, White Noise
- **Plausible deniability** — Deterministic titles ("Rain on Glass", "Distant Thunder") make backups indistinguishable from real music

### Security
- **Shamir's Secret Sharing (SLIP-0039)** — Split seed into shares (2-of-3, 2-of-5, 3-of-5); recover with threshold
- **QR export** — Encrypt seed with password and export as QR code (PNG, PDF, printable)
- **Play Integrity** — Device attestation before decryption
- **Root detection** — Checks for rooted/tampered devices
- **Memory wiping** — Seed and key material zeroed after use
- **Secret messages** — Transmit encrypted text, image, or voice messages via sound

### UX
- **Multimodal success feedback** — Haptic (CONFIRM + double-pulse), particle burst animation, ascending C5-E5 audio chirp
- **Voice biometric** — Optional voiceprint enrollment for authentication
- **Onboarding flow** — 3-screen introduction for first-time users
- **Dieter Rams design** — Minimal, functional, honest UI following 10 Principles of Good Design

---

## Recovery Options

SonicVault provides multiple recovery paths — you are never locked to a single device.

| Method | Requires | Cross-device? |
|--------|----------|---------------|
| **Biometric (default)** | Same device + fingerprint/face | No |
| **Password mode** | WAV/FLAC file + password | **Yes** |
| **QR code** | QR image/PDF + password | **Yes** |
| **Shamir shares** | Threshold share files + biometric | No (per share) |
| **Sound transfer** | Transmitting device in range | **Yes** |
| **Acoustic restore** | Backup WAV played near receiver; SE-bound decrypt on Seeker | **Yes** (Seeker only) |
| **Dead Drop** | Any device listening to ultrasonic broadcast | **Yes** |
| **Geolock** | Backup file + correct GPS location | **Yes** |

### If you lose your device

As long as you have your **backup audio file** (WAV/FLAC) and **encryption password**, you can recover on any Android device running SonicVault with password mode enabled. The QR code backup is also fully cross-device — just scan and enter your password. **SE-bound backups** (created on a Seeker) decrypt only on another Seeker loaded with the same seed — play the WAV near the new device and use the teleprompter to enter words into Seed Vault.

---

## Audio Format Support

### Cover audio input (any format works)

| Format | Extensions | Notes |
|--------|-----------|-------|
| WAV | `.wav` | Best quality, native PCM — **use 16-bit** (Audacity: Export as WAV, 16-bit) |
| FLAC | `.flac` | Lossless, preserves stego data perfectly |
| MP3 | `.mp3` | Common, fine as cover input |
| M4A/AAC | `.m4a`, `.aac` | Default phone recording format |
| OGG | `.ogg`, `.oga` | Vorbis or Opus container |
| Opus | `.opus` | Low-latency codec |
| AMR | `.amr`, `.3gp` | Voice recordings |

### Backup output (lossless only)

| Format | Why |
|--------|-----|
| **WAV** | Universal, no compression artifacts, maximum compatibility |
| **FLAC** | 50-70% smaller, lossless — preserves all steganographic data |

### Why not Opus/MP3/AAC for output?

LSB steganography embeds data in the **least significant bits** of PCM samples. Lossy codecs destroy these bits during compression. FLAC is the ideal format: lossless, 50-70% smaller than WAV, and preserves the full frequency spectrum.

### Cover audio tips

- **16-bit PCM WAV** — Required for best results. 24-bit or 32-bit WAV will be rejected with a clear error.
- **Convert in Audacity** — Tracks → Resample → 44100 Hz → File → Export → Export as WAV → 16-bit PCM.

---

## Quick Start

### Build

```bash
cd SonicVault
./gradlew assembleDebug
```

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Or in Android Studio

Open the `SonicVault` folder, sync Gradle, run on device/emulator (API 24+).

> **Note:** Microphone, voice enrollment, sound transmission, and GPS features require a **real Android device**. Emulators lack hardware audio and location support. Microphone permission is requested when you tap RECEIVE or start acoustic restore.

---

## Testing Flows

Step-by-step flows for validating SonicRequest, SonicSafe, and Nonce Pool on devnet.

### Flow A: SonicRequest (Solana Pay over sound) — Two Phantom phones

1. Open `sonic_send.html` (or `docs/web/sonic_send.html`) on laptop; enter recipient wallet, amount, label.
2. On **receiver phone** (wallet that will receive SOL), note the address.
3. On **sender phone** (has SOL to send), open SonicRequest screen.
4. Tap BROADCAST on laptop — it plays the Solana Pay URI as sound.
5. Sender phone decodes the acoustic payload, shows payment details.
6. Tap APPROVE — Phantom opens; biometric to connect, biometric to sign.
7. TX submits to devnet; explorer link shown.

### Flow B: SonicSafe Cold Sign — Two phones

1. **Hot phone**: Open SonicSafe → Hot Signer tab.
2. Enter recipient address, amount.
3. Tap SEND FOR SIGNING — unsigned TX transmits acoustically.
4. **Cold phone**: Open SonicSafe → Cold Signer tab (listening).
5. Cold phone decodes TX, shows details (recipient, amount, durable nonce badge).
6. Tap APPROVE AND SIGN — Phantom signs.
7. Cold phone transmits 64-byte signature back acoustically (2× with 1s gap for reliability).
8. Hot phone receives signature, reconstructs full TX, submits to devnet.
9. Both phones show success.

### Flow C: Nonce Pool Setup

1. Open Settings → NONCE POOL SETUP.
2. Tap CREATE — MWA prompt appears 3 times (one per nonce account).
3. Each creates a nonce account on devnet (~0.00136 SOL each).
4. After creation (5s delay + retry), nonces appear as AVAILABLE in the pool.
5. SonicSafe uses these for durable nonce transactions.

### Seeker (Solana Mobile) vs Phantom

- **Seeker with Seed Vault**: Uses Seed Vault TEE instead of Phantom. Same MWA protocol; signing in hardware secure element. Double-tap + biometric on device.
- **Phantom on regular Android**: Uses Phantom app for signing. Authorize + sign = two biometric prompts.
- Both use the same `walletAdapter.transact()` API — the app does not differentiate.

---

## Demo Script (90 Seconds)

- **0:00** SonicRequest — RPi speaker plays payment WAV → Seeker receives → "Pay 0.5 SOL to Booth #42?" → fingerprint → 440Hz chirp → Explorer confirms in 3 seconds. *"That speaker just took a payment. No QR. No NFC. No internet on the terminal."*
- **0:45** SonicSafe — Hot plays TX → cold signs → cold plays 64B sig → hot broadcasts. *"Air-gapped. No radio. No pairing."*
- **1:30** Vault Restore — Old Seeker plays WAV → new receives → teleprompter → address matches. *"I dropped my Seeker in the ocean. I played a WAV file near my new Seeker. My wallet is back."*
- **2:15** SE-Bound — Same WAV on standard Android fails; on Seeker decrypts. *"This backup only opens on a Seeker loaded with your seed."*

**One-liner:** *"SonicVault turns sound into a hardware security module. Pay by ear. Back up by song. Sign by silence."*

### Extended Demo (5 Minutes)

- **0:00** Hook — "24 words control your wallet. How do you back it up? In music."
- **0:25** Embed seed into WAV, show waveform animation
- **0:55** Show backup in music library (genre-tagged "Rain on Glass")
- **1:15** Transmit via ultrasound — live FFT lights up red at 15–19.5 kHz
- **1:50** Receive on second device — haptic + particle burst + chirp
- **2:10** Open Sonic Visualiser — SonicVault logo visible in spectrogram
- **2:35** Sound as Seed — record ambient sound, entropy meter fills, seed generated from sound
- **3:10** Dead Drop — 3 browser tabs simultaneously receive the broadcast
- **3:40** ZK Passport — prove seed ownership without revealing the seed
- **3:55** Close — "Hidden in sound. Transmitted through air. Disguised as music."

---

## Architecture

```
Seed Phrase → Validate BIP-39 → Encrypt (Keystore + Biometric)
                                        ↓
                                  AES-256-GCM ciphertext
                                        ↓
                          ┌──────────────────────────────────────────┐
                          │  Hybrid Steganography                    │
                          │  • Phase: metadata (37 bytes)            │
                          │  • LSB: encrypted payload                │
                          │  • Reed-Solomon: error correction (t=16) │
                          │  • Spectrogram Art: logo in 11-18 kHz    │
                          └──────────────────────────────────────────┘
                                        ↓
                              WAV / FLAC output
                                        ↓
                    ┌───────────────────────────────────┐
                    │  Music Player Disguise             │
                    │  • MediaStore registration         │
                    │  • Genre tagging + metadata        │
                    │  • Deterministic album art         │
                    │  • Sound fingerprint identicon     │
                    └───────────────────────────────────┘
                                        ↓
                          Share to cloud / storage / sound

Recovery: Select file → Extract → Decrypt (Biometric/Password) → RS decode → Verify checksum → Seed
```

### Matryoshka Pipeline (3-Layer Nesting)

```
Seed → Audio LSB embedding → STFT → Spectrogram image
    → Image LSB embedding (payload in pixels)
    → Griffin-Lim reconstruction → Audio output
    → Spectrogram art branding (11-18 kHz)

Recovery: Audio → STFT → Extract image LSB → Payload
```

### Sound Transfer Pipeline

```
Sender:   Seed → AES-256-GCM encrypt → Reed-Solomon encode → ggwave ultrasonic encode → Speaker
Receiver: Microphone → ggwave decode → Reed-Solomon decode → AES-256-GCM decrypt → Seed
```

### Acoustic Solana Pipelines

```
SonicRequest:  Terminal WAV (Solana Pay URI) → AcousticPaymentReceiver → SonicRequestViewModel
               → SolanaTransactionBuilder.buildSolTransfer → MWA signTransactions → RPC sendTransaction
               → AcousticAck (440Hz tone)

SonicSafe:     Hot: NoncePoolManager.checkoutNonce → buildDurableNonceTx → MWA sign (hot) → AcousticTransmitter
               Cold: AcousticChunkReceiver → SolanaTxParser.parseForDisplay → MWA sign → AcousticTransmitter (64B sig)
               Hot: receive sig → addSignatureAndSerialize → RPC sendTransaction → markConsumed

Restore:       RestoreBroadcast: Backup file → getRawPayloadBytesFromBackup → AcousticTransmitter (session 3)
               RestoreCeremony: AcousticRestoreReceiver → decrypt → MnemonicTeleprompter → Seed Vault import deeplink
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 1.9 |
| UI | Jetpack Compose + Material 3 |
| Encryption | AES-256-GCM, Android Keystore (TEE), Argon2id, PBKDF2, ECDH, HKDF-SHA256 |
| Steganography | LSB Matching + Phase coding (FFT) + TPDF dither + HMAC positions + Image LSB + Griffin-Lim |
| Error correction | Reed-Solomon RS(n, k, t=16) over GF(2^8) |
| Audio codec | ggwave (C++ via JNI), MediaCodec, WAV native |
| Export | WAV, FLAC (javaFlacEncoder) |
| Auth | BiometricPrompt, Play Integrity API |
| Seed standard | BIP-39, SLIP-0039 (Shamir) |
| ZK proofs | HMAC-SHA256 commit-challenge-response (Circom circuit reference) |
| Solana | SolanaKT 2.1.1, Mobile Wallet Adapter 2.0.0, Seed Vault SDK 0.4.0 |
| Persistence | Room (nonce pool), OkHttp (RPC) |
| Wallet compat | Phantom, Solflare, Backpack, Seed Vault (m/44'/501'/0'/0') |

---

## Security Model

- **Keys**: Hardware-backed via Android Keystore (TEE/StrongBox); 15-second auth validity
- **Cipher**: AES-256-GCM with 12-byte IV
- **KDF**: Argon2id (password mode), PBKDF2 600K iterations (fallback/duress)
- **Integrity**: SHA-256 checksum in phase-coded metadata
- **Device binding**: `SHA-256(Android ID + package)` — biometric backups only recoverable on creating device
- **Cross-device**: Password mode and QR export work on any device with SonicVault installed
- **Geolock**: HKDF-SHA256 key derived from GPS coordinates quantized to ~100m grid
- **Timelock**: Day-granularity time-based key, NTP-verified
- **GPS spoofing**: Multi-signal detection (mock provider, accuracy, altitude, provider status)
- **Reed-Solomon**: t=16 symbol error correction for ultrasonic transmission reliability
- **ZK proofs**: Seed ownership provable without revealing seed data
- **Privacy**: `FLAG_SECURE`, `allowBackup=false`, metadata stripping, memory wiping, no network data upload
- **Attestation**: Play Integrity API check before decryption
- **Root detection**: Checks for su binary, test-keys, Magisk, and tampered APK signatures
- **SonicRequest**: Replay protection via `memo=ts:{unix}` (reject if >120s old)
- **SonicSafe**: `sign_transactions` only — Seed Vault signs, app broadcasts. InstructionError = nonce consumed (always call `markConsumed`)
- **Nonce pool**: Room-backed; reconcileOnStartup on app launch for IN_FLIGHT recovery
- **SE-bound**: AES key derived from Seed Vault `signMessage`; never stored

See [SECURITY.md](SECURITY.md) for full details.

---

## Project Structure

```
app/src/main/java/com/sonicvault/app/
├── data/
│   ├── attestation/  # Play Integrity, device attestation
│   ├── binding/      # Device binding, TEE key attestation
│   ├── codec/        # Reed-Solomon ECC codec
│   ├── crypto/       # AES-GCM, Argon2, PBKDF2, duress keys, Seed Vault, SeedVaultKeyOracle, HkdfKeyDeriver
│   ├── deadrop/      # Dead Drop ECDH encryptor + receiver service
│   ├── entropy/      # Audio entropy extraction, GPS location mixer
│   ├── geolock/      # GPS key derivation, geo+time lock, spoofing detection
│   ├── media/        # MediaStore, WAV metadata, sound fingerprint, album art
│   ├── network/      # Certificate-pinned HTTP client
│   ├── nonce/        # NonceAccountEntity, NoncePoolDao, NoncePoolManager, AppDatabase
│   ├── preferences/  # Onboarding, user preferences
│   ├── recovery/     # AcousticRestoreReceiver, RestoreVerifier
│   ├── repository/   # BackupRepository (orchestrates backup/recover, getRawPayloadBytesFromBackup)
│   ├── security/     # Root detection, tamper checking
│   ├── shamir/       # SLIP-0039 Shamir secret sharing
│   ├── solana/       # SolanaPayUri, SolanaTransactionBuilder, SolanaTxParser, SolanaRpcClient,
│   │                 # DurableNonceTxBuilder, AcousticPaymentReceiver
│   ├── sound/        # ggwave data-over-sound, AcousticChunker, AcousticTransmitter, AcousticChunkReceiver,
│   │                 # AcousticAck, AcousticNack, GgwaveDataOverSound
│   ├── stego/        # LSB, Phase, Hybrid, Image, Matryoshka, Spectrogram Art, Griffin-Lim
│   ├── time/         # SNTP time verification
│   ├── voice/        # Voice biometric auth, embedding extraction
│   └── zk/           # ZK Sound Passport (commit-challenge-response)
├── di/               # AppModule (dependency injection)
├── domain/
│   ├── model/        # BackupState, Protocol, RecoveryState, DuressPayload
│   └── usecase/      # CreateBackup, RecoverSeed, SplitSeed, Sound use cases
├── logging/          # SonicVaultLogger (tag-prefixed, level-aware)
├── ui/
│   ├── component/    # RadiatingRings, ConnectionState, SuccessCelebration, Spectrogram, MnemonicTeleprompter, etc.
│   ├── nav/          # Navigation graph (25+ routes)
│   ├── screen/       # home, backup, recovery, sonicrequest, sonicsafe, noncepool, settings, deadrop, etc.
│   └── theme/        # Material 3 dark theme, Spacing, Typography
└── util/             # Constants, BIP39, AutoLock, AudioBeep, EmulatorDetector, SeedVaultDeeplink
```

**170+ Kotlin source files** across 20+ data packages and 20+ screen packages.

---

## Known Limitations

- **Cover format**: Backup cover must be 16-bit PCM WAV (or FLAC/MP3 for decode path). 24-bit/32-bit WAV rejected.
- **Device binding**: Biometric backups require the same device (by design). Use password mode or QR for cross-device.
- **Seed Vault SDK**: Requires SeedVaultSimulator on emulator or Solana Seeker hardware
- **FLAC decode**: Requires Android 13+ (API 33+); older devices use WAV
- **Voice biometric**: Feature-based embedding (placeholder); production should use neural model (FRILL/pyannote)
- **ggwave payload**: ~140 bytes max per single burst; larger data uses AcousticChunker (session + CRC16)
- **Griffin-Lim**: Phase reconstruction is approximate; audio quality degrades slightly after Matryoshka round-trip
- **ZK proofs**: Current implementation uses HMAC-based simplified scheme; production should use Groth16 via Circom/snarkjs
- **Geolock precision**: ~100m grid; GPS accuracy varies by device and environment
- **SonicSafe manual from**: When user enters "From address" manually (no MWA), durable nonce is disabled; blockhash fallback used
- **Acoustic range**: ~1–3 m typical; ambient noise and mic quality affect reliability

---

## Solana Relevance

- Full **BIP-39** support for Phantom, Solflare, Backpack seed phrases
- Standard Solana derivation path: `m/44'/501'/0'/0'`
- **Seed Vault SDK** + **Mobile Wallet Adapter** — native integration for Solana Seeker
- **SonicRequest** — Acoustic Solana Pay: merchant terminal plays WAV, Seeker pays. No QR, no NFC, no internet on terminal.
- **SonicSafe** — Acoustic cold signing: hot transmits TX, cold signs offline, transmits 64B sig back. Air-gapped.
- **Durable nonce pool** — Room-backed; eliminates blockhash expiry during acoustic round-trip
- Audio steganography provides a **novel backup mechanism** unique in the Solana ecosystem
- Data-over-sound enables **air-gapped seed transfer** without cables, Bluetooth, or WiFi
- Dead Drop enables **one-to-many seed distribution** for team wallets
- Sound as Seed enables **entropy generation from the physical world** — no trust in PRNGs alone
- ZK Sound Passport enables **proof of wallet ownership** without exposing the seed

---

## Terminal / Merchant Setup (SonicRequest)

For SonicRequest demos, the merchant terminal (RPi or laptop) plays a Solana Pay URI as a WAV. Use `scripts/generate_pay_wav.py`:

```bash
# Install ggwave (required for terminal script)
pip install ggwave

# Generate WAV only (no playback)
python scripts/generate_pay_wav.py --recipient <SEEKER_PUBKEY> --no-play

# Or loop: regenerate every 90s, play 10× per cycle. Stays within 120s replay window.
python scripts/generate_pay_wav.py --recipient <SEEKER_PUBKEY>  # Output: pay_request.wav
# On Linux: aplay pay_request.wav
# On macOS: afplay pay_request.wav
```

URI format: `solana:<recipient>?amount=<SOL>&label=<merchant>&memo=ts:<unix>`. Terminal uses AUDIBLE_FAST (protocol 1) for range.

---

## Contributing

Contributions are welcome. Please read [SECURITY.md](SECURITY.md) before submitting changes that touch crypto, authentication, or data handling.

---

## License

MIT License — see [LICENSE](LICENSE).

ggwave library: MIT License (Georgi Gerganov) — see [app/src/main/cpp/ggwave/LICENSE](app/src/main/cpp/ggwave/LICENSE).

---

## Troubleshooting

### Set Gradle JDK (Java 17+ required)
Android Studio -> Preferences -> Build Tools -> Gradle -> Gradle JDK -> select JBR 21 or any Java 17+.

### "No space left on device"
Clear Gradle caches: `rm -rf ~/.gradle/caches` then rebuild.

### Emulator limitations
Mic recording, voice enrollment, sound transmission, and GPS features require a real device. The emulator shows a banner warning.
