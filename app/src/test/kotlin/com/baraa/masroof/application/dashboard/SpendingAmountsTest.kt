package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class SpendingAmountsTest {
    @Test
    fun creditFacilitiesOverview_separatesCreditAndDebitAggregates() {
        val overview = CreditFacilitiesOverview(
            facilities = listOf(
                facilitySpending("100.00", "80.00"),
                facilitySpending("50.00", "40.00"),
            ),
            debitCards = listOf(
                debitSpending("120.00"),
                debitSpending("30.00"),
            ),
            legacyFlat = emptyCreditOverview(),
            currency = Currency.SAR,
        )

        assertEquals(
            BigDecimal("150.00"),
            overview.aggregateCreditSalaryPeriodSpending().amount,
        )
        assertEquals(
            BigDecimal("120.00"),
            overview.aggregateCreditStatementSpending().amount,
        )
        assertEquals(
            BigDecimal("150.00"),
            overview.aggregateDebitSalaryPeriodSpending().amount,
        )
    }

    @Test
    fun aggregateFacilityDue_usesLatestFacilityStatement() {
        val older = StatementDueSnapshot(
            amount = Money.of("100.00", Currency.SAR),
            updatedAt = Instant.parse("2026-08-01T09:00:00Z"),
            dueDate = LocalDate.parse("2026-09-01"),
        )
        val newer = StatementDueSnapshot(
            amount = Money.of("250.00", Currency.SAR),
            updatedAt = Instant.parse("2026-08-10T09:00:00Z"),
            dueDate = LocalDate.parse("2026-09-07"),
        )
        val overview = CreditFacilitiesOverview(
            facilities = listOf(
                facilitySpending("0.00", "0.00", due = older),
                facilitySpending("0.00", "0.00", due = newer),
            ),
            debitCards = emptyList(),
            legacyFlat = emptyCreditOverview(),
            currency = Currency.SAR,
        )

        assertEquals(Money.of("250.00", Currency.SAR), overview.aggregateFacilityDue()?.amount)
    }

    @Test
    fun aggregateFacilityDue_nullWhenNoCreditFacilities() {
        val overview = CreditFacilitiesOverview(
            facilities = emptyList(),
            debitCards = listOf(debitSpending("50.00")),
            legacyFlat = emptyCreditOverview(),
            currency = Currency.SAR,
        )

        assertNull(overview.aggregateFacilityDue())
    }

    private fun facilitySpending(
        salary: String,
        statement: String,
        due: StatementDueSnapshot? = null,
    ): CreditFacilityOverview {
        val row = CreditCardDashboardRow(
            bank = com.baraa.masroof.domain.model.Bank.BANK_ALJAZIRA,
            last4 = "1111",
            calendarMonthSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
            statementSpendingNet = SignedMoneyAmount.of(Money.of(statement, Currency.SAR)),
            salaryPeriodSpendingNet = SignedMoneyAmount.of(Money.of(salary, Currency.SAR)),
            statementPeriodLabel = null,
            snapshot = null,
        )
        return CreditFacilityOverview(
            bank = row.bank,
            primary = row,
            supplementaries = emptyList(),
            facilityDue = due,
            facilitySalaryPeriodSpending = SignedMoneyAmount.of(Money.of(salary, Currency.SAR)),
            facilityStatementSpending = SignedMoneyAmount.of(Money.of(statement, Currency.SAR)),
            aggregateStatementPeriodLabel = null,
            salaryPeriodLabel = "Aug",
            currency = Currency.SAR,
        )
    }

    private fun debitSpending(amount: String): DebitCardOverview =
        DebitCardOverview(
            bank = com.baraa.masroof.domain.model.Bank.BANK_ALJAZIRA,
            last4 = "2210",
            displayLabel = "Mada ••2210",
            linkedAccountLabel = null,
            linkedAccountMaskedNumber = null,
            network = com.baraa.masroof.domain.model.CardNetwork.MADA,
            salaryPeriodSpendingNet = SignedMoneyAmount.of(Money.of(amount, Currency.SAR)),
            salaryPeriodLabel = "Aug",
        )

    private fun emptyCreditOverview(): CreditCardsOverview =
        CreditCardsOverview(
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
