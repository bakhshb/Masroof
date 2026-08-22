package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentAccountFlowDetailGrouperTest {
    @Test
    fun groupsExpenseTransactionsIntoMatchingCategories() {
        val accountId = "account:bank_aljazira:3001"
        val cardId = "card:bank_aljazira:7271"
        val transactions = listOf(
            tx("xfer-out", FinancialTransactionType.EXTERNAL_TRANSFER_OUT, "100", source = accountId),
            tx("card-pay", FinancialTransactionType.CREDIT_CARD_PAYMENT, "500", source = accountId, dest = cardId),
            tx("cash", FinancialTransactionType.CASH_WITHDRAWAL, "50", source = accountId),
            tx("pos", FinancialTransactionType.EXPENSE, "90", source = accountId),
            tx("card-exp", FinancialTransactionType.EXPENSE, "75", source = cardId),
        )

        val grouping = CurrentAccountFlowDetailGrouper.group(
            transactions = transactions,
            parsedRecords = emptyList(),
        )

        assertEquals(listOf("xfer-out"), ids(grouping, FlowExpenseCategory.EXTERNAL_TRANSFER_OUT))
        assertEquals(listOf("card-pay"), ids(grouping, FlowExpenseCategory.CREDIT_CARD_PAYMENT))
        assertEquals(listOf("cash"), ids(grouping, FlowExpenseCategory.CASH_WITHDRAWAL))
        assertEquals(listOf("pos"), ids(grouping, FlowExpenseCategory.POS_PURCHASE))
        assertEquals(emptyList<String>(), ids(grouping, FlowExpenseCategory.BILL_PAYMENT))
        assertEquals(emptyList<String>(), ids(grouping, FlowExpenseCategory.FEE))
    }

    @Test
    fun groupsIncomeTransactionsIntoSalaryAndTransfers() {
        val accountId = "account:bank_aljazira:3001"
        val transactions = listOf(
            tx("income", FinancialTransactionType.INCOME, "15000", dest = accountId),
            tx("xfer-in", FinancialTransactionType.EXTERNAL_TRANSFER_IN, "200", dest = accountId),
        )

        val grouping = CurrentAccountFlowDetailGrouper.group(
            transactions = transactions,
            parsedRecords = emptyList(),
        )

        assertEquals(listOf("income"), ids(grouping, FlowIncomeCategory.SALARY))
        assertEquals(listOf("xfer-in"), ids(grouping, FlowIncomeCategory.EXTERNAL_TRANSFER_IN))
        assertEquals(emptyList<String>(), ids(grouping, FlowIncomeCategory.OTHER_INCOME))
    }

    @Test
    fun groupedExpenseTotalsMatchSummaryCalculator() {
        val accountId = "account:bank_aljazira:3001"
        val cardId = "card:bank_aljazira:7271"
        val transactions = listOf(
            tx("income", FinancialTransactionType.INCOME, "15000", dest = accountId),
            tx("xfer-in", FinancialTransactionType.EXTERNAL_TRANSFER_IN, "200", dest = accountId),
            tx("card-pay", FinancialTransactionType.CREDIT_CARD_PAYMENT, "500", source = accountId, dest = cardId),
            tx("xfer-out", FinancialTransactionType.EXTERNAL_TRANSFER_OUT, "100", source = accountId),
            tx("cash", FinancialTransactionType.CASH_WITHDRAWAL, "50", source = accountId),
            tx("pos", FinancialTransactionType.EXPENSE, "90", source = accountId),
            tx("card-exp", FinancialTransactionType.EXPENSE, "75", source = cardId),
        )

        val summary = CurrentAccountSummaryCalculator.summarize(
            transactions = transactions,
            parsedRecords = emptyList(),
        )
        val grouping = CurrentAccountFlowDetailGrouper.group(
            transactions = transactions,
            parsedRecords = emptyList(),
        )

        assertEquals(
            summary.outflow.externalTransfersOut,
            sumGrouped(grouping, FlowExpenseCategory.EXTERNAL_TRANSFER_OUT),
        )
        assertEquals(
            summary.outflow.creditCardPayments,
            sumGrouped(grouping, FlowExpenseCategory.CREDIT_CARD_PAYMENT),
        )
        assertEquals(
            summary.outflow.cashWithdrawals,
            sumGrouped(grouping, FlowExpenseCategory.CASH_WITHDRAWAL),
        )
        assertEquals(
            summary.outflow.posPurchases,
            sumGrouped(grouping, FlowExpenseCategory.POS_PURCHASE),
        )
    }

    @Test
    fun groupsSelfTransfersSeparatelyFromCoreExpenseCategories() {
        val accountA = "account:bank_aljazira:3001"
        val accountB = "account:bank_aljazira:3002"
        val transactions = listOf(
            tx(
                id = "self",
                type = FinancialTransactionType.SELF_TRANSFER,
                amount = "500",
                source = accountA,
                dest = accountB,
            ),
            tx("xfer-out", FinancialTransactionType.EXTERNAL_TRANSFER_OUT, "100", source = accountA),
        )

        val grouping = CurrentAccountFlowDetailGrouper.group(
            transactions = transactions,
            parsedRecords = emptyList(),
            ownedAccountContainerIds = setOf(accountA, accountB),
            ownedAccountLast4s = setOf("3001", "3002"),
        )

        assertEquals(listOf("self"), grouping.selfTransfersOut.map { it.id })
        assertEquals(listOf("self"), grouping.selfTransfersIn.map { it.id })
        assertEquals(listOf("xfer-out"), ids(grouping, FlowExpenseCategory.EXTERNAL_TRANSFER_OUT))
    }

    @Test
    fun smsResolvedCashWithdrawalWithoutSource_groupsAsCashWithdrawalNotFee() {
        val owned = "account:bank_aljazira:3001"
        val cash = com.baraa.masroof.domain.model.FinancialTransaction(
            id = "cash-expense",
            type = FinancialTransactionType.EXPENSE,
            amount = Money.of("500.00", Currency.SAR),
            occurredAt = java.time.Instant.parse("2026-08-10T12:00:00Z"),
            sourceContainerId = null,
            destinationContainerId = null,
            merchant = null,
            counterparty = null,
            categoryId = null,
            linkedParsedEventIds = listOf("evt-cash"),
            appliedExchangeRate = null,
            exchangeRateSource = null,
        )
        val parsedRecords = listOf(
            ParsedEventRecord(
                event = ParsedEvent(
                    id = "evt-cash",
                    rawSmsId = "sms-evt-cash",
                    bank = Bank.BANK_ALJAZIRA,
                    messageFamily = MessageFamily.WITHDRAWAL,
                    direction = MoneyDirection.OUTGOING,
                    amount = Money.of("500.00", Currency.SAR),
                    purchaseChannel = null,
                    sourceAccountRef = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
                    destinationAccountRef = null,
                    cardRef = null,
                    merchant = null,
                    counterparty = "سحب نقدي\nمن حساب: 3001",
                    occurredAt = java.time.Instant.parse("2026-08-10T12:00:00Z"),
                    bankNetworkType = null,
                    confidence = Confidence(1.0),
                    parseStatus = ParseStatus.SUCCESS,
                ),
                details = ParsedEventDetails(),
            ),
        )
        val rawSmsById = mapOf(
            "sms-evt-cash" to RawSms(
                id = "sms-evt-cash",
                sender = "AlJazira",
                body = "سحب نقدي\nمن حساب: 3001",
                receivedAt = java.time.Instant.parse("2026-08-10T12:00:00Z"),
                deviceMessageId = "1",
                bodyHash = "h",
            ),
        )

        val grouping = CurrentAccountFlowDetailGrouper.group(
            transactions = listOf(cash),
            parsedRecords = parsedRecords,
            ownedAccountContainerIds = setOf(owned),
            ownedAccountLast4s = setOf("3001"),
            rawSmsById = rawSmsById,
        )

        assertEquals(listOf("cash-expense"), ids(grouping, FlowExpenseCategory.CASH_WITHDRAWAL))
        assertEquals(emptyList<String>(), ids(grouping, FlowExpenseCategory.FEE))
    }

    private fun ids(
        grouping: CurrentAccountFlowDetailGrouping,
        category: FlowExpenseCategory,
    ): List<String> = grouping.expense[category].orEmpty().map { it.id }

    private fun ids(
        grouping: CurrentAccountFlowDetailGrouping,
        category: FlowIncomeCategory,
    ): List<String> = grouping.income[category].orEmpty().map { it.id }

    private fun sumGrouped(
        grouping: CurrentAccountFlowDetailGrouping,
        category: FlowExpenseCategory,
    ): Money = grouping.expense[category].orEmpty()
        .fold(Money.zero(Currency.SAR)) { acc, tx -> acc + tx.amount }

    private fun tx(
        id: String,
        type: FinancialTransactionType,
        amount: String,
        source: String? = null,
        dest: String? = null,
    ): com.baraa.masroof.domain.model.FinancialTransaction =
        com.baraa.masroof.domain.model.FinancialTransaction(
            id = id,
            type = type,
            amount = Money.of(amount, Currency.SAR),
            occurredAt = java.time.Instant.parse("2026-08-01T10:00:00Z"),
            sourceContainerId = source,
            destinationContainerId = dest,
            merchant = null,
            counterparty = null,
            categoryId = null,
            linkedParsedEventIds = emptyList(),
            appliedExchangeRate = null,
            exchangeRateSource = null,
        )
}
