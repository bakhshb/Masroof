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
    fun alJaziraSetup_groupsPrimaryAndTwoSupplementariesWithMadaDebit() {
        val overview = CreditCardsOverview(
            cards = listOf(
                row("1111", "400.00"),
                row("2222", "150.00"),
                row("3333", "75.00"),
            ),
            aggregateDueAmount = null,
            aggregateDueUpdatedAt = null,
            aggregateDueDate = null,
            aggregatePeriodSpendingNet = SignedMoneyAmount(BigDecimal("625.00"), Currency.SAR),
            aggregateStatementSpendingNet = SignedMoneyAmount(BigDecimal("625.00"), Currency.SAR),
            aggregateStatementPeriodLabel = "Jul-Aug",
            calendarMonthLabel = null,
            salaryPeriodLabel = "Aug",
            currency = Currency.SAR,
        )
        val registry = listOf(
            card("1111", CardRole.PRIMARY, CardType.CREDIT),
            card("2222", CardRole.SUPPLEMENTARY, CardType.CREDIT, parent = "1111"),
            card("3333", CardRole.SUPPLEMENTARY, CardType.CREDIT, parent = "1111"),
            debit("9999"),
        )

        val facilities = CreditFacilityOverviewBuilder.build(overview, registry)

        assertEquals(1, facilities.facilities.size)
        assertEquals(1, facilities.debitCards.size)
        assertEquals("1111", facilities.facilities.single().primaryLast4)
        assertEquals(2, facilities.facilities.single().supplementaries.size)
        assertEquals(
            BigDecimal("625.00"),
            facilities.facilities.single().facilityStatementSpending.amount,
        )
        assertEquals("9999", facilities.debitCards.single().last4)
    }

    @Test
    fun noOwnedCredit_doesNotFallbackToAllTransactionCards() {
        val overview = CreditCardsOverview(
            cards = listOf(row("1111", "100.00")),
            aggregateDueAmount = null,
            aggregateDueUpdatedAt = null,
            aggregateDueDate = null,
            aggregatePeriodSpendingNet = SignedMoneyAmount(BigDecimal("100.00"), Currency.SAR),
            aggregateStatementSpendingNet = SignedMoneyAmount(BigDecimal("100.00"), Currency.SAR),
            aggregateStatementPeriodLabel = "Jul-Aug",
            calendarMonthLabel = null,
            salaryPeriodLabel = "Aug",
            currency = Currency.SAR,
        )

        val facilities = CreditFacilityOverviewBuilder.build(overview, registryCards = emptyList())

        assertEquals(0, facilities.facilities.size)
    }

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

    @Test
    fun debitLinkedAccount_usesAccountDisplayNameFromRegistry() {
        val overview = CreditCardsOverview(
            cards = emptyList(),
            aggregateDueAmount = null,
            aggregateDueUpdatedAt = null,
            aggregateDueDate = null,
            aggregatePeriodSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
            aggregateStatementSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
            aggregateStatementPeriodLabel = null,
            calendarMonthLabel = null,
            salaryPeriodLabel = "Aug",
            currency = Currency.SAR,
        )
        val registryAccounts = listOf(
            com.baraa.masroof.domain.model.AccountRegistryEntry(
                bank = Bank.BANK_ALJAZIRA,
                maskedNumber = "1234567890",
                ownership = OwnershipStatus.OWNED,
                displayName = "Home",
                firstSeenRawSmsId = "sms",
                lastSeenRawSmsId = "sms",
            ),
        )

        val facilities = CreditFacilityOverviewBuilder.build(
            overview = overview,
            registryCards = listOf(debit("9999")),
            registryAccounts = registryAccounts,
        )

        assertEquals("Home", facilities.debitCards.single().linkedAccountLabel)
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
        cardType: CardType = CardType.CREDIT,
        parent: String? = null,
    ): CardRegistryEntry =
        CardRegistryEntry(
            bank = Bank.BANK_ALJAZIRA,
            last4 = last4,
            ownership = OwnershipStatus.OWNED,
            cardType = cardType,
            cardRole = role,
            parentCardLast4 = parent,
            firstSeenRawSmsId = "sms",
            lastSeenRawSmsId = "sms",
        )

    private fun debit(last4: String): CardRegistryEntry =
        CardRegistryEntry(
            bank = Bank.BANK_ALJAZIRA,
            last4 = last4,
            ownership = OwnershipStatus.OWNED,
            cardType = CardType.DEBIT,
            cardNetwork = com.baraa.masroof.domain.model.CardNetwork.MADA,
            linkedAccountBankId = Bank.BANK_ALJAZIRA.id,
            linkedAccountMaskedNumber = "1234567890",
            firstSeenRawSmsId = "sms",
            lastSeenRawSmsId = "sms",
        )
}
