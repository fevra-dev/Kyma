# MONOLITH Hackathon Readiness Assessment

**SonicVault** vs [MONOLITH — A Solana Mobile Hackathon](https://align.nexus/organizations/8b216ce8-dd0e-4f96-85a1-0d95ba3022e2/hackathons/6unDGXkWmY1Yw99SsKMt6pPCQTpSSQh5kSiJRgqTwHXE/C8xDrMwKBRzWNiiqrs55VejMebdjnuBnBENVbzimQVmg)

**Submissions close:** March 9, 2026 · **Voting:** Mar 10 – Apr 30 · **Prizes:** 10 × $10,000 USDC + Best SKR Integration $10,000

---

## What to Build — Compliance

| Requirement | Status | Notes |
|-------------|--------|------|
| **Android + APK** | ✅ | Kotlin 1.9, minSdk 24, targetSdk 34. `./gradlew assembleDebug` produces APK. |
| **Solana Mobile Stack** | ✅ | Seed Vault SDK 0.4.0, Mobile Wallet Adapter 2.0.0. SeekerSeedVaultCrypto, MWA for SonicRequest/SonicSafe. |
| **Mobile Wallet Adapter** | ✅ | Used for transaction signing (SonicRequest, SonicSafe), nonce pool setup. |
| **Mobile-first design** | ✅ | Jetpack Compose, Material 3, Dieter Rams principles. No PWA wrapper. |
| **Meaningful Solana interaction** | ✅ | SonicRequest (acoustic Solana Pay), SonicSafe (air-gapped cold signing), RPC sendTransaction, durable nonce. |

---

## What to Submit — Checklist

| Deliverable | Status | Action |
|-------------|--------|--------|
| **Functional Android APK** | ✅ | Build release: `./gradlew assembleRelease`. Sign with release keystore. |
| **GitHub repository** | ⚠️ | Repo prepared (README, .gitignore). Push to GitHub and ensure public. |
| **Demo video** | ❌ | **Create.** 90s–5 min per README demo script. Show SonicRequest, SonicSafe, Restore, SE-bound. |
| **Pitch deck / presentation** | ❌ | **Create.** 5–10 slides: problem, solution, demo, tech, team, roadmap. |

---

## Evaluation Criteria (25% each)

### 1. Stickiness & Product Market Fit

> *How well does your app resonate with the Seeker community? Does it create habits and drive daily engagement?*

| Strength | Gap |
|----------|-----|
| Unique value: acoustic Solana Pay, air-gapped signing, seed backup in music. | Backup/recovery is occasional, not daily. SonicRequest/SonicSafe are use-case specific. |
| Seeker-exclusive features (SE-bound, Seed Vault) align with hardware. | No clear “daily habit” hook (e.g. activity tracker, notifications). |
| Dead Drop, Secret Messages add utility. | Consider: “Daily backup reminder” or “SonicRequest merchant discovery” to increase engagement. |

**Recommendation:** Emphasize in pitch: *“Every time you back up, pay, or sign — no cables, no pairing. Sound becomes your security layer.”* Add a simple “Recent activity” or “Last backup” on home screen to suggest ongoing use.

---

### 2. User Experience

> *Is the app intuitive, polished, easy to navigate and enjoyable to use?*

| Strength | Gap |
|----------|-----|
| Dieter Rams design, Material 3, clear hierarchy. | Transmit/Receive screen had scroll issues (recently fixed). |
| Onboarding flow, multimodal feedback (haptic, chirp, confetti). | Some flows (SonicSafe, Nonce Pool) are power-user oriented. |
| Radiating rings, SoundHandshakeIndicator, protocol selector. | Ensure all primary actions are reachable on small screens. |

**Recommendation:** Run through all flows on a physical device. Confirm: Backup → Recover → SonicRequest → SonicSafe → Dead Drop. Fix any remaining UX friction before the demo.

---

### 3. Innovation / X-Factor

> *How novel and creative is your idea? Does it stand out from the rest?*

| Strength | Gap |
|----------|-----|
| **Acoustic Solana Pay** — no QR, no NFC, no internet on terminal. | — |
| **Air-gapped cold signing** — sound only, no radio. | — |
| **Seed in music** — steganography, spectrogram art, plausible deniability. | — |
| **SE-bound encryption** — Seeker-exclusive decrypt. | — |
| **Sound as Seed** — entropy from ambient audio. | — |

**Recommendation:** Lead with these differentiators in the demo. One-liner: *“Pay by ear. Back up by song. Sign by silence.”*

---

### 4. Presentation & Demo Quality

> *How clearly does your team communicate their idea? Does the demo effectively showcase the core concept, key features and unique selling points?*

| Strength | Gap |
|----------|-----|
| README has 90s and 5-min demo scripts. | No recorded video yet. |
| Clear architecture diagrams, tech stack table. | Pitch deck not created. |

**Recommendation:**  
1. **Demo video (90s):** SonicRequest (0:00) → SonicSafe (0:45) → Vault Restore (1:30) → SE-bound (2:15).  
2. **Pitch deck:** Problem (seed backup is risky) → Solution (sound) → Demo highlights → Tech → Team → Roadmap.  
3. **Submission text:** Use the one-liner and 2–3 bullet points from the README.

---

## Best SKR Integration ($10,000)

SKR (Seeker / Solana Kickoff Reward) integration is a separate prize. From research:

- **Seeker ID** (e.g. `yourname.skr`) — human-readable names.
- **Genesis Token** — automatic verification in dApp Store apps.
- **dApp Store** — apps built for Solana dApp Store score higher.

| Current | To Consider |
|---------|-------------|
| Seed Vault SDK, MWA, SE-bound encryption. | Explicit SKR/Seeker ID integration if documented. |
| Works on Seeker hardware. | dApp Store submission (optional, post-hackathon). |
| — | Check [Solana Mobile dApp Store docs](https://docs.solanamobile.com/dapp-store/submit-new-app) for SKR-specific requirements. |

**Recommendation:** If SKR/Seeker ID APIs are available, add a “Seeker ID” or “Verified on Seeker” badge in the app. Otherwise, emphasize existing Seeker integration (Seed Vault, SE-bound) in the submission.

---

## Pre-Submission Checklist

- [ ] **Build release APK** — `./gradlew assembleRelease` (configure signing)
- [ ] **Push to GitHub** — public repo, clean README, LICENSE
- [ ] **Record demo video** — 90s minimum, 5 min ideal
- [ ] **Create pitch deck** — 5–10 slides
- [ ] **Test on real device** — SonicRequest, SonicSafe, Restore, Dead Drop
- [ ] **Verify MWA + Seed Vault** — works with SeedVaultSimulator or Seeker
- [ ] **Submit on Align** — APK link, GitHub link, video, deck

---

## Summary

| Category | Readiness | Priority |
|----------|-----------|----------|
| Technical (build, Solana, MWA) | ✅ Strong | — |
| Submission artifacts | ⚠️ Partial | **Create demo video + pitch deck** |
| Stickiness / PMF | ⚠️ Good | Emphasize unique value in pitch |
| UX | ✅ Good | Final device testing |
| Innovation | ✅ Strong | Lead with acoustic differentiators |
| Presentation | ❌ Missing | **Video + deck required** |

**Next steps:**  
1. Record the 90s demo video.  
2. Create a 5–10 slide pitch deck.  
3. Push to GitHub and submit on Align before March 9, 2026.
