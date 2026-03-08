package com.sonicvault.app.data.solana

/**
 * Constants for SKR token (SonicVault/Kyma tipping token).
 *
 * SKR mint and program IDs for SPL Token transfers.
 */
object SkrConstants {

    /** SKR token mint address (devnet). */
    const val SKR_MINT = "SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3"

    /** SKR token decimals (base units = amount * 10^decimals). */
    const val SKR_DECIMALS = 6

    /** SPL Token Program ID. */
    const val TOKEN_PROGRAM_ID = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"

    /** Associated Token Account Program ID (canonical SPL). */
    const val ATA_PROGRAM_ID = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"
}
