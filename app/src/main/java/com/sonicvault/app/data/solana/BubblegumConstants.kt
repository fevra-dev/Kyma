package com.sonicvault.app.data.solana

/**
 * Constants for Metaplex Bubblegum compressed NFT (cNFT) acoustic airdrop.
 *
 * Tree and collection must be pre-created on devnet. Use depth 10, buffer 64, canopy 5 (~0.07 SOL).
 * Replace placeholders with actual addresses after running the tree creation script.
 */
object BubblegumConstants {

    /** Bubblegum program ID (v1 + v2). */
    const val BUBBLEGUM_PROGRAM_ID = "BGUMAp9Gq7iTEuizy4pqaxsTyUCBK68MDfK752saRPUY"

    /** SPL Account Compression program. */
    const val SPL_COMPRESSION_ID = "cmtDvXumGCrqC1Age74AVPhSRVXJMd8PJS91L8KbNCK"

    /** SPL Noop (log wrapper for compression). */
    const val SPL_NOOP_ID = "noopb9bkMVfRPU8AsbpTUg8AQkHtKwMYZiFUjNRtMmV"

    /** Metaplex Token Metadata program. */
    const val TOKEN_METADATA_ID = "metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s"

    /** System program. */
    const val SYSTEM_PROGRAM_ID = "11111111111111111111111111111111"

    /**
     * Merkle tree address for Kyma cNFT drops.
     * Placeholder — replace after creating tree on devnet (depth 10, buffer 64, canopy 5).
     */
    const val KYMA_CNFT_TREE = "11111111111111111111111111111111"

    /**
     * Collection NFT mint for Kyma cNFT drops.
     * Placeholder — replace after creating collection on devnet.
     */
    const val KYMA_COLLECTION_MINT = "11111111111111111111111111111111"

    /** cNFT metadata URI (IPFS or Arweave). */
    const val CNFT_METADATA_URI = "https://kyma.xyz/pop/monolith2026.json"

    /** cNFT name. */
    const val CNFT_NAME = "MONOLITH 2026 — Proof of Presence"

    /** cNFT symbol. */
    const val CNFT_SYMBOL = "M26POP"
}
