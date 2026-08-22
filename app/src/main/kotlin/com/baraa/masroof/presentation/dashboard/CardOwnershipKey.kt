package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.application.dashboard.DebitCardOverview
import com.baraa.masroof.domain.model.Bank

/** Stable ownership key for cards — bank + last4, not last4 alone. */
object CardOwnershipKey {
    fun of(bank: Bank, last4: String): String = "${bank.id}:$last4"

    fun of(row: CreditCardDashboardRow): String = of(row.bank, row.last4)

    fun of(debit: DebitCardOverview): String = of(debit.bank, debit.last4)

    fun of(card: OwnedCardUi): String = of(card.bank, card.last4)

    fun ownedKeys(ownedCards: List<OwnedCardUi>): Set<String> = ownedCards.map(::of).toSet()
}
