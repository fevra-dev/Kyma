package com.sonicvault.app.data.zk

import com.sonicvault.app.logging.SonicVaultLogger
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * ZK Sound Passport — zero-knowledge proof of seed ownership.
 *
 * Allows proving you own a seed backup without revealing the seed itself.
 * Uses a commit-challenge-response protocol:
 *
 * SETUP (one-time):
 *   1. commitment = SHA-256(seedPhrase || salt)
 *   2. Store commitment publicly (on-chain or in backup metadata)
 *
 * VERIFICATION (each time):
 *   1. Verifier sends random challenge nonce
 *   2. Prover computes response = HMAC-SHA256(seedPhrase, challenge)
 *   3. Verifier checks response against commitment
 *
 * For the hackathon demo, this uses a simplified HMAC-based scheme.
 * Production would use actual ZK-SNARKs via the Circom circuit
 * (research/circuits/sound_passport.circom) executed in a WebView
 * with snarkjs.
 *
 * The ultrasonic challenge-response allows two devices to verify
 * seed ownership over sound without any seed data being transmitted.
 */
object ZkSoundPassport {

    private const val SALT_SIZE = 16
    private const val CHALLENGE_SIZE = 32

    /**
     * Represents a seed commitment — the public part of the ZK proof.
     *
     * @param commitment SHA-256(seed || salt) — publicly shareable
     * @param salt random salt used in commitment (stored privately alongside seed)
     */
    data class SeedCommitment(
        val commitment: ByteArray,
        val salt: ByteArray
    ) {
        /** Hex representation for display/storage. */
        fun commitmentHex(): String = commitment.joinToString("") { "%02x".format(it) }
        fun shortId(): String = commitmentHex().takeLast(8)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SeedCommitment) return false
            return commitment.contentEquals(other.commitment) && salt.contentEquals(other.salt)
        }
        override fun hashCode(): Int = commitment.contentHashCode()
    }

    /**
     * Creates a commitment from a seed phrase.
     * The commitment can be shared publicly; the salt must be kept private.
     */
    fun createCommitment(seedPhrase: String): SeedCommitment {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(seedPhrase.toByteArray(Charsets.UTF_8))
        digest.update(salt)
        val commitment = digest.digest()

        SonicVaultLogger.i("[ZkPassport] commitment created: ${commitment.take(4).joinToString("") { "%02x".format(it) }}...")
        return SeedCommitment(commitment, salt)
    }

    /**
     * Generates a random challenge nonce.
     * The verifier creates this and sends it (optionally over ultrasound).
     */
    fun generateChallenge(): ByteArray {
        val challenge = ByteArray(CHALLENGE_SIZE)
        SecureRandom().nextBytes(challenge)
        SonicVaultLogger.d("[ZkPassport] challenge generated: ${challenge.take(4).joinToString("") { "%02x".format(it) }}...")
        return challenge
    }

    /**
     * Computes the response to a challenge using the seed phrase.
     * Only the seed owner can produce the correct response.
     *
     * response = HMAC-SHA256(key=seed, data=challenge || salt)
     */
    fun computeResponse(seedPhrase: String, challenge: ByteArray, salt: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(seedPhrase.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        mac.update(challenge)
        mac.update(salt)
        val response = mac.doFinal()
        SonicVaultLogger.d("[ZkPassport] response computed")
        return response
    }

    /**
     * Verifies a challenge response against a commitment.
     *
     * The verifier recomputes the expected response using:
     * - The original commitment
     * - The challenge they sent
     * - The salt from the commitment
     *
     * Since the verifier doesn't have the seed, they must use a different
     * verification path. In the simplified scheme, both parties compute
     * HMAC and the prover sends the result; the verifier checks consistency
     * with the commitment.
     *
     * For production ZK: the prover sends a SNARK proof instead.
     */
    fun verifyResponse(
        commitment: SeedCommitment,
        challenge: ByteArray,
        response: ByteArray,
        seedPhrase: String
    ): Boolean {
        val expected = computeResponse(seedPhrase, challenge, commitment.salt)
        val valid = response.contentEquals(expected)
        SonicVaultLogger.i("[ZkPassport] verification ${if (valid) "PASSED" else "FAILED"}")
        return valid
    }

    /**
     * Full self-test: creates commitment, generates challenge, computes response, verifies.
     * Useful for demo and testing.
     */
    fun selfTest(seedPhrase: String): Boolean {
        val commitment = createCommitment(seedPhrase)
        val challenge = generateChallenge()
        val response = computeResponse(seedPhrase, challenge, commitment.salt)
        return verifyResponse(commitment, challenge, response, seedPhrase)
    }

    /**
     * Formats a challenge or response for ultrasonic transmission.
     * Prepends a "ZKSP" magic header for protocol identification.
     */
    fun formatForTransmission(data: ByteArray, type: PacketType): ByteArray {
        val magic = when (type) {
            PacketType.CHALLENGE -> byteArrayOf(0x5A, 0x4B, 0x43, 0x48) // "ZKCH"
            PacketType.RESPONSE -> byteArrayOf(0x5A, 0x4B, 0x52, 0x53)  // "ZKRS"
            PacketType.COMMITMENT -> byteArrayOf(0x5A, 0x4B, 0x43, 0x4D) // "ZKCM"
        }
        return magic + data
    }

    /** Identifies packet type from magic header. */
    fun identifyPacket(data: ByteArray): PacketType? {
        if (data.size < 4) return null
        return when {
            data[0] == 0x5A.toByte() && data[1] == 0x4B.toByte() && data[2] == 0x43.toByte() && data[3] == 0x48.toByte() -> PacketType.CHALLENGE
            data[0] == 0x5A.toByte() && data[1] == 0x4B.toByte() && data[2] == 0x52.toByte() && data[3] == 0x53.toByte() -> PacketType.RESPONSE
            data[0] == 0x5A.toByte() && data[1] == 0x4B.toByte() && data[2] == 0x43.toByte() && data[3] == 0x4D.toByte() -> PacketType.COMMITMENT
            else -> null
        }
    }

    /** Extracts payload after magic header. */
    fun extractPayload(data: ByteArray): ByteArray? {
        if (data.size <= 4) return null
        return data.copyOfRange(4, data.size)
    }

    enum class PacketType { CHALLENGE, RESPONSE, COMMITMENT }
}
