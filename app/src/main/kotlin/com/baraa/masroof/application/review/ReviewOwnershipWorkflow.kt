package com.baraa.masroof.application.review

import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.repository.CardRegistryRepository

/**
 * Review-screen ownership actions and registry lookups for presentation.
 */
class ReviewOwnershipWorkflow(
    private val cardRegistryRepository: CardRegistryRepository,
    private val ownershipConfirmationService: OwnershipConfirmationService,
) {
    suspend fun isCardOwnershipUnknown(card: CardReference): Boolean =
        cardRegistryRepository.resolve(card) == OwnershipStatus.UNKNOWN

    suspend fun confirmCardOwned(card: CardReference) {
        ownershipConfirmationService.confirmCardOwned(card)
    }

    suspend fun markCardExternal(card: CardReference) {
        ownershipConfirmationService.markCardExternal(card)
    }
}
