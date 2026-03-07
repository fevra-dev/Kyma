package com.sonicvault.app.data.shamir

import android.content.Context
import com.sonicvault.app.logging.SonicVaultLogger
import com.sonicvault.app.util.Bip39Entropy
import com.sonicvault.app.util.wipe
import java.math.BigInteger
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.SecretKeyFactory

/**
 * SLIP-0039 Shamir's Secret Sharing implementation.
 * Ported from ilap/slip39-js. Single-group T-of-N only (MVP).
 *
 * Flow: BIP39 phrase → entropy → encrypt (Feistel) → split (Shamir GF256) → encode (words)
 */
class Slip39ShamirImpl(
    private val context: Context,
    private val bip39Entropy: Bip39Entropy
) : Slip39Shamir {

    private val wordList: List<String> by lazy {
        context.assets.open(SLIP39_WORDLIST_PATH).bufferedReader().use { r ->
            r.readLines().map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        }.also { SonicVaultLogger.d("[Shamir] loaded ${it.size} SLIP39 words") }
    }

    private val wordToIndex: Map<String, Int> by lazy {
        wordList.mapIndexed { i, w -> w to i }.toMap()
    }

    override fun split(seedPhrase: String, threshold: Int, totalShares: Int): Result<List<String>> {
        SonicVaultLogger.i("[Shamir] split threshold=$threshold total=$totalShares")
        return runCatching {
            val entropy = bip39Entropy.phraseToEntropy(seedPhrase.trim())
                ?: throw IllegalArgumentException("Invalid BIP39 phrase")
            if (entropy.size != 16 && entropy.size != 32) {
                throw IllegalArgumentException("Entropy must be 128 or 256 bits")
            }
            val identifier = generateIdentifier()
            val encryptedMaster = crypt(entropy, "", 0, identifier, 0, true)
            val shares = splitSecret(threshold, totalShares, encryptedMaster)
            val passphrase = ""
            val extendableBackupFlag = 0
            val groupIndex = 0
            val groupThreshold = 1
            val groupCount = 1
            val mnemonics = shares.mapIndexed { memberIndex, value ->
                encodeMnemonic(
                    identifier, extendableBackupFlag, 0,
                    groupIndex, groupThreshold, groupCount,
                    memberIndex, threshold, value
                )
            }
            SonicVaultLogger.i("[Shamir] split produced ${mnemonics.size} shares")
            mnemonics
        }.onFailure { SonicVaultLogger.e("[Shamir] split failed", it) }
    }

    override fun combine(shares: List<String>): Result<String> {
        SonicVaultLogger.i("[Shamir] combine shares=${shares.size}")
        return runCatching {
            if (shares.isEmpty()) throw IllegalArgumentException("At least one share required")
            val masterSecret = combineMnemonics(shares, "")
            try {
                val phrase = bip39Entropy.entropyToPhrase(masterSecret)
                    ?: throw IllegalArgumentException("Failed to convert entropy to phrase")
                SonicVaultLogger.i("[Shamir] combine success")
                phrase
            } finally {
                masterSecret.wipe()
            }
        }.onFailure { SonicVaultLogger.e("[Shamir] combine failed", it) }
    }

    // --- SLIP-0039 internals ---

    private fun generateIdentifier(): ByteArray {
        val bytes = ByteArray(2)
        SecureRandom().nextBytes(bytes)
        bytes[0] = (bytes[0].toInt() and 0x7F).toByte()
        return bytes
    }

    private fun crypt(
        masterSecret: ByteArray,
        passphrase: String,
        iterationExponent: Int,
        identifier: ByteArray,
        extendableBackupFlag: Int,
        encrypt: Boolean
    ): ByteArray {
        val half = masterSecret.size / 2
        var il = masterSecret.copyOfRange(0, half)
        var ir = masterSecret.copyOfRange(half, masterSecret.size)
        val salt = if (extendableBackupFlag != 0) byteArrayOf() else
            CUSTOMIZATION_NON_EXTENDABLE.toByteArray(Charsets.UTF_8) + identifier
        val pwd = passphrase.toByteArray(Charsets.UTF_8)
        val range = if (encrypt) listOf(0, 1, 2, 3) else listOf(3, 2, 1, 0)
        for (round in range) {
            val f = roundFunction(round, pwd, iterationExponent, salt, ir)
            val t = xor(il, f)
            il = ir
            ir = t
        }
        return ir + il
    }

    private fun roundFunction(
        round: Int,
        passphrase: ByteArray,
        exp: Int,
        salt: ByteArray,
        secret: ByteArray
    ): ByteArray {
        val saltedSecret = salt + secret
        val roundedPhrase = byteArrayOf(round.toByte()) + passphrase
        val count = (ITERATION_COUNT shl exp) / ROUND_COUNT
        val spec = PBEKeySpec(
            roundedPhrase.map { it.toInt().toChar() }.toCharArray(),
            saltedSecret,
            count,
            secret.size * 8
        )
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
    }

    private fun xor(a: ByteArray, b: ByteArray): ByteArray {
        return ByteArray(a.size) { ((a[it].toInt() and 0xFF) xor (b[it].toInt() and 0xFF)).toByte() }
    }

    private fun createDigest(randomData: ByteArray, sharedSecret: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(randomData, "HmacSHA256"))
        return mac.doFinal(sharedSecret).copyOf(DIGEST_LENGTH)
    }

    private fun splitSecret(threshold: Int, shareCount: Int, sharedSecret: ByteArray): List<ByteArray> {
        if (threshold < 1) throw IllegalArgumentException("Threshold must be >= 1")
        if (threshold > shareCount) throw IllegalArgumentException("Threshold must not exceed share count")
        if (shareCount > MAX_SHARE_COUNT) throw IllegalArgumentException("Share count must not exceed $MAX_SHARE_COUNT")
        if (threshold == 1) return List(shareCount) { sharedSecret.copyOf() }
        val randomShareCount = threshold - 2
        val randomPart = ByteArray(sharedSecret.size - DIGEST_LENGTH).also { SecureRandom().nextBytes(it) }
        val digest = createDigest(randomPart, sharedSecret)
        val baseShares = mutableMapOf<Int, ByteArray>()
        for (i in 0 until randomShareCount) {
            val r = ByteArray(sharedSecret.size).also { SecureRandom().nextBytes(it) }
            baseShares[i] = r
        }
        baseShares[DIGEST_INDEX] = digest + randomPart
        baseShares[SECRET_INDEX] = sharedSecret
        val shares = mutableListOf<ByteArray>()
        for (i in 0 until randomShareCount) shares.add(baseShares[i]!!)
        for (i in randomShareCount until shareCount) {
            shares.add(interpolate(baseShares, i))
        }
        return shares
    }

    private fun interpolate(shares: Map<Int, ByteArray>, x: Int): ByteArray {
        if (shares.containsKey(x)) return shares[x]!!
        val length = shares.values.first().size
        var logProd = 0
        for (k in shares.keys) logProd += LOG_TABLE[k xor x]
        logProd = logProd % 255
        if (logProd < 0) logProd += 255
        val result = ByteArray(length)
        for (k in shares.keys) {
            var sum = 0
            for (kk in shares.keys) sum += LOG_TABLE[k xor kk]
            sum = sum % 255
            if (sum < 0) sum += 255
            var basis = (logProd - LOG_TABLE[k xor x] - sum) % 255
            if (basis < 0) basis += 255
            var logBasisEval = basis
            for (idx in 0 until length) {
                val shareVal = shares[k]!![idx].toInt() and 0xFF
                val r = if (shareVal != 0) EXP_TABLE[(LOG_TABLE[shareVal] + logBasisEval) % 255] else 0
                result[idx] = (result[idx].toInt() and 0xFF xor r).toByte()
            }
        }
        return result
    }

    private fun recoverSecret(threshold: Int, shares: Map<Int, ByteArray>): ByteArray {
        if (threshold == 1) return shares.values.first()
        val sharedSecret = interpolate(shares, SECRET_INDEX)
        val digestShare = interpolate(shares, DIGEST_INDEX)
        val digest = digestShare.copyOfRange(0, DIGEST_LENGTH)
        val randomPart = digestShare.copyOfRange(DIGEST_LENGTH, digestShare.size)
        val recovered = createDigest(randomPart, sharedSecret)
        if (!digest.contentEquals(recovered)) throw IllegalArgumentException("Invalid digest")
        return sharedSecret
    }

    private fun rs1024Polymod(data: List<Int>): Int {
        val gen = intArrayOf(
            0xE0E040, 0x1C1C080, 0x3838100, 0x7070200, 0xE0E0009, 0x1C0C2412,
            0x38086C24, 0x3090FC48, 0x21B1F890, 0x3F3F120
        )
        var chk = 1
        for (byte in data) {
            val b = chk shr 20
            chk = ((chk and 0xFFFFF) shl 10) xor byte
            for (i in 0 until 10) {
                val g = if ((b shr i) and 1 != 0) gen[i] else 0
                chk = chk xor g
            }
        }
        return chk
    }

    private fun rs1024CreateChecksum(data: List<Int>, extendableBackupFlag: Int): List<Int> {
        val custom = (if (extendableBackupFlag != 0) CUSTOMIZATION_EXTENDABLE else CUSTOMIZATION_NON_EXTENDABLE)
            .toByteArray(Charsets.UTF_8).map { it.toInt() and 0xFF }
        val values = custom + data + listOf(0, 0, 0)
        val polymod = rs1024Polymod(values) xor 1
        return listOf(
            (polymod shr 0) and 1023,
            (polymod shr 10) and 1023,
            (polymod shr 20) and 1023
        ).reversed()
    }

    private fun rs1024VerifyChecksum(data: List<Int>, extendableBackupFlag: Int): Boolean {
        val custom = (if (extendableBackupFlag != 0) CUSTOMIZATION_EXTENDABLE else CUSTOMIZATION_NON_EXTENDABLE)
            .toByteArray(Charsets.UTF_8).map { it.toInt() and 0xFF }
        return rs1024Polymod(custom + data) == 1
    }

    private fun intFromIndices(indices: List<Int>): BigInteger {
        var value = BigInteger.ZERO
        for (idx in indices) {
            value = value.multiply(RADIX_BIG).add(BigInteger.valueOf(idx.toLong()))
        }
        return value
    }

    private fun intToIndices(value: BigInteger, length: Int, bits: Int): List<Int> {
        val mask = (1 shl bits) - 1
        return (0 until length).map { i ->
            value.shiftRight(i * bits).and(BigInteger.valueOf(mask.toLong())).toInt()
        }.reversed()
    }

    private fun encodeMnemonic(
        identifier: ByteArray,
        extendableBackupFlag: Int,
        iterationExponent: Int,
        groupIndex: Int,
        groupThreshold: Int,
        groupCount: Int,
        memberIndex: Int,
        memberThreshold: Int,
        value: ByteArray
    ): String {
        val idExpInt = BigInteger(identifier + byteArrayOf(0)).toLong() shr 8
        val idExpCombined = (idExpInt shl (ITERATION_EXP_BITS + EXTENDABLE_BACKUP_FLAG_BITS)) or
            (extendableBackupFlag.toLong() shl ITERATION_EXP_BITS) or iterationExponent.toLong()
        val gp = intToIndices(BigInteger.valueOf(idExpCombined), ITERATION_EXP_WORDS_LENGTH, RADIX_BITS).toMutableList()
        val indc2 = ((groupIndex shl 6) + ((groupThreshold - 1) shl 2) + ((groupCount - 1) shr 2)).toInt()
        gp.add(indc2 shr 8)
        gp.add(indc2 and 0xFF)
        val valueWordCount = (value.size * 8 + RADIX_BITS - 1) / RADIX_BITS
        val valueInt = BigInteger(1, value)
        val tp = intToIndices(valueInt, valueWordCount, RADIX_BITS)
        val calc = (((groupCount - 1) and 3) shl 8) + (memberIndex shl 4) + (memberThreshold - 1)
        val calcHi = (calc shr 8) and 0xFF
        val calcLo = calc and 0xFF
        val shareData = gp + listOf(calcHi, calcLo) + tp
        val checksum = rs1024CreateChecksum(shareData.map { it and 1023 }, extendableBackupFlag)
        return (shareData + checksum).map { wordList[it and 1023] }.joinToString(" ")
    }

    private fun decodeMnemonic(mnemonic: String): DecodedShare {
        val words = mnemonic.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < MNEMONICS_WORDS_LENGTH) throw IllegalArgumentException("Mnemonic too short")
        val indices = words.map { wordToIndex[it] ?: throw IllegalArgumentException("Invalid word: $it") }
        val paddingLen = (RADIX_BITS * (indices.size - METADATA_WORDS_LENGTH)) % 16
        if (paddingLen > 8) throw IllegalArgumentException("Invalid mnemonic length")
        val idExpInt = intFromIndices(indices.take(ITERATION_EXP_WORDS_LENGTH)).toLong()
        val identifier = (idExpInt shr (ITERATION_EXP_BITS + EXTENDABLE_BACKUP_FLAG_BITS)).toInt()
        val extendableBackupFlag = (idExpInt shr ITERATION_EXP_BITS).toInt() and 1
        val iterationExponent = idExpInt.toInt() and ((1 shl ITERATION_EXP_BITS) - 1)
        if (!rs1024VerifyChecksum(indices, extendableBackupFlag)) throw IllegalArgumentException("Invalid checksum")
        val tmp = intFromIndices(indices.slice(ITERATION_EXP_WORDS_LENGTH until ITERATION_EXP_WORDS_LENGTH + 2))
        val metaIndices = intToIndices(tmp, 5, 4)
        val groupIndex = metaIndices[0]
        val groupThreshold = metaIndices[1] + 1
        val groupCount = metaIndices[2] + 1
        val memberIndex = metaIndices[3]
        val memberThreshold = metaIndices[4] + 1
        val valueData = indices.slice(ITERATION_EXP_WORDS_LENGTH + 2 until indices.size - CHECKSUM_WORDS_LENGTH)
        val valueByteCount = (RADIX_BITS * valueData.size - paddingLen + 7) / 8
        val valueInt = intFromIndices(valueData)
        val value = valueInt.toByteArray().let { b ->
            if (b.size > valueByteCount) b.takeLast(valueByteCount).toByteArray()
            else if (b.size < valueByteCount) ByteArray(valueByteCount - b.size) + b
            else b
        }
        return DecodedShare(identifier, extendableBackupFlag, iterationExponent, groupIndex, groupThreshold, groupCount, memberIndex, memberThreshold, value)
    }

    private data class DecodedShare(
        val identifier: Int,
        val extendableBackupFlag: Int,
        val iterationExponent: Int,
        val groupIndex: Int,
        val groupThreshold: Int,
        val groupCount: Int,
        val memberIndex: Int,
        val memberThreshold: Int,
        val value: ByteArray
    )

    private fun combineMnemonics(mnemonics: List<String>, passphrase: String): ByteArray {
        val decoded = mnemonics.map { decodeMnemonic(it) }
        val first = decoded.first()
        for (d in decoded) {
            if (d.identifier != first.identifier || d.extendableBackupFlag != first.extendableBackupFlag ||
                d.iterationExponent != first.iterationExponent)
                throw IllegalArgumentException("All shares must have same identifier/iteration")
        }
        val byGroup = decoded.groupBy { it.groupIndex }
        val groupThreshold = first.groupThreshold
        val groupCount = first.groupCount
        if (byGroup.size < groupThreshold) throw IllegalArgumentException("Insufficient groups")
        if (byGroup.size != groupThreshold) throw IllegalArgumentException("Wrong number of groups")
        val groupShares = mutableMapOf<Int, ByteArray>()
        for ((gIdx, members) in byGroup) {
            val byThresh = members.groupBy { it.memberThreshold }
            if (byThresh.size != 1) throw IllegalArgumentException("Inconsistent member thresholds")
            val threshold = byThresh.keys.first()
            val shares = members.associate { it.memberIndex to it.value }
            if (shares.size < threshold) throw IllegalArgumentException("Insufficient shares in group")
            if (shares.size != threshold) throw IllegalArgumentException("Wrong number of shares in group")
            groupShares[gIdx] = recoverSecret(threshold, shares)
        }
        val ems = recoverSecret(groupThreshold, groupShares)
        val idBytes = intToIndices(BigInteger.valueOf(first.identifier.toLong()), ITERATION_EXP_WORDS_LENGTH, 8)
            .map { it.toByte() }.toByteArray()
        return crypt(ems, passphrase, first.iterationExponent, idBytes, first.extendableBackupFlag, false)
    }

    companion object {
        private const val SLIP39_WORDLIST_PATH = "slip39_wordlist.txt"
        private const val RADIX_BITS = 10
        private val RADIX_BIG = BigInteger.valueOf(1024)
        private const val ITERATION_EXP_BITS = 4
        private const val EXTENDABLE_BACKUP_FLAG_BITS = 1
        private const val ITERATION_EXP_WORDS_LENGTH = 2
        private const val CHECKSUM_WORDS_LENGTH = 3
        private const val METADATA_WORDS_LENGTH = 7
        private const val MNEMONICS_WORDS_LENGTH = 20
        private const val DIGEST_LENGTH = 4
        private const val ITERATION_COUNT = 10000
        private const val ROUND_COUNT = 4
        private const val DIGEST_INDEX = 254
        private const val SECRET_INDEX = 255
        private const val MAX_SHARE_COUNT = 16
        private const val CUSTOMIZATION_NON_EXTENDABLE = "shamir"
        private const val CUSTOMIZATION_EXTENDABLE = "shamir_extendable"

        private val EXP_TABLE = intArrayOf(
            1, 3, 5, 15, 17, 51, 85, 255, 26, 46, 114, 150, 161, 248, 19, 53, 95, 225, 56,
            72, 216, 115, 149, 164, 247, 2, 6, 10, 30, 34, 102, 170, 229, 52, 92, 228, 55,
            89, 235, 38, 106, 190, 217, 112, 144, 171, 230, 49, 83, 245, 4, 12, 20, 60,
            68, 204, 79, 209, 104, 184, 211, 110, 178, 205, 76, 212, 103, 169, 224, 59,
            77, 215, 98, 166, 241, 8, 24, 40, 120, 136, 131, 158, 185, 208, 107, 189, 220,
            127, 129, 152, 179, 206, 73, 219, 118, 154, 181, 196, 87, 249, 16, 48, 80,
            240, 11, 29, 39, 105, 187, 214, 97, 163, 254, 25, 43, 125, 135, 146, 173, 236,
            47, 113, 147, 174, 233, 32, 96, 160, 251, 22, 58, 78, 210, 109, 183, 194, 93,
            231, 50, 86, 250, 21, 63, 65, 195, 94, 226, 61, 71, 201, 64, 192, 91, 237, 44,
            116, 156, 191, 218, 117, 159, 186, 213, 100, 172, 239, 42, 126, 130, 157, 188,
            223, 122, 142, 137, 128, 155, 182, 193, 88, 232, 35, 101, 175, 234, 37, 111,
            177, 200, 67, 197, 84, 252, 31, 33, 99, 165, 244, 7, 9, 27, 45, 119, 153, 176,
            203, 70, 202, 69, 207, 74, 222, 121, 139, 134, 145, 168, 227, 62, 66, 198, 81,
            243, 14, 18, 54, 90, 238, 41, 123, 141, 140, 143, 138, 133, 148, 167, 242, 13,
            23, 57, 75, 221, 124, 132, 151, 162, 253, 28, 36, 108, 180, 199, 82, 246
        )

        private val LOG_TABLE = intArrayOf(
            0, 0, 25, 1, 50, 2, 26, 198, 75, 199, 27, 104, 51, 238, 223, 3, 100, 4, 224,
            14, 52, 141, 129, 239, 76, 113, 8, 200, 248, 105, 28, 193, 125, 194, 29, 181,
            249, 185, 39, 106, 77, 228, 166, 114, 154, 201, 9, 120, 101, 47, 138, 5, 33,
            15, 225, 36, 18, 240, 130, 69, 53, 147, 218, 142, 150, 143, 219, 189, 54, 208,
            206, 148, 19, 92, 210, 241, 64, 70, 131, 56, 102, 221, 253, 48, 191, 6, 139,
            98, 179, 37, 226, 152, 34, 136, 145, 16, 126, 110, 72, 195, 163, 182, 30, 66,
            58, 107, 40, 84, 250, 133, 61, 186, 43, 121, 10, 21, 155, 159, 94, 202, 78,
            212, 172, 229, 243, 115, 167, 87, 175, 88, 168, 80, 244, 234, 214, 116, 79,
            174, 233, 213, 231, 230, 173, 232, 44, 215, 117, 122, 235, 22, 11, 245, 89,
            203, 95, 176, 156, 169, 81, 160, 127, 12, 246, 111, 23, 196, 73, 236, 216, 67,
            31, 45, 164, 118, 123, 183, 204, 187, 62, 90, 251, 96, 177, 134, 59, 82, 161,
            108, 170, 85, 41, 157, 151, 178, 135, 144, 97, 190, 220, 252, 188, 149, 207,
            205, 55, 63, 91, 209, 83, 57, 132, 60, 65, 162, 109, 71, 20, 42, 158, 93, 86,
            242, 211, 171, 68, 17, 146, 217, 35, 32, 46, 137, 180, 124, 184, 38, 119, 153,
            227, 165, 103, 74, 237, 222, 197, 49, 254, 24, 13, 99, 140, 128, 192, 247,
            112, 7
        )
    }
}
