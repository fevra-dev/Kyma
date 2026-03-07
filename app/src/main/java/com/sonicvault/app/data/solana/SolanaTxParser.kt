package com.sonicvault.app.data.solana

import com.sonicvault.app.logging.SonicVaultLogger
import io.github.novacrypto.base58.Base58

/**
 * Parses Solana legacy transaction wire format to extract transfer details.
 *
 * Wire format: [signatures] [message]
 * - Signatures: compact-u16 length + N×64 bytes
 * - Message: header(3) + account_keys + blockhash(32) + instructions
 *
 * System Program transfer: program_id_index, accounts=[from_idx, to_idx], data=4B discriminator(2)+8B lamports
 */
object SolanaTxParser {

    /** System Program instruction discriminators (little-endian u32). */
    private const val TRANSFER_INSTRUCTION = 2
    private const val ADVANCE_NONCE_INSTRUCTION = 4

    /** Minimum size: 1 sig (64B) + header(3) + 2 accounts (64B) + blockhash(32) + 1 instruction */
    private const val MIN_TX_SIZE = 1 + 64 + 3 + 2 + 64 + 32 + 5

    /** Default fee for display when not parsed (simple transfer ~5000 lamports). */
    private const val DEFAULT_FEE_LAMPORTS = 5000L

    data class TransferDetails(
        val from: String,
        val to: String,
        val lamports: Long
    )

    /**
     * Parses transfer details from serialized transaction bytes.
     *
     * @return TransferDetails if a System Program transfer is found, null otherwise
     */
    fun parseTransferDetails(bytes: ByteArray): TransferDetails? {
        if (bytes.size < MIN_TX_SIZE) return null
        return try {
            var pos = 0

            // Skip signatures: compact-u16 length + N×64 bytes
            val (numSigs, sigLen) = readCompactU16(bytes, pos)
            pos += sigLen
            if (numSigs < 0 || numSigs > 16) return null
            pos += numSigs * 64
            if (pos > bytes.size) return null

            // Message header
            if (pos + 3 > bytes.size) return null
            pos += 3

            // Account keys: compact-u16 length + N×32 bytes
            val (numAccounts, accLen) = readCompactU16(bytes, pos)
            pos += accLen
            if (numAccounts < 2 || numAccounts > 64) return null
            val accountKeysStart = pos
            val accountKeysEnd = pos + numAccounts * 32
            if (accountKeysEnd > bytes.size) return null
            pos = accountKeysEnd

            // Blockhash
            pos += 32
            if (pos > bytes.size) return null

            // Instructions: compact-u16 length
            val (numInstructions, instLen) = readCompactU16(bytes, pos)
            pos += instLen
            if (numInstructions < 1) return null

            for (_i in 0 until numInstructions) {
                if (pos >= bytes.size) break
                @Suppress("UNUSED_VARIABLE") val programIdIdx = bytes[pos].toInt() and 0xFF
                pos++
                val (numAccountsInInst, accInstLen) = readCompactU16(bytes, pos)
                pos += accInstLen
                val (dataLen, dataLenBytes) = readCompactU16(bytes, pos)
                pos += dataLenBytes
                if (pos + numAccountsInInst + dataLen > bytes.size) break
                val accountIndices = bytes.copyOfRange(pos, pos + numAccountsInInst)
                pos += numAccountsInInst
                val data = bytes.copyOfRange(pos, pos + dataLen)
                pos += dataLen

                // System Program transfer: program_id_index typically 2 (fee payer + recipient + System Program)
                if (numAccountsInInst >= 2 && dataLen >= 12) {
                    val discriminator = data[0].toInt() and 0xFF or
                        ((data[1].toInt() and 0xFF) shl 8) or
                        ((data[2].toInt() and 0xFF) shl 16) or
                        ((data[3].toInt() and 0xFF) shl 24)
                    if (discriminator == TRANSFER_INSTRUCTION) {
                        val fromIdx = accountIndices[0].toInt() and 0xFF
                        val toIdx = accountIndices[1].toInt() and 0xFF
                        if (fromIdx < numAccounts && toIdx < numAccounts) {
                            val fromKey = bytes.copyOfRange(
                                accountKeysStart + fromIdx * 32,
                                accountKeysStart + fromIdx * 32 + 32
                            )
                            val toKey = bytes.copyOfRange(
                                accountKeysStart + toIdx * 32,
                                accountKeysStart + toIdx * 32 + 32
                            )
                            val lamports = (data[4].toLong() and 0xFF) or
                                ((data[5].toLong() and 0xFF) shl 8) or
                                ((data[6].toLong() and 0xFF) shl 16) or
                                ((data[7].toLong() and 0xFF) shl 24) or
                                ((data[8].toLong() and 0xFF) shl 32) or
                                ((data[9].toLong() and 0xFF) shl 40) or
                                ((data[10].toLong() and 0xFF) shl 48) or
                                ((data[11].toLong() and 0xFF) shl 56)
                            return TransferDetails(
                                from = Base58.base58Encode(fromKey),
                                to = Base58.base58Encode(toKey),
                                lamports = lamports
                            )
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            SonicVaultLogger.w("[SolanaTxParser] parse failed: ${e.message}")
            null
        }
    }

    /**
     * Reads compact-u16 from Solana wire format.
     * @return Pair(value, bytesRead)
     */
    private fun readCompactU16(data: ByteArray, offset: Int): Pair<Int, Int> {
        var pos = offset
        var result = 0
        var shift = 0
        repeat(3) {
            if (pos >= data.size) return Pair(0, 0)
            val b = data[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return Pair(result, pos - offset)
            shift += 7
        }
        return Pair(result, pos - offset)
    }

    /**
     * Parses transaction for ColdSignerScreen display.
     *
     * @return ParsedTransaction with from, to, amount, fee, isDurableNonce; null if parse fails
     */
    fun parseForDisplay(bytes: ByteArray): ParsedTransaction? {
        if (bytes.size < MIN_TX_SIZE) return null
        return try {
            var pos = 0
            val (numSigs, sigLen) = readCompactU16(bytes, pos)
            pos += sigLen
            if (numSigs < 0 || numSigs > 16) return null
            pos += numSigs * 64
            if (pos > bytes.size) return null
            if (pos + 3 > bytes.size) return null
            pos += 3
            val (numAccounts, accLen) = readCompactU16(bytes, pos)
            pos += accLen
            if (numAccounts < 2 || numAccounts > 64) return null
            val accountKeysStart = pos
            val accountKeysEnd = pos + numAccounts * 32
            if (accountKeysEnd > bytes.size) return null
            pos = accountKeysEnd
            pos += 32  // blockhash
            if (pos > bytes.size) return null
            val (numInstructions, instLen) = readCompactU16(bytes, pos)
            pos += instLen
            if (numInstructions < 1) return null

            var isDurableNonce = false
            var fromAddress = ""
            var toAddress = ""
            var amountLamports = 0L

            for (i in 0 until numInstructions) {
                if (pos >= bytes.size) break
                pos++  // programIdIdx
                val (numAccountsInInst, accInstLen) = readCompactU16(bytes, pos)
                pos += accInstLen
                val (dataLen, dataLenBytes) = readCompactU16(bytes, pos)
                pos += dataLenBytes
                if (pos + numAccountsInInst + dataLen > bytes.size) break
                val accountIndices = bytes.copyOfRange(pos, pos + numAccountsInInst)
                pos += numAccountsInInst
                val data = bytes.copyOfRange(pos, pos + dataLen)
                pos += dataLen

                if (dataLen >= 4) {
                    val discriminator = data[0].toInt() and 0xFF or
                        ((data[1].toInt() and 0xFF) shl 8) or
                        ((data[2].toInt() and 0xFF) shl 16) or
                        ((data[3].toInt() and 0xFF) shl 24)
                    when (discriminator) {
                        ADVANCE_NONCE_INSTRUCTION -> if (i == 0) isDurableNonce = true
                        TRANSFER_INSTRUCTION -> if (numAccountsInInst >= 2 && dataLen >= 12) {
                            val fromIdx = accountIndices[0].toInt() and 0xFF
                            val toIdx = accountIndices[1].toInt() and 0xFF
                            if (fromIdx < numAccounts && toIdx < numAccounts) {
                                fromAddress = Base58.base58Encode(
                                    bytes.copyOfRange(accountKeysStart + fromIdx * 32, accountKeysStart + fromIdx * 32 + 32)
                                )
                                toAddress = Base58.base58Encode(
                                    bytes.copyOfRange(accountKeysStart + toIdx * 32, accountKeysStart + toIdx * 32 + 32)
                                )
                                amountLamports = (data[4].toLong() and 0xFF) or
                                    ((data[5].toLong() and 0xFF) shl 8) or
                                    ((data[6].toLong() and 0xFF) shl 16) or
                                    ((data[7].toLong() and 0xFF) shl 24) or
                                    ((data[8].toLong() and 0xFF) shl 32) or
                                    ((data[9].toLong() and 0xFF) shl 40) or
                                    ((data[10].toLong() and 0xFF) shl 48) or
                                    ((data[11].toLong() and 0xFF) shl 56)
                            }
                        }
                    }
                }
            }
            if (fromAddress.isBlank() || toAddress.isBlank()) return null
            ParsedTransaction(
                fromAddress = fromAddress,
                toAddress = toAddress,
                amountLamports = amountLamports,
                feeLamports = DEFAULT_FEE_LAMPORTS,
                isDurableNonce = isDurableNonce
            )
        } catch (e: Exception) {
            SonicVaultLogger.w("[SolanaTxParser] parseForDisplay failed: ${e.message}")
            null
        }
    }

    /** Truncates base58 address for display (first 6 + … + last 4). */
    fun truncateAddress(address: String): String {
        if (address.length <= 6 + 4) return address
        return "${address.take(6)}…${address.takeLast(4)}"
    }
}

/**
 * Human-readable transaction fields for the ColdSignerScreen UI.
 */
data class ParsedTransaction(
    val fromAddress: String,
    val toAddress: String,
    val amountLamports: Long,
    val feeLamports: Long,
    val isDurableNonce: Boolean
) {
    val amountSol: Double get() = amountLamports / 1_000_000_000.0
    val feeSol: Double get() = feeLamports / 1_000_000_000.0
    val fromTruncated: String get() = "${fromAddress.take(4)}…${fromAddress.takeLast(4)}"
    val toTruncated: String get() = "${toAddress.take(4)}…${toAddress.takeLast(4)}"
}
