package com.baraa.masroof.domain.ownership

import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.repository.AccountRegistryRepository
import com.baraa.masroof.domain.repository.CardRegistryRepository

/**
 * Exact bank-scoped ownership lookup against the registry.
 *
 * Missing entries resolve to [OwnershipStatus.UNKNOWN] (never throw).
 * Does not correlate [com.baraa.masroof.domain.model.Bank.UNKNOWN] masks to
 * known-bank owned accounts.
 */
class OwnershipResolver(
    private val accountRegistry: AccountRegistryRepository,
    private val cardRegistry: CardRegistryRepository,
) {
    suspend fun resolveAccount(reference: AccountReference): OwnershipStatus =
        accountRegistry.resolve(reference)

    suspend fun resolveCard(reference: CardReference): OwnershipStatus =
        cardRegistry.resolve(reference)
}
