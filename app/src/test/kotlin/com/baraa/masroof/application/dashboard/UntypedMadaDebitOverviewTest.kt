package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardNetwork
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.OwnershipStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class UntypedMadaDebitOverviewTest {
    @Test
    fun untypedMadaNetworkCard_appearsInDebitOverviewAndHierarchy() {
        val bank = Bank.BANK_ALJAZIRA
        val ownedAccounts = listOf(
            com.baraa.masroof.domain.model.AccountRegistryEntry.forTest(
                bank = bank,
                maskedNumber = "****3001",
                ownership = OwnershipStatus.OWNED,
                accountType = com.baraa.masroof.domain.model.AccountType.CURRENT,
                firstSeenRawSmsId = "sms",
                lastSeenRawSmsId = "sms",
            ),
        )
        val accountsFleet = AccountsSummary(
            accounts = listOf(
                OwnedAccount.from(
                    bank = bank,
                    maskedNumber = "****3001",
                    summary = CurrentAccountSummary.zero(Currency.SAR),
                )!!,
            ),
        )
        val madaCard = CardRegistryEntry.forTest(
            bank = bank,
            last4 = "8219",
            ownership = OwnershipStatus.OWNED,
            cardType = null,
            cardNetwork = CardNetwork.MADA,
            linkedAccountBankId = bank.id,
            linkedAccountMaskedNumber = "3001",
            firstSeenRawSmsId = "sms",
            lastSeenRawSmsId = "sms",
        )
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

        val facilities = CreditFacilityOverviewBuilder.build(
            overview = overview,
            registryCards = listOf(madaCard),
            registryAccounts = ownedAccounts,
        )

        assertEquals(listOf("8219"), facilities.debitCards.map { it.last4 })
        assertEquals(0, facilities.facilities.size)

        val hierarchy = BankHierarchyBuilder.build(
            ownedAccounts = ownedAccounts,
            accountsFleet = accountsFleet,
            creditFacilities = facilities,
        )
        val tree = hierarchy.banks.single()
        assertEquals(listOf("8219"), tree.currentAccounts.single().debitCards.map { it.last4 })
    }
}
