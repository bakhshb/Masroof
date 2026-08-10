package com.baraa.masroof.domain.repository

import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.OwnershipStatus

/**
 * Durable card ownership registry. Discovery observes; confirmation sets status.
 */
interface CardRegistryRepository {
    /**
     * Records that [reference] was seen in [rawSmsId].
     *
     * New rows start as [OwnershipStatus.UNKNOWN]. Existing OWNED/EXTERNAL
     * statuses are preserved. Same [rawSmsId] as [CardRegistryEntry.lastSeenRawSmsId]
     * is a no-op (idempotent).
     */
    suspend fun observe(reference: CardReference, rawSmsId: String)

    suspend fun setOwnership(reference: CardReference, status: OwnershipStatus)

    suspend fun resolve(reference: CardReference): OwnershipStatus

    suspend fun get(reference: CardReference): CardRegistryEntry?

    suspend fun listAll(): List<CardRegistryEntry>
}
