package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class CurrentAccountSummaryCalculatorTest {
    @Test
    fun splitsAccountFlowsAndExcludesCreditCardPurchases() {
        val accountId = "account:bank_aljazira:3001"
        val cardId = "card:bank_aljazira:7271"
        val summary = CurrentAccountSummaryCalculator.summarize(
            transactions = listOf(
                tx("income", FinancialTransactionType.INCOME, "15000", source = accountId),
                tx("xfer-in", FinancialTransactionType.EXTERNAL_TRANSFER_IN, "200", dest = accountId),
                tx("card-pay", FinancialTransactionType.CREDIT_CARD_PAYMENT, "500", source = accountId, dest = cardId),
                tx("xfer-out", FinancialTransactionType.EXTERNAL_TRANSFER_OUT, "100", source = accountId),
                tx("cash", FinancialTransactionType.CASH_WITHDRAWAL, "50", source = accountId),
                tx("pos", FinancialTransactionType.EXPENSE, "90", source = accountId),
                tx("card-exp", FinancialTransactionType.EXPENSE, "75", source = cardId),
            ),
            parsedRecords = emptyList(),
        )

        assertEquals(Money.of("15000.00", Currency.SAR), summary.income)
        assertEquals(Money.of("200.00", Currency.SAR), summary.externalTransfersIn)
        assertEquals(Money.of("500.00", Currency.SAR), summary.creditCardPayments)
        assertEquals(Money.of("100.00", Currency.SAR), summary.externalTransfersOut)
        assertEquals(Money.of("50.00", Currency.SAR), summary.cashWithdrawals)
        assertEquals(Money.of("90.00", Currency.SAR), summary.posPurchases)
        assertEquals(
            SignedMoneyAmount.of(Money.of("14460.00", Currency.SAR)),
            summary.netMovement,
        )
    }

    @Test
    fun billPaymentDetectedFromLinkedParsedEvent() {
        val accountId = "account:bank_aljazira:3001"
        val billTx = tx("bill", FinancialTransactionType.EXPENSE, "210", source = accountId, linked = listOf("evt-bill"))
        val summary = CurrentAccountSummaryCalculator.summarize(
            transactions = listOf(billTx),
            parsedRecords = listOf(
                parsedRecord("evt-bill", MessageFamily.BILL_PAYMENT),
            ),
        )
        assertEquals(Money.of("210.00", Currency.SAR), summary.billPayments)
        assertEquals(Money.zero(Currency.SAR), summary.posPurchases)
    }

    @Test
    fun spendingSplit_separatesAccountAndCard() {
        val accountId = "account:bank_aljazira:3001"
        val cardId = "card:bank_aljazira:7271"
        val split = CurrentAccountSummaryCalculator.spendingSplit(
            transactions = listOf(
                tx("pos", FinancialTransactionType.EXPENSE, "90", source = accountId),
                tx("card", FinancialTransactionType.EXPENSE, "75", source = cardId),
                tx("refund", FinancialTransactionType.REFUND, "10", dest = cardId),
            ),
            parsedRecords = emptyList(),
        )
        assertEquals(Money.of("90.00", Currency.SAR), split.fromCurrentAccount)
        assertEquals(SignedMoneyAmount.of(Money.of("65.00", Currency.SAR)), split.onCreditCard)
    }

    private fun tx(
        id: String,
        type: FinancialTransactionType,
        amount: String,
        source: String? = null,
        dest: String? = null,
        linked: List<String> = listOf("evt-$id"),
    ): FinancialTransaction =
        FinancialTransaction(
            id = id,
            type = type,
            amount = Money.of(amount, Currency.SAR),
            occurredAt = Instant.parse("2026-08-10T12:00:00Z"),
            sourceContainerId = source,
            destinationContainerId = dest,
            merchant = null,
            counterparty = null,
            categoryId = null,
            linkedParsedEventIds = linked,
        )

    private fun parsedRecord(id: String, family: MessageFamily): ParsedEventRecord {
        val event = ParsedEvent(
            id = id,
            rawSmsId = "sms-$id",
            bank = com.baraa.masroof.domain.model.Bank.BANK_ALJAZIRA,
            messageFamily = family,
            direction = com.baraa.masroof.domain.model.MoneyDirection.OUTGOING,
            amount = Money.of("1.00", Currency.SAR),
            purchaseChannel = null,
            sourceAccountRef = null,
            destinationAccountRef = null,
            cardRef = null,
            merchant = null,
            counterparty = null,
            occurredAt = Instant.parse("2026-08-10T12:00:00Z"),
            bankNetworkType = null,
            confidence = com.baraa.masroof.domain.model.Confidence(1.0),
            parseStatus = com.baraa.masroof.domain.model.ParseStatus.SUCCESS,
        )
        return ParsedEventRecord(event = event, details = ParsedEventDetails())
    }
}
