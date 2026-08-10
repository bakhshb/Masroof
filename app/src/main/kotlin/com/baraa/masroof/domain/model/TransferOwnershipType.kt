package com.baraa.masroof.domain.model

/**
 * Ownership relationship of a transfer relative to the user.
 *
 * Distinct from [BankNetworkType]: an intra-bank transfer can still be
 * [EXTERNAL_INCOMING] or [EXTERNAL_OUTGOING].
 */
enum class TransferOwnershipType {
    SELF_TRANSFER,
    EXTERNAL_INCOMING,
    EXTERNAL_OUTGOING,
    UNKNOWN,
}
