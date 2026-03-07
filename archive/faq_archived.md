# Archived FAQ Items

Questions and answers removed from the in-app FAQ. Preserved for reference.

---

## What is a duress password?

A duress password is a safety feature for high-risk situations. If someone forces you to reveal your backup, entering the duress password decrypts a decoy seed phrase instead of your real one. The attacker sees what looks like a valid wallet, but your actual funds remain safe. Set a duress password during backup creation — it should be different from your encryption password.

---

## How does voice unlock work?

Voice unlock creates a unique voiceprint from a 4-second recording of your voice. This voiceprint (a mathematical embedding) is stored encrypted on your device only. When verifying, a new recording is compared to your stored voiceprint using cosine similarity. For best results, enroll and verify in similar conditions (same room, same phrase or hum). Use "Test Voice" after enrollment to confirm it recognises you.

---

## What is the timelock / unlock date feature?

Timelock lets you set a date before which the backup cannot be decrypted. This is useful for inheritance planning or delayed access scenarios. Note: the timelock uses your device's clock, so it can be bypassed by changing the system time. For critical time-sensitive protection, combine with a strong password.

*(Feature archived — see geolock_timelock/.)*

---

## What is a secret message?

Secret message is a fun feature that lets you transmit short encrypted text messages via sound. Toggle to "Secret Message" on the Transmit screen to switch from seed phrase to message mode. Messages are limited to ~120 characters due to the ggwave audio payload size. The receiver toggles to "Message" mode to see the decoded text.

---

## Does Kyma work on emulator?

Basic features work on emulator, but microphone recording, voice enrollment, and sound transmission require a real Android device. The emulator lacks a real microphone and speaker, so audio features will show errors or produce no output.

---

## What is QR Export?

QR Export generates a QR code from your seed phrase for offline, camera-based backup. You can print it or photograph it. The QR is generated on-device and never transmitted. Store QR backups securely — anyone who scans it can see your seed.

*(Feature archived — see qr/.)*
