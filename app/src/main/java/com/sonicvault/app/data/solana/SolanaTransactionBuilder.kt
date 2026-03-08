package com.sonicvault.app.data.solana

import com.solana.core.AccountMeta
import com.solana.core.HotAccount
import com.solana.core.PublicKey
import com.solana.core.SignaturePubkeyPair
import com.solana.core.Transaction
import com.solana.programs.Program
import com.solana.programs.SystemProgram
import com.solana.programs.TokenProgram
import com.sonicvault.app.data.solana.GovernanceConstants
import com.sonicvault.app.logging.SonicVaultLogger
import org.bitcoinj.core.Utils
import java.util.ArrayList

/**
 * Builds Solana SOL transfer transactions for SonicRequest and SonicSafe.
 *
 * Uses SolanaKT (Transaction, SystemProgram.transfer). Produces unsigned transaction
 * message for external signing (Seed Vault / MWA).
 *
 * LAMPORTS_PER_SOL = 1_000_000_000
 *
 * Durable nonce: buildDurableNonceTx adds NonceAdvance as first instruction.
 * SolanaKT SystemProgram lacks nonce instructions; we build them manually.
 */
object SolanaTransactionBuilder {

    const val LAMPORTS_PER_SOL = 1_000_000_000L

    /** System Program instruction indices (Solana system_instruction.rs). */
    private const val IX_CREATE_ACCOUNT = 0
    private const val IX_TRANSFER = 2
    private const val IX_ADVANCE_NONCE = 4
    private const val IX_INITIALIZE_NONCE = 6

    /** Nonce account size in bytes (version + state + authority + blockhash). */
    private const val NONCE_ACCOUNT_SIZE = 80

    /** Sysvar addresses for nonce instructions. Canonical: B1ock uses "1" not "l" per Solana base58. */
    private val SYSVAR_RECENT_BLOCKHASHES = PublicKey("SysvarRecentB1ockHashes11111111111111111111")
    private val SYSVAR_RENT = PublicKey("SysvarRent111111111111111111111111111111111")

    /**
     * Builds an unsigned SOL transfer transaction.
     *
     * @param fromPubkey fee payer / sender (base58)
     * @param toPubkey recipient (base58)
     * @param lamports amount in lamports
     * @param blockhash recent blockhash from RPC
     * @return Unsigned transaction with placeholder signature, or null on failure
     */
    fun buildSolTransfer(
        fromPubkey: String,
        toPubkey: String,
        lamports: Long,
        blockhash: String
    ): Transaction? {
        return try {
            val from = PublicKey(fromPubkey)
            val to = PublicKey(toPubkey)

            val tx = Transaction()
            tx.add(SystemProgram.transfer(from, to, lamports))
            tx.setRecentBlockHash(blockhash)
            tx.feePayer = from

            // Add placeholder for fee payer signature (required for compile)
            tx.signatures.add(SignaturePubkeyPair(null, from))

            SonicVaultLogger.d("[SolanaTxBuilder] built transfer ${lamports} lamports")
            tx
        } catch (e: Exception) {
            SonicVaultLogger.e("[SolanaTxBuilder] build failed", e)
            null
        }
    }

    /**
     * Builds an unsigned SOL transfer with reference keys (read-only non-signers).
     * Used for Solana Pay: references enable payment tracking by merchant.
     *
     * @param references base58 pubkeys appended as read-only AccountMeta
     */
    fun buildSolTransferWithReferences(
        fromPubkey: String,
        toPubkey: String,
        lamports: Long,
        blockhash: String,
        references: List<String>
    ): Transaction? {
        return try {
            val from = PublicKey(fromPubkey)
            val to = PublicKey(toPubkey)
            val keys = ArrayList<AccountMeta>()
            keys.add(AccountMeta(from, true, true))
            keys.add(AccountMeta(to, false, true))
            references.forEach { ref ->
                keys.add(AccountMeta(PublicKey(ref), false, false))
            }
            val data = ByteArray(4 + 8)
            Utils.uint32ToByteArrayLE(IX_TRANSFER.toLong(), data, 0)
            writeU64LE(lamports, data, 4)
            val transferIx = Program.createTransactionInstruction(SystemProgram.PROGRAM_ID, keys, data)
            val tx = Transaction()
            tx.add(transferIx)
            tx.setRecentBlockHash(blockhash)
            tx.feePayer = from
            tx.signatures.add(SignaturePubkeyPair(null, from))
            SonicVaultLogger.d("[SolanaTxBuilder] built transfer with ${references.size} references")
            tx
        } catch (e: Exception) {
            SonicVaultLogger.e("[SolanaTxBuilder] buildSolTransferWithReferences failed", e)
            null
        }
    }

    /**
     * Derives the Associated Token Account address for (owner, mint).
     */
    fun deriveAssociatedTokenAddress(owner: String, mint: String): PublicKey? {
        return try {
            PublicKey.associatedTokenAddress(PublicKey(owner), PublicKey(mint)).address
        } catch (e: Exception) {
            SonicVaultLogger.e("[SolanaTxBuilder] deriveAssociatedTokenAddress failed", e)
            null
        }
    }

    /**
     * Builds an unsigned SKR token transfer (TransferChecked).
     *
     * @param fromPubkey owner of source ATA (base58)
     * @param toPubkey recipient wallet (base58); recipient ATA derived
     * @param amountBaseUnits amount in base units (amount * 10^decimals)
     * @param blockhash recent blockhash
     * @param references optional read-only account keys for payment tracking
     */
    fun buildSkrTransfer(
        fromPubkey: String,
        toPubkey: String,
        amountBaseUnits: Long,
        blockhash: String,
        references: List<String> = emptyList()
    ): Transaction? {
        return try {
            val from = PublicKey(fromPubkey)
            val to = PublicKey(toPubkey)
            val mint = PublicKey(SkrConstants.SKR_MINT)
            val senderAta = deriveAssociatedTokenAddress(fromPubkey, SkrConstants.SKR_MINT)
                ?: return null
            val recipientAta = deriveAssociatedTokenAddress(toPubkey, SkrConstants.SKR_MINT)
                ?: return null
            val transferIx = TokenProgram.transferChecked(
                source = senderAta,
                destination = recipientAta,
                amount = amountBaseUnits,
                decimals = SkrConstants.SKR_DECIMALS.toByte(),
                owner = from,
                tokenMint = mint
            )
            val keys = transferIx.keys.toMutableList()
            references.forEach { ref ->
                keys.add(AccountMeta(PublicKey(ref), false, false))
            }
            val transferIxWithRefs = Program.createTransactionInstruction(
                TokenProgram.PROGRAM_ID,
                keys,
                transferIx.data
            )
            val tx = Transaction()
            tx.add(transferIxWithRefs)
            tx.setRecentBlockHash(blockhash)
            tx.feePayer = from
            tx.signatures.add(SignaturePubkeyPair(null, from))
            SonicVaultLogger.d("[SolanaTxBuilder] built SKR transfer $amountBaseUnits base units")
            tx
        } catch (e: Exception) {
            SonicVaultLogger.e("[SolanaTxBuilder] buildSkrTransfer failed", e)
            null
        }
    }

    /**
     * Builds create-associated-token-account instruction (idempotent).
     * Use before SKR transfer when recipient may not have an ATA.
     * Instruction index 1 = CreateIdempotent (succeeds if account already exists).
     */
    fun buildCreateAtaIfNeeded(
        payer: String,
        owner: String,
        mint: String
    ): com.solana.core.TransactionInstruction? {
        return try {
            val payerKey = PublicKey(payer)
            val ownerKey = PublicKey(owner)
            val mintKey = PublicKey(mint)
            val ata = deriveAssociatedTokenAddress(owner, mint) ?: return null
            val keys = listOf(
                AccountMeta(payerKey, true, true),
                AccountMeta(ata, false, true),
                AccountMeta(ownerKey, false, false),
                AccountMeta(mintKey, false, false),
                AccountMeta(SystemProgram.PROGRAM_ID, false, false),
                AccountMeta(TokenProgram.PROGRAM_ID, false, false),
                AccountMeta(com.solana.programs.TokenProgram.SYSVAR_RENT_PUBKEY, false, false)
            )
            Program.createTransactionInstruction(
                PublicKey(SkrConstants.ATA_PROGRAM_ID),
                keys,
                byteArrayOf(1) // CreateIdempotent instruction index
            )
        } catch (e: Exception) {
            SonicVaultLogger.e("[SolanaTxBuilder] buildCreateAtaIfNeeded failed", e)
            null
        }
    }

    /**
     * Builds an unsigned durable nonce SOL transfer for SonicSafe cold signing.
     *
     * Instruction layout: ix[0] = NonceAdvance, ix[1] = Transfer.
     * recentBlockhash = nonceValue (durable nonce from chain).
     * Fee payer = cold wallet; cold signs via Seed Vault. Nonce authority (hot) must
     * also sign NonceAdvance — caller must handle MWA signTransactions.
     *
     * @param nonceAccountPubkey base58 nonce account
     * @param nonceAuthorityPubkey base58 hot wallet (nonce authority)
     * @param feePayerPubkey base58 cold wallet (fee payer, from)
     * @param toPubkey base58 recipient
     * @param lamports amount
     * @param nonceValue current nonce value (base58) from nonce account
     * @return Unsigned Transaction or null on failure
     */
    fun buildDurableNonceTx(
        nonceAccountPubkey: String,
        nonceAuthorityPubkey: String,
        feePayerPubkey: String,
        toPubkey: String,
        lamports: Long,
        nonceValue: String
    ): Transaction? {
        return try {
            val nonce = PublicKey(nonceAccountPubkey)
            val authority = PublicKey(nonceAuthorityPubkey)
            val from = PublicKey(feePayerPubkey)
            val to = PublicKey(toPubkey)

            val nonceAdvanceIx = buildNonceAdvanceInstruction(nonce, authority)
            val transferIx = SystemProgram.transfer(from, to, lamports)

            val tx = Transaction()
            tx.add(nonceAdvanceIx)
            tx.add(transferIx)
            tx.setRecentBlockHash(nonceValue)
            tx.feePayer = from
            tx.signatures.add(SignaturePubkeyPair(null, from))

            SonicVaultLogger.d("[SolanaTxBuilder] built durable nonce transfer ${lamports} lamports")
            tx
        } catch (e: Exception) {
            SonicVaultLogger.e("[SolanaTxBuilder] buildDurableNonceTx failed", e)
            null
        }
    }

    /** Writes 8-byte little-endian u64 at offset. */
    private fun writeU64LE(value: Long, data: ByteArray, offset: Int) {
        for (i in 0..7) {
            data[offset + i] = ((value shr (i * 8)) and 0xFF).toByte()
        }
    }

    /**
     * AdvanceNonceAccount instruction: accounts = [nonce writable, recent_blockhashes, authority signer].
     * Data = 4-byte u32 = 4 (instruction index).
     */
    private fun buildNonceAdvanceInstruction(nonce: PublicKey, authority: PublicKey): com.solana.core.TransactionInstruction {
        val keys = ArrayList<AccountMeta>()
        keys.add(AccountMeta(nonce, false, true))   // writable
        keys.add(AccountMeta(SYSVAR_RECENT_BLOCKHASHES, false, false))
        keys.add(AccountMeta(authority, true, false))  // signer
        val data = ByteArray(4)
        Utils.uint32ToByteArrayLE(IX_ADVANCE_NONCE.toLong(), data, 0)
        return Program.createTransactionInstruction(SystemProgram.PROGRAM_ID, keys, data)
    }

    /**
     * Builds an unsigned create-nonce-account transaction.
     *
     * TX: CreateAccount(space=80) + InitializeNonce(authority).
     * Signers: payer (via MWA) and new nonce account (sign with nonceAccount).
     *
     * @param payerPubkey base58 payer (fee payer)
     * @param authorityPubkey base58 nonce authority (typically same as payer for SonicSafe)
     * @param rentLamports amount for rent exemption (from getMinimumBalanceForRentExemption(80))
     * @param blockhash recent blockhash
     * @return CreateNonceAccountResult with tx, nonceAccount (for signing), or null on failure
     */
    fun buildCreateNonceAccountTx(
        payerPubkey: String,
        authorityPubkey: String,
        rentLamports: Long,
        blockhash: String
    ): CreateNonceAccountResult? {
        return try {
            val nonceAccount = HotAccount()
            val noncePubkey = nonceAccount.publicKey
            val payer = PublicKey(payerPubkey)
            val authority = PublicKey(authorityPubkey)

            val createAccountIx = buildCreateAccountInstruction(payer, noncePubkey, rentLamports)
            val initNonceIx = buildInitializeNonceInstruction(noncePubkey, authority)

            val tx = Transaction()
            tx.add(createAccountIx)
            tx.add(initNonceIx)
            tx.setRecentBlockHash(blockhash)
            tx.feePayer = payer
            tx.signatures.add(SignaturePubkeyPair(null, payer))
            tx.signatures.add(SignaturePubkeyPair(null, noncePubkey))

            SonicVaultLogger.d("[SolanaTxBuilder] built createNonceAccount tx")
            CreateNonceAccountResult(tx, nonceAccount)
        } catch (e: Exception) {
            SonicVaultLogger.e("[SolanaTxBuilder] buildCreateNonceAccountTx failed", e)
            null
        }
    }

    /**
     * CreateAccount instruction: accounts = [payer signer, newAccount signer].
     * Data = 4B instruction index + 8B lamports + 8B space + 32B owner (SystemProgram).
     */
    private fun buildCreateAccountInstruction(
        payer: PublicKey,
        newAccount: PublicKey,
        lamports: Long
    ): com.solana.core.TransactionInstruction {
        val keys = ArrayList<AccountMeta>()
        keys.add(AccountMeta(payer, true, true))
        keys.add(AccountMeta(newAccount, true, true))
        val data = ByteArray(4 + 8 + 8 + 32)
        Utils.uint32ToByteArrayLE(IX_CREATE_ACCOUNT.toLong(), data, 0)
        writeU64LE(lamports, data, 4)
        writeU64LE(NONCE_ACCOUNT_SIZE.toLong(), data, 12)
        SystemProgram.PROGRAM_ID.toByteArray().copyInto(data, 20)
        return Program.createTransactionInstruction(SystemProgram.PROGRAM_ID, keys, data)
    }

    /**
     * InitializeNonce instruction: accounts = [nonce writable, recent_blockhashes, rent].
     * Data = 32-byte authority pubkey.
     */
    private fun buildInitializeNonceInstruction(nonce: PublicKey, authority: PublicKey): com.solana.core.TransactionInstruction {
        val keys = ArrayList<AccountMeta>()
        keys.add(AccountMeta(nonce, false, true))
        keys.add(AccountMeta(SYSVAR_RECENT_BLOCKHASHES, false, false))
        keys.add(AccountMeta(SYSVAR_RENT, false, false))
        val data = ByteArray(4 + 32)
        Utils.uint32ToByteArrayLE(IX_INITIALIZE_NONCE.toLong(), data, 0)
        authority.toByteArray().copyInto(data, 4)
        return Program.createTransactionInstruction(SystemProgram.PROGRAM_ID, keys, data)
    }

    /**
     * Verifies that the first instruction of a deserialized transaction is NonceAdvance.
     * Used on the cold signer device to show the "Durable Nonce — No Expiry" badge.
     */
    fun isDurableNonceTx(txBytes: ByteArray): Boolean {
        return SolanaTxParser.parseForDisplay(txBytes)?.isDurableNonce == true
    }

    /**
     * Serializes the transaction message (bytes to be signed).
     * Call after building; the transaction must have been compiled.
     */
    fun serializeMessage(tx: Transaction): ByteArray? {
        return try {
            tx.serializeMessage()
        } catch (e: Exception) {
            SonicVaultLogger.e("[SolanaTxBuilder] serializeMessage failed", e)
            null
        }
    }

    /**
     * Serializes the unsigned transaction for MWA signTransactions.
     * Uses zeroed signature placeholders.
     */
    fun serializeForSigning(tx: Transaction): ByteArray? {
        return try {
            tx.serialize(com.solana.core.SerializeConfig(verifySignatures = false))
        } catch (e: Exception) {
            SonicVaultLogger.e("[SolanaTxBuilder] serializeForSigning failed", e)
            null
        }
    }

    /**
     * Reconstructs a signed transaction from unsigned bytes by replacing the 64-byte
     * signature placeholder at bytes 1..64 with the real Ed25519 signature.
     *
     * Used for cold-sign optimization: cold transmits only 64-byte sig; hot reconstructs
     * full signed TX from stored unsigned bytes.
     *
     * @param unsignedBytes serialized unsigned TX (with zeroed sig at 1..64)
     * @param signature 64-byte Ed25519 signature from cold signer
     * @return signed transaction bytes for sendTransaction RPC, or null if invalid
     */
    fun reconstructSignedTx(unsignedBytes: ByteArray, signature: ByteArray): ByteArray? {
        if (signature.size != 64 || unsignedBytes.size < 65) return null
        return try {
            val result = unsignedBytes.copyOf()
            signature.copyInto(result, 1, 0, 64)
            SonicVaultLogger.d("[SolanaTxBuilder] reconstructed signed TX ${result.size} bytes")
            result
        } catch (e: Exception) {
            SonicVaultLogger.e("[SolanaTxBuilder] reconstructSignedTx failed", e)
            null
        }
    }

    /**
     * Builds an unsigned SPL Memo transaction for Guardian Voting demo.
     * Memo content: "VOTE:{proposalBase58}:{direction}" — proves voter presence and intent.
     *
     * @param payerPubkey base58 fee payer (voter)
     * @param proposalPubkey base58 proposal address
     * @param direction YES/NO/ABSTAIN
     * @param blockhash recent blockhash from RPC
     */
    fun buildVoteMemoTx(
        payerPubkey: String,
        proposalPubkey: String,
        direction: GovernanceConstants.VoteDirection,
        blockhash: String
    ): Transaction? {
        return try {
            val payer = PublicKey(payerPubkey)
            val memo = "VOTE:$proposalPubkey:${direction.name}"
            val memoBytes = memo.toByteArray(Charsets.UTF_8)
            val memoProgram = PublicKey(GovernanceConstants.SPL_MEMO_PROGRAM_ID)
            val ix = Program.createTransactionInstruction(memoProgram, ArrayList(), memoBytes)
            val tx = Transaction()
            tx.add(ix)
            tx.setRecentBlockHash(blockhash)
            tx.feePayer = payer
            tx.signatures.add(SignaturePubkeyPair(null, payer))
            SonicVaultLogger.d("[SolanaTxBuilder] built vote memo: ${memo.take(48)}…")
            tx
        } catch (e: Exception) {
            SonicVaultLogger.e("[SolanaTxBuilder] buildVoteMemoTx failed", e)
            null
        }
    }

    /**
     * Adds the signature to the transaction and serializes for RPC submission.
     *
     * @param tx the transaction (from buildSolTransfer)
     * @param signature 64-byte Ed25519 signature
     * @return signed transaction bytes for sendTransaction RPC (base64)
     */
    fun addSignatureAndSerialize(tx: Transaction, signature: ByteArray): ByteArray? {
        return try {
            require(signature.size == 64) { "Signature must be 64 bytes" }
            val feePayer = tx.feePayer ?: return null
            tx.addSignature(feePayer, signature)
            tx.serialize(com.solana.core.SerializeConfig(verifySignatures = false))
        } catch (e: Exception) {
            SonicVaultLogger.e("[SolanaTxBuilder] addSignature failed", e)
            null
        }
    }
}

/**
 * Result of buildCreateNonceAccountTx.
 *
 * @param tx unsigned transaction (CreateAccount + InitializeNonce)
 * @param nonceAccount HotAccount for the new nonce account; caller must sign with it before MWA
 */
data class CreateNonceAccountResult(
    val tx: com.solana.core.Transaction,
    val nonceAccount: com.solana.core.HotAccount
)
