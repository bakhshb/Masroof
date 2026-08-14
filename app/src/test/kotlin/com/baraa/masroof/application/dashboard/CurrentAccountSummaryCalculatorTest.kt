package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.RawSms
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

        assertEquals(Money.of("15000.00", Currency.SAR), summary.salary)
        assertEquals(Money.zero(Currency.SAR), summary.otherIncome)
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
    fun salaryTransferDetectedFromSmsWording() {
        val accountId = "account:bank_aljazira:3001"
        val salaryTx = tx(
            id = "salary-xfer",
            type = FinancialTransactionType.EXTERNAL_TRANSFER_IN,
            amount = "3191.68",
            dest = accountId,
            linked = listOf("evt-salary"),
        )
        val summary = CurrentAccountSummaryCalculator.summarize(
            transactions = listOf(salaryTx),
            parsedRecords = listOf(
                parsedRecord("evt-salary", MessageFamily.TRANSFER_IN),
            ),
            rawSmsById = mapOf(
                "sms-evt-salary" to RawSms(
                    id = "sms-evt-salary",
                    sender = "AlJazira",
                    body = "حوالة واردة راتب\nمبلغ: SAR 3,191.68",
                    receivedAt = Instant.parse("2026-07-27T01:12:00Z"),
                    deviceMessageId = "evt-salary",
                    bodyHash = "evt-salary",
                ),
            ),
        )

        assertEquals(Money.of("3191.68", Currency.SAR), summary.salary)
        assertEquals(Money.zero(Currency.SAR), summary.externalTransfersIn)
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
    fun summarize_filtersToOwnedAccountsOnly() {
        val owned = "account:bank_aljazira:3001"
        val other = "account:bank_aljazira:3002"
        val summary = CurrentAccountSummaryCalculator.summarize(
            transactions = listOf(
                tx("owned-pos", FinancialTransactionType.EXPENSE, "90", source = owned),
                tx("other-pos", FinancialTransactionType.EXPENSE, "40", source = other),
            ),
            parsedRecords = emptyList(),
            ownedAccountContainerIds = setOf(owned),
        )
        assertEquals(Money.of("90.00", Currency.SAR), summary.posPurchases)
    }

    @Test
    fun selfTransfersTrackedSeparatelyWithoutAffectingNet() {
        val ownedA = "account:bank_aljazira:3001"
        val ownedB = "account:bank_aljazira:3002"
        val summary = CurrentAccountSummaryCalculator.summarize(
            transactions = listOf(
                tx(
                    id = "self",
                    type = FinancialTransactionType.SELF_TRANSFER,
                    amount = "500",
                    source = ownedA,
                    dest = ownedB,
                ),
            ),
            parsedRecords = emptyList(),
            ownedAccountContainerIds = setOf(ownedA, ownedB),
        )
        assertEquals(Money.of("500.00", Currency.SAR), summary.selfTransfersIn)
        assertEquals(Money.of("500.00", Currency.SAR), summary.selfTransfersOut)
        assertEquals(SignedMoneyAmount.zero(Currency.SAR), summary.netMovement)
    }

    @Test
    fun spendingSplit_totalSpending_matchesCurrentAccountOutflow() {
        val accountId = "account:bank_aljazira:3001"
        val cardId = "card:bank_aljazira:7271"
        val split = CurrentAccountSummaryCalculator.spendingSplit(
            transactions = listOf(
                tx("pos", FinancialTransactionType.EXPENSE, "90", source = accountId),
                tx("xfer-out", FinancialTransactionType.EXTERNAL_TRANSFER_OUT, "100", source = accountId),
                tx("card-pay", FinancialTransactionType.CREDIT_CARD_PAYMENT, "50", source = accountId, dest = cardId),
                tx("card", FinancialTransactionType.EXPENSE, "75", source = cardId),
            ),
            parsedRecords = emptyList(),
        )
        assertEquals(Money.of("240.00", Currency.SAR), split.totalSpending)
        assertEquals(SignedMoneyAmount.of(Money.of("75.00", Currency.SAR)), split.creditCardPurchases)
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
            direction = com.baraa.masroof.domain.model.MoneyDirection.INCOMING,
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
