# Archived Features

Code moved here is preserved but excluded from the build.

## FAQ (archived items)
- `faq_archived.md` — Q&A removed from in-app FAQ: duress password, voice unlock, timelock, secret message, emulator, QR export

## QR Backup (archived)
- `qr/QrExportScreen.kt` — Export seed as QR code
- `qr/QrImportScreen.kt` — Import seed from QR code image

## Sound as Seed (archived)
- `soundseed/SoundSeedScreen.kt` — Generate BIP39 mnemonic from ambient audio
- `soundseed/SoundSeedViewModel.kt` — Recording and entropy extraction logic

## ZK Passport (archived)
- `zk/ZkPassportScreen.kt` — ZK proof UI for seed ownership
- `zk/ZkSoundPassport.kt` — ZK commitment/verification logic

## Geolock + Timelock (archived)
- `geolock_timelock/` — GPS-based and time-based conditional encryption
- See `geolock_timelock/README.md` for details. Archived: spoofable, added complexity.

## Web Receivers (archived)
Legacy web receiver pages superseded by unified `docs/web/sonic_receive.html`:

- `web/solana_pay_receiver_research.html` — Solana Pay–only receiver (from `docs/research/web/`)
- `web/sonic_receive_research.html` — Sonic Receive (Reed-Solomon, SVDD) from `docs/research/web/`
- `web/solana_pay_receiver_newest.html` — Solana Pay receiver from `docs/research/newest/sonicvault/web/`
- `web/sonic_receive_new_ideas.html` — Sonic Receive (source for unified design)
- `web/solana_pay_receiver_new_ideas.html` — Pay by Ear (source for unified design)

The unified receiver at `docs/web/sonic_receive.html` handles both Solana Pay URIs and general Sonic data (plain text, Reed-Solomon, SVDD session code decrypt).
