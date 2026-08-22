package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.CardRole
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.OwnershipStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class CreditFacilityOverviewBuilderTest {
    @Test
    fun groupsPrimaryWithSupplementariesAndSumsFacilitySpending() {
        val overview = CreditCardsOverview(
            cards = listOf(
                row("1111", "100.00"),
                row("2222", "50.00"),
                row("3333", "25.00"),
            ),
            aggregateDueAmount = null,
            aggregateDueUpdatedAt = null,
            aggregateDueDate = null,
            aggregatePeriodSpendingNet = SignedMoneyAmount(BigDecimal("175.00"), Currency.SAR),
            aggregateStatementSpendingNet = SignedMoneyAmount(BigDecimal("175.00"), Currency.SAR),
            aggregateStatementPeriodLabel = "Jul-Aug",
            calendarMonthLabel = null,
            salaryPeriodLabel = "Aug",
            currency = Currency.SAR,
        )
        val registry = listOf(
            card("1111", CardRole.PRIMARY),
            card("2222", CardRole.SUPPLEMENTARY, parent = "1111"),
            card("3333", CardRole.SUPPLEMENTARY, parent = "1111"),
        )

        val facilities = CreditFacilityOverviewBuilder.build(overview, registry)

        assertEquals(1, facilities.facilities.size)
        val facility = facilities.facilities.single()
        assertEquals("1111", facility.primaryLast4)
        assertEquals(2, facility.supplementaries.size)
        assertEquals(
            BigDecimal("175.00"),
            facility.facilityStatementSpending.amount,
        )
        assertNull(facility.facilityDue)
    }

    private fun row(last4: String, amount: String): CreditCardDashboardRow =
        CreditCardDashboardRow(
            bank = Bank.BANK_ALJAZIRA,
            last4 = last4,
            calendarMonthSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
            statementSpendingNet = SignedMoneyAmount(BigDecimal(amount), Currency.SAR),
            salaryPeriodSpendingNet = SignedMoneyAmount(BigDecimal(amount), Currency.SAR),
            statementPeriodLabel = "Jul-Aug",
            snapshot = null,
        )

    private fun card(
        last4: String,
        role: CardRole,
        parent: String? = null,
    ): CardRegistryEntry =
        CardRegistryEntry(
            bank = Bank.BANK_ALJAZIRA,
            last4 = last4,
            ownership = OwnershipStatus.OWNED,
            cardType = CardType.CREDIT,
            cardRole = role,
            parentCardLast4 = parent,
            firstSeenRawSmsId = "sms",
            lastSeenRawSmsId = "sms",
        )
}
