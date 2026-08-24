package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardNetwork
import com.baraa.masroof.domain.model.FinancialTransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DashboardRegistryLabelsTest {
    @Test
    fun resolveAccountLabel_customNameWins() {
        val label = DashboardRegistryLabels.resolveAccountLabel(
            displayName = "  Home  ",
            maskedNumber = "3001",
            last4Template = { "Account ···$it" },
        )
        assertEquals("Home", label)
    }

    @Test
    fun resolveAccountLabel_blankFallsBackToLast4() {
        val label = DashboardRegistryLabels.resolveAccountLabel(
            displayName = "   ",
            maskedNumber = "3001",
            last4Template = { "Account ···$it" },
        )
        assertEquals("Account ···3001", label)
    }

    @Test
    fun resolveCardLabel_customNameWins() {
        val label = DashboardRegistryLabels.resolveCardLabel(
            displayName = "Travel",
            last4 = "7271",
            last4Template = { "Card ···$it" },
        )
        assertEquals("Travel", label)
    }

    @Test
    fun accountLabel_lookupByBankAndMaskedNumber() {
        val accounts = listOf(
            OwnedAccountUi(
                bank = Bank.BANK_ALJAZIRA,
                maskedNumber = "3001",
                displayName = "Salary",
            ),
        )
        val label = DashboardRegistryLabels.resolveAccountLabel(
            displayName = accounts.single().displayName,
            maskedNumber = accounts.single().maskedNumber,
            last4Template = { "Account ···$it" },
        )
        assertEquals("Salary", label)
    }

    @Test
    fun cardLabel_lookupByBankAndLast4() {
        val cards = listOf(
            OwnedCardUi(
                bank = Bank.BANK_ALJAZIRA,
                last4 = "7271",
                displayName = "Main CC",
            ),
        )
        val label = DashboardRegistryLabels.resolveCardLabel(
            displayName = cards.single().displayName,
            last4 = cards.single().last4,
            last4Template = { "Card ···$it" },
        )
        assertEquals("Main CC", label)
    }

    @Test
    fun accountLabel_lookupByContainerId() {
        val label = DashboardRegistryLabels.resolveAccountLabel(
            displayName = "Bills",
            maskedNumber = "6810",
            last4Template = { "Account ···$it" },
        )
        assertEquals("Bills", label)
    }

    @Test
    fun resolveCardNetwork_usesBankAndLast4FromCardContainer() {
        val visa = OwnedCardUi(Bank.BANK_ALJAZIRA, "7271", cardNetwork = CardNetwork.VISA)
        val mada = OwnedCardUi(Bank.BANK_ALJAZIRA, "2210", cardNetwork = CardNetwork.MADA)
        val cardId = FinancialContainerIdFactory.cardId(Bank.BANK_ALJAZIRA, "7271")

        val network = resolveCardNetworkFromTransaction(
            row = preview(cardLast4 = "7271", source = cardId),
            cards = listOf(visa, mada),
        )

        assertEquals(CardNetwork.VISA, network)
    }

    @Test
    fun resolveCardNetwork_madaDebit_matchesOwnedDebitCard() {
        val mada = OwnedCardUi(Bank.BANK_ALJAZIRA, "8219", cardNetwork = CardNetwork.MADA)
        val cardId = FinancialContainerIdFactory.cardId(Bank.BANK_ALJAZIRA, "8219")

        val network = resolveCardNetworkFromTransaction(
            row = preview(cardLast4 = "8219", source = cardId),
            cards = listOf(mada),
        )

        assertEquals(CardNetwork.MADA, network)
    }

    @Test
    fun resolveCardNetwork_unknownOrMissing_returnsNull() {
        val unknown = OwnedCardUi(Bank.BANK_ALJAZIRA, "1111", cardNetwork = CardNetwork.UNKNOWN)
        assertNull(
            resolveCardNetworkFromTransaction(
                row = preview(cardLast4 = "1111"),
                cards = listOf(unknown),
            ),
        )
        assertNull(
            resolveCardNetworkFromTransaction(
                row = preview(cardLast4 = null),
                cards = listOf(unknown),
            ),
        )
    }

    private fun preview(
        cardLast4: String?,
        source: String? = null,
        destination: String? = null,
    ): TransactionPreviewUi =
        TransactionPreviewUi(
            id = "tx",
            title = "Tamara",
            amount = Money.of("10.00", Currency.SAR),
            localDate = LocalDate.of(2026, 8, 23),
            amountLabel = "10.00 SAR",
            dateLabel = "23 Aug",
            type = FinancialTransactionType.EXPENSE,
            typeLabelResHint = FinancialTransactionType.EXPENSE,
            direction = TransactionDirectionUi.OUTWARD,
            cardLast4 = cardLast4,
            sourceContainerId = source,
            destinationContainerId = destination,
            searchText = "tamara",
        )
}
