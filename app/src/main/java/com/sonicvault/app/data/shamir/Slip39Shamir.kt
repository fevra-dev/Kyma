package com.sonicvault.app.data.shamir

/**
 * SLIP-0039 Shamir's Secret Sharing interface.
 * Splits BIP39 entropy into N shares (M required to recover).
 * Each share is a mnemonic string (SLIP-0039 word list).
 *
 * @see https://github.com/satoshilabs/slips/blob/master/slip-0039.md
 */
interface Slip39Shamir {
    /**
     * Splits a BIP39 seed phrase into N shares, requiring M to reconstruct.
     *
     * @param seedPhrase BIP39 12 or 24 word phrase
     * @param threshold M - minimum shares required (e.g. 2 for 2-of-3)
     * @param totalShares N - total shares (e.g. 3 for 2-of-3)
     * @return List of SLIP-0039 share strings, or failure
     */
    fun split(seedPhrase: String, threshold: Int, totalShares: Int): Result<List<String>>

    /**
     * Combines ≥ threshold shares to recover the original BIP39 seed phrase.
     *
     * @param shares SLIP-0039 share strings (e.g. from extracted stego files)
     * @return Reconstructed BIP39 phrase, or failure
     */
    fun combine(shares: List<String>): Result<String>
}
