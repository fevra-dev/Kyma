package com.sonicvault.app.domain.model

/**
 * Result of successful seed recovery. [checksumVerified] is first 8 hex chars when hybrid checksum matched.
 * [isPrivateKey] true when recovered payload is a Solana private key (base58) rather than BIP39 mnemonic.
 */
data class RecoveryResult(val seed: String, val checksumVerified: String? = null, val isPrivateKey: Boolean = false)

/**
 * Recovery flow state machine:
 * IDLE -> READING -> EXTRACTING -> [DECRYPTING | AWAITING_UNLOCK | AWAITING_VOICE | TIMELOCK_NOT_REACHED] -> SHOW_SEED | ERROR
 * When payload has duress, AWAITING_UNLOCK shows fingerprint + password options.
 * When voice enrolled and device-bound, AWAITING_VOICE requires voice verification before decrypt.
 * When payload has timelock and now < unlockTimestamp, TIMELOCK_NOT_REACHED shows unlock date.
 */
sealed class RecoveryState {
    data object Idle : RecoveryState()
    data object Reading : RecoveryState()
    data object Extracting : RecoveryState()
    data object Decrypting : RecoveryState()
    /** passwordOnly=true: password mode backup, show password field only. passwordOnly=false: duress backup, show fingerprint + password. */
    data class AwaitingUnlock(val extracted: ExtractedPayload, val passwordOnly: Boolean = false) : RecoveryState()
    /** Voice gate: user must speak [challengeWord] to verify identity before decrypt. */
    data class AwaitingVoiceVerification(val extracted: ExtractedPayload, val challengeWord: String) : RecoveryState()
    /** Voice verification in progress: recording user speaking the challenge word. */
    data class VerifyingVoice(val extracted: ExtractedPayload, val challengeWord: String) : RecoveryState()
    /** Timelock: backup unlocks after this Unix timestamp (seconds). */
    data class TimelockNotReached(val unlockTimestamp: Long) : RecoveryState()
    data class ShowSeed(val seedPhrase: String, val checksumVerified: String? = null, val isPrivateKey: Boolean = false) : RecoveryState()
    data class Error(val message: String) : RecoveryState()
}
