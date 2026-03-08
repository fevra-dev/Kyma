package com.sonicvault.app.data.solana

/**
 * Constants for Metaplex Bubblegum compressed NFT (cNFT) acoustic airdrop.
 *
 * Tree and collection must be pre-created on devnet. Use depth 10, buffer 64, canopy 5 (~0.07 SOL).
 * Replace placeholders with actual addresses after running the tree creation script.
 *
 * ## Deployment Steps (devnet)
 *
 * 1. Install Metaplex CLI:
 *    ```
 *    npm i -g @metaplex-foundation/cli
 *    ```
 *
 * 2. Create merkle tree (depth 10 = 1024 leaves, buffer 64, canopy 5):
 *    ```
 *    metaplex bubblegum create-tree \
 *      --rpc-url https://api.devnet.solana.com \
 *      --keypair ~/.config/solana/id.json \
 *      --max-depth 10 --max-buffer-size 64 --canopy-depth 5
 *    ```
 *    → Copy the merkle tree address into [KYMA_CNFT_TREE]
 *
 * 3. Create collection NFT:
 *    ```
 *    metaplex token-metadata create \
 *      --rpc-url https://api.devnet.solana.com \
 *      --keypair ~/.config/solana/id.json \
 *      --name "MONOLITH 2026" --symbol "M26POP" \
 *      --uri "https://kyma.xyz/pop/monolith2026.json" \
 *      --is-collection true --seller-fee-basis-points 0
 *    ```
 *    → Copy the mint address into [KYMA_COLLECTION_MINT]
 *
 * 4. Rebuild the app — [isPlaceholder] will return false and minting will be enabled.
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

    /** System Program placeholder used before real addresses are deployed. */
    private const val SYSTEM_PROGRAM_PLACEHOLDER = "11111111111111111111111111111111"

    /** @return true if tree or collection mint are still placeholder addresses. */
    val isPlaceholder: Boolean
        get() = KYMA_CNFT_TREE == SYSTEM_PROGRAM_PLACEHOLDER ||
                KYMA_COLLECTION_MINT == SYSTEM_PROGRAM_PLACEHOLDER
}
