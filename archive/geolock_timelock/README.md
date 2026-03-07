# Geolock + Timelock (archived)

Archived 2025-03. These features added complexity without strong security value.
GPS and system clock are trivially spoofable.

## Geolock
- `GeoKeyDerivation.kt` — GPS-based key derivation (~100m grid)
- `GeoTimeLock.kt` — Combined geo+time encryption
- `GpsSpoofingDetector.kt` — Mock location detection
- `GeoTimeLockScreen.kt` — Configuration UI (was never wired to backup creation)
- `LocationSeedMixer.kt` — Location entropy for Sound-as-Seed (also archived)

## Timelock
- `SntpTimeVerifier.kt` — NTP time verification (mitigated clock spoof when online)
- Timelock payload format (v4) was in BackupRepository
- Recovery flow: TimelockNotReached state, unlock date check

## Why archived
- **Geolock**: GPS spoofable via mock locations; requires location permission; UX issues (indoor, airplane)
- **Timelock**: System clock spoofable; NTP helps when online but fails offline; FAQ already noted bypass

## Restoration
To restore: copy files back to `app/src/main/java/`, re-add route in SonicVaultNav,
re-add UI in BackupScreen/SettingsScreen, re-integrate timelock in BackupRepository/RecoveryViewModel.
