package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.application.dashboard.CreditCardsOverview
import com.baraa.masroof.application.dashboard.CreditFacilitiesOverview
import com.baraa.masroof.application.dashboard.DebitCardOverview

fun DashboardUiState.followedCreditCardsOverview(): CreditCardsOverview? {
    val overview = creditCards ?: return null
    val ownedKeys = CardOwnershipKey.ownedKeys(ownedCards)
    return overview.followedOnly(ownedKeys)
}

fun DashboardUiState.followedCreditFacilities(): CreditFacilitiesOverview? {
    val overview = creditFacilities ?: return null
    val ownedKeys = CardOwnershipKey.ownedKeys(ownedCards)
    val facilities = overview.facilities.filter { facility ->
        facility.allCards.any { CardOwnershipKey.of(it) in ownedKeys }
    }
    if (facilities.isEmpty()) return null
    return overview.copy(facilities = facilities, debitCards = emptyList<DebitCardOverview>())
}

/** Cards summary includes owned debit tiles; home carousel stays credit-only via [followedCreditFacilities]. */
fun DashboardUiState.followedCreditFacilitiesForSummary(): CreditFacilitiesOverview? {
    val overview = creditFacilities ?: return null
    val ownedKeys = CardOwnershipKey.ownedKeys(ownedCards)
    val facilities = overview.facilities.filter { facility ->
        facility.allCards.any { CardOwnershipKey.of(it) in ownedKeys }
    }
    val debitCards = overview.debitCards.filter { CardOwnershipKey.of(it) in ownedKeys }
    if (facilities.isEmpty() && debitCards.isEmpty()) return null
    return overview.copy(facilities = facilities, debitCards = debitCards)
}
