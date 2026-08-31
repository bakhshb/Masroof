package com.baraa.masroof.application.notification

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.repository.AccountRegistryRepository
import com.baraa.masroof.domain.repository.CardRegistryRepository
import com.baraa.masroof.domain.repository.ReviewRepository

/**
 * Notification-center counts derived from review and registry state.
 */
class NotificationCenterMetricsWorkflow(
    private val reviewRepository: ReviewRepository,
    private val cardRegistryRepository: CardRegistryRepository,
    private val accountRegistryRepository: AccountRegistryRepository,
) {
    suspend fun requiredReviewCount(): Int = reviewRepository.listRequired().size

    suspend fun unregisteredCardCount(): Int =
        cardRegistryRepository.listAll()
            .count { card -> card.bank != Bank.UNKNOWN && card.ownership == OwnershipStatus.UNKNOWN }

    suspend fun unregisteredAccountCount(): Int =
        accountRegistryRepository.listAll()
            .count { account -> account.bank != Bank.UNKNOWN && account.ownership == OwnershipStatus.UNKNOWN }
}
