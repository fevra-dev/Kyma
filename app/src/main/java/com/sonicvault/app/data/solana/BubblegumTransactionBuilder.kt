package com.sonicvault.app.data.solana

import com.solana.core.AccountMeta
import com.solana.core.PublicKey
import com.solana.core.SignaturePubkeyPair
import com.solana.core.Transaction
import com.solana.programs.Program
import com.solana.programs.SystemProgram
import com.sonicvault.app.logging.SonicVaultLogger
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds Bubblegum mintToCollectionV1 transactions for cNFT acoustic airdrop.
 *
 * Requires pre-created tree and collection on devnet. Tree/collection authority must sign;
 * for self-service, use a drop authority backend or pre-delegated collection authority.
 *
 * PDAs: treeAuthority, collectionMetadata, editionAccount, bubblegumSigner.
 */
object BubblegumTransactionBuilder {

    private val BUBBLEGUM_PROGRAM = PublicKey(BubblegumConstants.BUBBLEGUM_PROGRAM_ID)
    private val SPL_COMPRESSION = PublicKey(BubblegumConstants.SPL_COMPRESSION_ID)
    private val SPL_NOOP = PublicKey(BubblegumConstants.SPL_NOOP_ID)
    private val TOKEN_METADATA = PublicKey(BubblegumConstants.TOKEN_METADATA_ID)
    private val SYSTEM_PROGRAM = PublicKey(BubblegumConstants.SYSTEM_PROGRAM_ID)

    /**
     * Anchor instruction discriminant for mint_to_collection_v1.
     * First 8 bytes of sha256("global:mint_to_collection_v1").
     */
    private val MINT_TO_COLLECTION_V1_DISCRIMINATOR = byteArrayOf(
        0x99.toByte(), 0x12, 0xb2.toByte(), 0x2f, 0xc5.toByte(), 0x9e.toByte(), 0x56, 0x0f
    )

    /**
     * Derives tree authority PDA from merkle tree.
     * Seeds: [merkleTree]
     */
    fun deriveTreeAuthority(merkleTree: PublicKey): PublicKey? {
        return try {
            val pda = PublicKey.findProgramAddress(
                listOf(merkleTree.toByteArray()),
                BUBBLEGUM_PROGRAM
            )
            pda.address
        } catch (e: Exception) {
            SonicVaultLogger.e("[BubblegumTxBuilder] deriveTreeAuthority failed", e)
            null
        }
    }

    /**
     * Derives collection metadata PDA from collection mint.
     * Seeds: ["metadata", TOKEN_METADATA_ID, collectionMint]
     */
    fun deriveCollectionMetadata(collectionMint: PublicKey): PublicKey? {
        return try {
            val pda = PublicKey.findProgramAddress(
                listOf(
                    "metadata".toByteArray(),
                    TOKEN_METADATA.toByteArray(),
                    collectionMint.toByteArray()
                ),
                TOKEN_METADATA
            )
            pda.address
        } catch (e: Exception) {
            SonicVaultLogger.e("[BubblegumTxBuilder] deriveCollectionMetadata failed", e)
            null
        }
    }

    /**
     * Derives master edition PDA from collection mint.
     * Seeds: ["metadata", TOKEN_METADATA_ID, collectionMint, "edition"]
     */
    fun deriveEditionAccount(collectionMint: PublicKey): PublicKey? {
        return try {
            val pda = PublicKey.findProgramAddress(
                listOf(
                    "metadata".toByteArray(),
                    TOKEN_METADATA.toByteArray(),
                    collectionMint.toByteArray(),
                    "edition".toByteArray()
                ),
                TOKEN_METADATA
            )
            pda.address
        } catch (e: Exception) {
            SonicVaultLogger.e("[BubblegumTxBuilder] deriveEditionAccount failed", e)
            null
        }
    }

    /**
     * Derives bubblegum signer PDA.
     * Seeds: ["collection_cpi"]
     */
    fun deriveBubblegumSigner(): PublicKey? {
        return try {
            val pda = PublicKey.findProgramAddress(
                listOf("collection_cpi".toByteArray()),
                BUBBLEGUM_PROGRAM
            )
            pda.address
        } catch (e: Exception) {
            SonicVaultLogger.e("[BubblegumTxBuilder] deriveBubblegumSigner failed", e)
            null
        }
    }

    /**
     * Builds mintToCollectionV1 instruction data (discriminator + Borsh MetadataArgs).
     *
     * Borsh field sizes per Bubblegum IDL:
     *   sellerFeeBasisPoints: u16 (2), primarySaleHappened: bool (1), isMutable: bool (1),
     *   editionNonce: Option<u8> (1 for None), tokenStandard: Option<u8> (1 for None),
     *   collection: Option<Collection> (1+32+1), uses: Option<Uses> (1 for None),
     *   tokenProgramVersion: enum u8 (1), creators: Vec<Creator> length u32 (4) + per-creator (32+1+1).
     */
    private fun buildMetadataArgsBorsh(
        name: String,
        symbol: String,
        uri: String,
        collectionMint: PublicKey,
        creator: PublicKey
    ): ByteArray {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val symbolBytes = symbol.toByteArray(Charsets.UTF_8)
        val uriBytes = uri.toByteArray(Charsets.UTF_8)
        val size = 8 + 4 + nameBytes.size + 4 + symbolBytes.size + 4 + uriBytes.size +
            2 + 1 + 1 + 1 + 1 + (1 + 32 + 1) + 1 + 1 + 4 + (32 + 1 + 1)
        val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(MINT_TO_COLLECTION_V1_DISCRIMINATOR)
        buf.putInt(nameBytes.size)
        buf.put(nameBytes)
        buf.putInt(symbolBytes.size)
        buf.put(symbolBytes)
        buf.putInt(uriBytes.size)
        buf.put(uriBytes)
        buf.putShort(0)           // sellerFeeBasisPoints: u16
        buf.put(1)                // primarySaleHappened: bool = true
        buf.put(1)                // isMutable: bool = true
        buf.put(0)                // editionNonce: Option<u8> = None (1 byte)
        buf.put(0)                // tokenStandard: Option<u8> = None (1 byte)
        buf.put(1)                // collection: Option::Some
        buf.put(collectionMint.toByteArray()) // collection.key (32 bytes)
        buf.put(1)                // collection.verified: bool = true
        buf.put(0)                // uses: Option<Uses> = None
        buf.put(0)                // tokenProgramVersion: enum Original = 0 (1 byte)
        buf.putInt(1)             // creators vec length: u32
        buf.put(creator.toByteArray()) // creator address (32 bytes)
        buf.put(1)                // creator.verified: bool = true
        buf.put(100.toByte())     // creator.share: u8 = 100
        return buf.array()
    }

    /**
     * Builds unsigned mintToCollectionV1 transaction (simplified for demo).
     * Uses leafOwner as tree creator and collection authority — for when user owns the tree.
     *
     * @param leafOwner base58 receiver wallet (payer, tree creator, collection authority)
     * @param merkleTree base58 merkle tree address
     * @param collectionMint base58 collection NFT mint
     * @param blockhash recent blockhash from RPC
     */
    fun buildMintToCollectionV1(
        leafOwner: String,
        merkleTree: String,
        collectionMint: String,
        blockhash: String
    ): Transaction? = buildMintToCollectionV1(
        leafOwner, merkleTree, collectionMint, leafOwner, leafOwner, blockhash
    )

    /**
     * Builds unsigned mintToCollectionV1 transaction (full form).
     *
     * @param leafOwner base58 receiver wallet (payer = leafOwner for self-mint)
     * @param merkleTree base58 merkle tree address
     * @param collectionMint base58 collection NFT mint
     * @param treeCreatorOrDelegate base58 tree creator (must sign; use event key for public tree)
     * @param collectionAuthority base58 collection authority (must sign)
     * @param blockhash recent blockhash from RPC
     * @return Unsigned Transaction or null on failure; returns null if tree/collection not deployed
     */
    fun buildMintToCollectionV1(
        leafOwner: String,
        merkleTree: String,
        collectionMint: String,
        treeCreatorOrDelegate: String,
        collectionAuthority: String,
        blockhash: String
    ): Transaction? {
        if (BubblegumConstants.isPlaceholder) {
            SonicVaultLogger.e("[BubblegumTxBuilder] cNFT tree/collection not deployed — see BubblegumConstants for deployment steps")
            return null
        }
        return try {
            val leafOwnerKey = PublicKey(leafOwner)
            val merkleTreeKey = PublicKey(merkleTree)
            val collectionMintKey = PublicKey(collectionMint)
            val treeCreatorKey = PublicKey(treeCreatorOrDelegate)
            val collectionAuthorityKey = PublicKey(collectionAuthority)

            val treeAuthority = deriveTreeAuthority(merkleTreeKey) ?: return null
            val collectionMetadata = deriveCollectionMetadata(collectionMintKey) ?: return null
            val editionAccount = deriveEditionAccount(collectionMintKey) ?: return null
            val bubblegumSigner = deriveBubblegumSigner() ?: return null

            val collectionAuthorityRecordPda = try {
                PublicKey.findProgramAddress(
                    listOf(
                        "metadata".toByteArray(),
                        TOKEN_METADATA.toByteArray(),
                        collectionMintKey.toByteArray(),
                        "collection_authority".toByteArray(),
                        collectionAuthorityKey.toByteArray()
                    ),
                    TOKEN_METADATA
                ).address
            } catch (_: Exception) {
                BUBBLEGUM_PROGRAM
            }

            val ixData = buildMetadataArgsBorsh(
                BubblegumConstants.CNFT_NAME,
                BubblegumConstants.CNFT_SYMBOL,
                BubblegumConstants.CNFT_METADATA_URI,
                collectionMintKey,
                collectionAuthorityKey
            )

            val keys = listOf(
                AccountMeta(treeAuthority, false, true),
                AccountMeta(leafOwnerKey, false, false),
                AccountMeta(leafOwnerKey, false, false),
                AccountMeta(merkleTreeKey, false, true),
                AccountMeta(leafOwnerKey, true, true),
                AccountMeta(treeCreatorKey, true, false),
                AccountMeta(collectionAuthorityKey, true, false),
                AccountMeta(collectionAuthorityRecordPda, false, false),
                AccountMeta(collectionMintKey, false, false),
                AccountMeta(collectionMetadata, false, false),
                AccountMeta(editionAccount, false, false),
                AccountMeta(bubblegumSigner, false, false),
                AccountMeta(SPL_NOOP, false, false),
                AccountMeta(SPL_COMPRESSION, false, false),
                AccountMeta(TOKEN_METADATA, false, false),
                AccountMeta(SYSTEM_PROGRAM, false, false)
            )

            val ix = Program.createTransactionInstruction(BUBBLEGUM_PROGRAM, keys, ixData)
            val tx = Transaction()
            tx.add(ix)
            tx.setRecentBlockHash(blockhash)
            tx.feePayer = leafOwnerKey
            tx.signatures.add(SignaturePubkeyPair(null, leafOwnerKey))
            tx.signatures.add(SignaturePubkeyPair(null, treeCreatorKey))
            tx.signatures.add(SignaturePubkeyPair(null, collectionAuthorityKey))

            SonicVaultLogger.d("[BubblegumTxBuilder] built mintToCollectionV1 (requires tree/collection authority signers for on-chain success)")
            tx
        } catch (e: Exception) {
            SonicVaultLogger.e("[BubblegumTxBuilder] buildMintToCollectionV1 failed", e)
            null
        }
    }
}
