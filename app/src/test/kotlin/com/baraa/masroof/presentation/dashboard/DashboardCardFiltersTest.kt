package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.application.dashboard.CreditCardsOverview
import com.baraa.masroof.application.dashboard.CreditFacilitiesOverview
import com.baraa.masroof.application.dashboard.CreditFacilityOverview
import com.baraa.masroof.application.dashboard.DebitCardOverview
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class DashboardCardFiltersTest {
    @Test
    fun followedCreditFacilities_keepsOnlyOwnedFacilitiesAndDebits() {
        val state = DashboardUiState(
            creditFacilities = CreditFacilitiesOverview(
                facilities = listOf(
                    facility("1111", listOf("2222")),
                    facility("9999", emptyList()),
                ),
                debitCards = listOf(
                    debit("3333"),
                    debit("8888"),
                ),
                legacyFlat = emptyOverview(),
                currency = Currency.SAR,
            ),
            ownedCards = listOf(
                OwnedCardUi(Bank.BANK_ALJAZIRA, "1111", cardNetwork = CardNetwork.VISA),
                OwnedCardUi(Bank.BANK_ALJAZIRA, "2222", cardNetwork = CardNetwork.VISA),
                OwnedCardUi(Bank.BANK_ALJAZIRA, "3333", cardNetwork = CardNetwork.MADA),
            ),
        )

        val filtered = state.followedCreditFacilities()

        assertNotNull(filtered)
        assertEquals(1, filtered!!.facilities.size)
        assertEquals("1111", filtered.facilities.single().primaryLast4)
        assertEquals(1, filtered.debitCards.size)
        assertEquals("3333", filtered.debitCards.single().last4)
    }

    @Test
    fun followedCreditFacilities_returnsNullWhenNothingOwned() {
        val state = DashboardUiState(
            creditFacilities = CreditFacilitiesOverview(
                facilities = listOf(facility("9999", emptyList())),
                debitCards = emptyList(),
                legacyFlat = emptyOverview(),
                currency = Currency.SAR,
            ),
            ownedCards = emptyList(),
        )

        assertNull(state.followedCreditFacilities())
    }

    private fun facility(primary: String, supplementaries: List<String>): CreditFacilityOverview {
        val primaryRow = cardRow(primary)
        return CreditFacilityOverview(
            bank = Bank.BANK_ALJAZIRA,
            primary = primaryRow,
            supplementaries = supplementaries.map(::cardRow),
            facilityDue = null,
            facilitySalaryPeriodSpending = SignedMoneyAmount.zero(Currency.SAR),
            facilityStatementSpending = SignedMoneyAmount.zero(Currency.SAR),
            aggregateStatementPeriodLabel = null,
            salaryPeriodLabel = null,
            currency = Currency.SAR,
        )
    }

    private fun cardRow(last4: String) = CreditCardDashboardRow(
        bank = Bank.BANK_ALJAZIRA,
        last4 = last4,
        calendarMonthSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
        statementSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
        salaryPeriodSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
        statementPeriodLabel = null,
        snapshot = null,
    )

    private fun debit(last4: String) = DebitCardOverview(
        bank = Bank.BANK_ALJAZIRA,
        last4 = last4,
        displayLabel = "Mada ••$last4",
        linkedAccountLabel = null,
        network = CardNetwork.MADA,
    )

    private fun emptyOverview() = CreditCardsOverview(
        cards = emptyList(),
        aggregateDueAmount = null,
        aggregateDueUpdatedAt = null,
        aggregateDueDate = null,
        aggregatePeriodSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
        aggregateStatementSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
        aggregateStatementPeriodLabel = null,
        calendarMonthLabel = null,
        salaryPeriodLabel = null,
        currency = Currency.SAR,
    )
}
