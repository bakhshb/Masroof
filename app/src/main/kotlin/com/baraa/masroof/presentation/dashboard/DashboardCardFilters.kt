package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.application.dashboard.CreditFacilitiesOverview
import com.baraa.masroof.application.dashboard.LoansOverview

fun DashboardUiState.followedCreditFacilities(): CreditFacilitiesOverview? {
    val overview = creditFacilities ?: return null
    val ownedKeys = CardOwnershipKey.ownedKeys(ownedCards)
    val facilities = overview.facilities.filter { facility ->
        facility.allCards.any { CardOwnershipKey.of(it) in ownedKeys }
    }
    val debitCards = overview.debitCards.filter { CardOwnershipKey.of(it) in ownedKeys }
    if (facilities.isEmpty() && debitCards.isEmpty()) return null
    return overview.copy(facilities = facilities, debitCards = debitCards)
}

fun DashboardUiState.followedLoansOverview(): LoansOverview? {
    val overview = loansOverview ?: return null
    return if (overview.hasContent) overview else null
}
