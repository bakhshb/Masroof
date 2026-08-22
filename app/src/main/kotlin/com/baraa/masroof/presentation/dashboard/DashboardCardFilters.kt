package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.application.dashboard.CreditCardsOverview
import com.baraa.masroof.application.dashboard.CreditFacilitiesOverview

fun DashboardUiState.followedCreditCardsOverview(): CreditCardsOverview? {
    val overview = creditCards ?: return null
    val ownedLast4s = ownedCards.map { it.last4 }.toSet()
    return overview.followedOnly(ownedLast4s)
}

fun DashboardUiState.followedCreditFacilities(): CreditFacilitiesOverview? {
    val overview = creditFacilities ?: return null
    if (!overview.hasContent) return null
    val ownedLast4s = ownedCards.map { it.last4 }.toSet()
    val facilities = overview.facilities.filter { facility ->
        facility.allCards.any { it.last4 in ownedLast4s }
    }
    val debitCards = overview.debitCards.filter { it.last4 in ownedLast4s }
    if (facilities.isEmpty() && debitCards.isEmpty()) return null
    return overview.copy(facilities = facilities, debitCards = debitCards)
}
