package com.baraa.masroof.domain.rules

import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.TransferOwnershipType

/**
 * Resolves transfer ownership from container ownership only.
 *
 * [com.baraa.masroof.domain.model.BankNetworkType] is intentionally not a parameter:
 * intra-bank wording never implies a self-transfer (DOMAIN D-002, D-010).
 */
object TransferOwnershipResolver {
    fun resolve(
        sourceOwnership: OwnershipStatus?,
        destinationOwnership: OwnershipStatus?,
    ): TransferOwnershipType {
        if (sourceOwnership == null || destinationOwnership == null) {
            return TransferOwnershipType.UNKNOWN
        }
        if (sourceOwnership == OwnershipStatus.UNKNOWN ||
            destinationOwnership == OwnershipStatus.UNKNOWN
        ) {
            return TransferOwnershipType.UNKNOWN
        }
        return when {
            sourceOwnership == OwnershipStatus.OWNED &&
                destinationOwnership == OwnershipStatus.OWNED ->
                TransferOwnershipType.SELF_TRANSFER

            sourceOwnership == OwnershipStatus.EXTERNAL &&
                destinationOwnership == OwnershipStatus.OWNED ->
                TransferOwnershipType.EXTERNAL_INCOMING

            sourceOwnership == OwnershipStatus.OWNED &&
                destinationOwnership == OwnershipStatus.EXTERNAL ->
                TransferOwnershipType.EXTERNAL_OUTGOING

            else -> TransferOwnershipType.UNKNOWN
        }
    }
}
