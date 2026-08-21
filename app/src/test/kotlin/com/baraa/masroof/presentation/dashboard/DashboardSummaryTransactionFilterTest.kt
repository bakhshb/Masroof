package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DashboardSummaryTransactionFilterTest {
    @Test
    fun forAccount_matchesSourceOrDestinationContainer() {
        val containerId = FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "1234567890123001")
        val otherAccountId = FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "1234567890128842")
        val transactions = listOf(
            preview(id = "1", source = containerId, destination = null),
            preview(id = "2", source = otherAccountId, destination = null),
            preview(id = "3", source = null, destination = containerId),
        )

        val filtered = DashboardSummaryTransactionFilter.forAccount(
            transactions = transactions,
            bank = Bank.BANK_ALJAZIRA,
            maskedNumber = "1234567890123001",
        )

        assertEquals(listOf("1", "3"), filtered.map { it.id })
    }

    @Test
    fun forCard_matchesCardLast4() {
        val transactions = listOf(
            preview(id = "1", cardLast4 = "4821"),
            preview(id = "2", cardLast4 = "3109"),
            preview(id = "3", cardLast4 = null),
        )

        val filtered = DashboardSummaryTransactionFilter.forCard(
            transactions = transactions,
            last4 = "4821",
        )

        assertEquals(listOf("1"), filtered.map { it.id })
    }

    private fun preview(
        id: String,
        source: String? = null,
        destination: String? = null,
        cardLast4: String? = null,
    ): TransactionPreviewUi =
        TransactionPreviewUi(
            id = id,
            title = "Sample",
            amount = Money.of("10.00", Currency.SAR),
            localDate = LocalDate.of(2026, 8, 1),
            amountLabel = "10.00 SAR",
            dateLabel = "1 Aug",
            type = FinancialTransactionType.EXPENSE,
            typeLabelResHint = FinancialTransactionType.EXPENSE,
            direction = TransactionDirectionUi.OUTWARD,
            cardLast4 = cardLast4,
            sourceContainerId = source,
            destinationContainerId = destination,
            searchText = "sample",
        )
}
