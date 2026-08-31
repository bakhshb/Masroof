package com.baraa.masroof.application.settings

import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.AccountType
import com.baraa.masroof.domain.model.CardNetwork
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.LoanReference
import com.baraa.masroof.domain.model.LoanRegistryEntry
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.repository.AccountRegistryRepository
import com.baraa.masroof.domain.repository.CardRegistryRepository
import com.baraa.masroof.domain.repository.LoanRegistryRepository

/**
 * Settings-screen registry reads, metadata edits, and ownership confirmation.
 */
class SettingsRegistryWorkflow(
    private val cardRegistryRepository: CardRegistryRepository,
    private val accountRegistryRepository: AccountRegistryRepository,
    private val loanRegistryRepository: LoanRegistryRepository,
    private val ownershipConfirmationService: OwnershipConfirmationService,
) {
    data class Snapshot(
        val cards: List<CardRegistryEntry>,
        val accounts: List<AccountRegistryEntry>,
        val loans: List<LoanRegistryEntry>,
    )

    suspend fun loadSnapshot(): Snapshot =
        Snapshot(
            cards = cardRegistryRepository.listAll(),
            accounts = accountRegistryRepository.listAll(),
            loans = loanRegistryRepository.listAll(),
        )

    suspend fun updateCardDisplayName(card: CardReference, displayName: String?) {
        cardRegistryRepository.updateDisplayName(card, displayName)
    }

    suspend fun updateAccountDisplayName(account: AccountReference, displayName: String?) {
        accountRegistryRepository.updateDisplayName(account, displayName)
    }

    suspend fun updateCardNetwork(card: CardReference, network: CardNetwork?) {
        cardRegistryRepository.updateCardNetwork(card, network)
    }

    suspend fun setPrimaryCard(card: CardReference) {
        cardRegistryRepository.setPrimaryCard(card)
    }

    suspend fun setSupplementaryCard(card: CardReference, primaryLast4: String) {
        cardRegistryRepository.setSupplementaryCard(card, primaryLast4)
    }

    suspend fun clearCardRole(card: CardReference) {
        cardRegistryRepository.clearCardRole(card)
    }

    suspend fun linkDebitToAccount(card: CardReference, account: AccountReference) {
        cardRegistryRepository.linkDebitToAccount(card, account)
    }

    suspend fun markCardAsDebit(card: CardReference) {
        cardRegistryRepository.markAsDebit(card)
    }

    suspend fun updateAccountType(account: AccountReference, accountType: AccountType) {
        accountRegistryRepository.updateAccountType(account, accountType)
    }

    suspend fun confirmCardOwned(card: CardReference) {
        ownershipConfirmationService.confirmCardOwned(card)
    }

    suspend fun markCardExternal(card: CardReference) {
        ownershipConfirmationService.markCardExternal(card)
    }

    suspend fun confirmAccountOwned(account: AccountReference) {
        ownershipConfirmationService.confirmAccountOwned(account)
    }

    suspend fun markAccountExternal(account: AccountReference) {
        ownershipConfirmationService.markAccountExternal(account)
    }

    suspend fun confirmLoanOwned(loan: LoanReference) {
        ownershipConfirmationService.confirmLoanOwned(loan)
    }

    suspend fun markLoanExternal(loan: LoanReference) {
        ownershipConfirmationService.markLoanExternal(loan)
    }
}
