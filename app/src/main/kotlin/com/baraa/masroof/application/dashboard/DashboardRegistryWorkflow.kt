package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.repository.AccountRegistryRepository
import com.baraa.masroof.domain.repository.CardRegistryRepository

/**
 * Dashboard registry reads for owned and unknown cards/accounts.
 */
class DashboardRegistryWorkflow(
    private val cardRegistryRepository: CardRegistryRepository,
    private val accountRegistryRepository: AccountRegistryRepository,
) {
    suspend fun listUnknownCards(): List<CardRegistryEntry> =
        cardRegistryRepository.listAll()
            .filter { it.bank != Bank.UNKNOWN && it.ownership == OwnershipStatus.UNKNOWN }
            .sortedBy { it.last4 }

    suspend fun listOwnedCards(): List<CardRegistryEntry> =
        cardRegistryRepository.listAll()
            .filter { it.bank != Bank.UNKNOWN && it.ownership == OwnershipStatus.OWNED }
            .sortedBy { it.last4 }

    suspend fun listOwnedAccounts(): List<AccountRegistryEntry> =
        accountRegistryRepository.listAll()
            .filter { it.bank != Bank.UNKNOWN && it.ownership == OwnershipStatus.OWNED }
            .sortedBy { it.maskedNumber }
}
