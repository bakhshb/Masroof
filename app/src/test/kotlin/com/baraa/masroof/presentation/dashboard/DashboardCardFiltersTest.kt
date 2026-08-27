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
    fun followedCreditFacilities_keepsOnlyOwnedFacilitiesWithoutDebitTiles() {
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
        assertEquals(0, filtered.debitCards.size)
    }

    @Test
    fun followedCreditFacilities_doesNotMatchSameLast4AcrossBanks() {
        val d360 = Bank("D360")
        val state = DashboardUiState(
            creditFacilities = CreditFacilitiesOverview(
                facilities = listOf(
                    facility("1234", emptyList(), bank = d360),
                    facility("1234", emptyList(), bank = Bank.BANK_ALJAZIRA),
                ),
                debitCards = emptyList(),
                legacyFlat = emptyOverview(),
                currency = Currency.SAR,
            ),
            ownedCards = listOf(
                OwnedCardUi(Bank.BANK_ALJAZIRA, "1234", cardNetwork = CardNetwork.VISA),
            ),
        )

        val filtered = state.followedCreditFacilities()

        assertNotNull(filtered)
        assertEquals(1, filtered!!.facilities.size)
        assertEquals(Bank.BANK_ALJAZIRA, filtered.facilities.single().bank)
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

    private fun facility(
        primary: String,
        supplementaries: List<String>,
        bank: Bank = Bank.BANK_ALJAZIRA,
    ): CreditFacilityOverview {
        val primaryRow = cardRow(primary, bank)
        return CreditFacilityOverview(
            bank = bank,
            primary = primaryRow,
            supplementaries = supplementaries.map { cardRow(it, bank) },
            facilityDue = null,
            facilitySalaryPeriodSpending = SignedMoneyAmount.zero(Currency.SAR),
            facilityStatementSpending = SignedMoneyAmount.zero(Currency.SAR),
            aggregateStatementPeriodLabel = null,
            salaryPeriodLabel = null,
            currency = Currency.SAR,
        )
    }

    private fun cardRow(last4: String, bank: Bank = Bank.BANK_ALJAZIRA) = CreditCardDashboardRow(
        bank = bank,
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
        linkedAccountMaskedNumber = null,
        network = CardNetwork.MADA,
        salaryPeriodSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
        salaryPeriodLabel = null,
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
