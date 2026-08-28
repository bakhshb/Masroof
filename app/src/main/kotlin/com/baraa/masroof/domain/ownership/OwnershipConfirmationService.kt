package com.baraa.masroof.domain.ownership

import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.repository.AccountRegistryRepository
import com.baraa.masroof.domain.repository.CardRegistryRepository
import com.baraa.masroof.domain.model.LoanReference
import com.baraa.masroof.domain.repository.LoanRegistryRepository

/**
 * Backend confirmation API for later onboarding/review UI.
 * Clears return to [OwnershipStatus.UNKNOWN] without deleting registry evidence.
 */
class OwnershipConfirmationService(
    private val accountRegistry: AccountRegistryRepository,
    private val cardRegistry: CardRegistryRepository,
    private val loanRegistry: LoanRegistryRepository,
) {
    suspend fun confirmAccountOwned(reference: AccountReference) {
        accountRegistry.setOwnership(reference, OwnershipStatus.OWNED)
    }

    suspend fun markAccountExternal(reference: AccountReference) {
        accountRegistry.setOwnership(reference, OwnershipStatus.EXTERNAL)
    }

    suspend fun clearAccountOwnership(reference: AccountReference) {
        accountRegistry.setOwnership(reference, OwnershipStatus.UNKNOWN)
    }

    suspend fun confirmCardOwned(reference: CardReference) {
        cardRegistry.setOwnership(reference, OwnershipStatus.OWNED)
    }

    suspend fun markCardExternal(reference: CardReference) {
        cardRegistry.setOwnership(reference, OwnershipStatus.EXTERNAL)
    }

    suspend fun clearCardOwnership(reference: CardReference) {
        cardRegistry.setOwnership(reference, OwnershipStatus.UNKNOWN)
    }

    suspend fun confirmLoanOwned(reference: LoanReference) {
        loanRegistry.setOwnership(reference, OwnershipStatus.OWNED)
    }

    suspend fun markLoanExternal(reference: LoanReference) {
        loanRegistry.setOwnership(reference, OwnershipStatus.EXTERNAL)
    }

    suspend fun clearLoanOwnership(reference: LoanReference) {
        loanRegistry.setOwnership(reference, OwnershipStatus.UNKNOWN)
    }
}
