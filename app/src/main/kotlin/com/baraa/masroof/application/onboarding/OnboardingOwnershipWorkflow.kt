package com.baraa.masroof.application.onboarding

import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.repository.AccountRegistryRepository
import com.baraa.masroof.domain.repository.CardRegistryRepository
import com.baraa.masroof.domain.repository.ReviewRepository

/**
 * Onboarding ownership candidates, counts, and confirmation actions.
 */
class OnboardingOwnershipWorkflow(
    private val accountRegistryRepository: AccountRegistryRepository,
    private val cardRegistryRepository: CardRegistryRepository,
    private val ownershipConfirmationService: OwnershipConfirmationService,
    private val reviewRepository: ReviewRepository,
) {
    enum class CandidateKind {
        ACCOUNT,
        CARD,
    }

    data class OwnershipCandidate(
        val kind: CandidateKind,
        val bank: Bank,
        val suffix: String,
        val ownership: OwnershipStatus,
    )

    data class Snapshot(
        val accounts: List<OwnershipCandidate>,
        val cards: List<OwnershipCandidate>,
        val ownedAccountsCount: Int,
        val ownedCardsCount: Int,
        val reviewRequiredCount: Int,
        val hasUnknownCandidates: Boolean,
    )

    suspend fun loadSnapshot(): Snapshot {
        val accounts = accountRegistryRepository.listAll()
            .filter { it.bank != Bank.UNKNOWN }
            .map {
                OwnershipCandidate(
                    kind = CandidateKind.ACCOUNT,
                    bank = it.bank,
                    suffix = it.maskedNumber,
                    ownership = it.ownership,
                )
            }
            .sortedBy { it.suffix }
        val cards = cardRegistryRepository.listAll()
            .filter { it.bank != Bank.UNKNOWN }
            .map {
                OwnershipCandidate(
                    kind = CandidateKind.CARD,
                    bank = it.bank,
                    suffix = it.last4,
                    ownership = it.ownership,
                )
            }
            .sortedBy { it.suffix }
        return Snapshot(
            accounts = accounts,
            cards = cards,
            ownedAccountsCount = accounts.count { it.ownership == OwnershipStatus.OWNED },
            ownedCardsCount = cards.count { it.ownership == OwnershipStatus.OWNED },
            reviewRequiredCount = reviewRepository.listRequired().size,
            hasUnknownCandidates = accounts.any { it.ownership == OwnershipStatus.UNKNOWN } ||
                cards.any { it.ownership == OwnershipStatus.UNKNOWN },
        )
    }

    suspend fun confirmAccountOwned(account: AccountReference) {
        ownershipConfirmationService.confirmAccountOwned(account)
    }

    suspend fun markAccountExternal(account: AccountReference) {
        ownershipConfirmationService.markAccountExternal(account)
    }

    suspend fun confirmCardOwned(card: CardReference) {
        ownershipConfirmationService.confirmCardOwned(card)
    }

    suspend fun markCardExternal(card: CardReference) {
        ownershipConfirmationService.markCardExternal(card)
    }
}
