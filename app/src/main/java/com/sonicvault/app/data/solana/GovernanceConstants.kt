package com.sonicvault.app.data.solana

/**
 * SPL Governance and Guardian Voting demo constants.
 *
 * SPL Governance program ID (mainnet; devnet uses same).
 * Demo realm/governance/proposal are placeholders until deployed.
 */
object GovernanceConstants {

    /** SPL Governance program ID. */
    const val SPL_GOVERNANCE_PROGRAM_ID = "GovER5Lthms3bLBqWub97yVrMmEogzX7xNjdXpPiCXLe"

    /** Demo realm (devnet placeholder — replace with real address). */
    const val KYMA_DEMO_REALM = "11111111111111111111111111111111"

    /** Demo governance (devnet placeholder). */
    const val KYMA_DEMO_GOVERNANCE = "11111111111111111111111111111111"

    /** Demo proposal (devnet placeholder). */
    const val KYMA_DEMO_PROPOSAL = "11111111111111111111111111111111"

    /** SPL Memo program ID — used for hackathon vote proof (VOTE:proposal:direction). */
    const val SPL_MEMO_PROGRAM_ID = "Memo1UhkJRfHyvLMcVucJwxXeuD728EqVDDwQDxFMNo"

    /** Vote direction enum (matches SPL Governance cast_vote). */
    enum class VoteDirection(val value: Byte) {
        NO(0x00),
        YES(0x01),
        ABSTAIN(0x02)
    }
}
