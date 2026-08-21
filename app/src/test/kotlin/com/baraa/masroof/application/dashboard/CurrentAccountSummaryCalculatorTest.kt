package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
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

        assertEquals(Money.of("15000.00", Currency.SAR), summary.inflow.salary)
        assertEquals(Money.zero(Currency.SAR), summary.inflow.otherIncome)
        assertEquals(Money.of("200.00", Currency.SAR), summary.inflow.externalTransfersIn)
        assertEquals(Money.of("500.00", Currency.SAR), summary.outflow.creditCardPayments)
        assertEquals(Money.of("100.00", Currency.SAR), summary.outflow.externalTransfersOut)
        assertEquals(Money.of("50.00", Currency.SAR), summary.outflow.cashWithdrawals)
        assertEquals(Money.of("90.00", Currency.SAR), summary.outflow.posPurchases)
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

        assertEquals(Money.of("3191.68", Currency.SAR), summary.inflow.salary)
        assertEquals(Money.zero(Currency.SAR), summary.inflow.externalTransfersIn)
    }

    @Test
    fun billPayment_autoAssemblesToBillPaymentType() {
        val owned = "account:bank_aljazira:3001"
        val billTx = tx(
            id = "bill",
            type = FinancialTransactionType.BILL_PAYMENT,
            amount = "210",
            source = owned,
            linked = listOf("evt-bill"),
        )
        val summary = CurrentAccountSummaryCalculator.summarize(
            transactions = listOf(billTx),
            parsedRecords = listOf(
                parsedRecord("evt-bill", MessageFamily.BILL_PAYMENT),
            ),
            ownedAccountContainerIds = setOf(owned),
            ownedAccountLast4s = setOf("3001"),
        )
        assertEquals(Money.of("210.00", Currency.SAR), summary.outflow.billPayments)
        assertEquals(Money.zero(Currency.SAR), summary.outflow.posPurchases)
        assertEquals(Money.zero(Currency.SAR), summary.outflow.fees)
    }

    @Test
    fun feeWithBillPaymentWording_countsAsBillPayment() {
        val owned = "account:bank_aljazira:3001"
        val feeBill = tx(
            id = "fee-bill",
            type = FinancialTransactionType.FEE,
            amount = "120.00",
            source = owned,
            linked = listOf("evt-fee-bill"),
        )
        val summary = CurrentAccountSummaryCalculator.summarize(
            transactions = listOf(feeBill),
            parsedRecords = listOf(
                parsedRecord(
                    id = "evt-fee-bill",
                    family = MessageFamily.BILL_PAYMENT,
                    sourceLast4 = "3001",
                    rawBody = "سداد فاتورة\nالمفوتر: STC",
                ),
            ),
            ownedAccountContainerIds = setOf(owned),
            ownedAccountLast4s = setOf("3001"),
            rawSmsById = mapOf(
                "sms-evt-fee-bill" to RawSms(
                    id = "sms-evt-fee-bill",
                    sender = "AlJazira",
                    body = "سداد فاتورة\nالمفوتر: STC",
                    receivedAt = Instant.parse("2026-08-10T12:00:00Z"),
                    deviceMessageId = "evt-fee-bill",
                    bodyHash = "evt-fee-bill",
                ),
            ),
        )
        assertEquals(Money.of("120.00", Currency.SAR), summary.outflow.billPayments)
        assertEquals(Money.zero(Currency.SAR), summary.outflow.fees)
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
        assertEquals(Money.of("210.00", Currency.SAR), summary.outflow.billPayments)
        assertEquals(Money.zero(Currency.SAR), summary.outflow.posPurchases)
    }

    @Test
    fun cashWithdrawalWithNullSourceContainer_ignoredWhenScopedToSingleAccount() {
        val owned = "account:bank_aljazira:3478"
        val cashWithdrawal = tx(
            id = "cash-withdrawal",
            type = FinancialTransactionType.CASH_WITHDRAWAL,
            amount = "2200.00",
            source = null,
            linked = emptyList(),
        )
        val summary = CurrentAccountSummaryCalculator.summarize(
            transactions = listOf(cashWithdrawal),
            parsedRecords = emptyList(),
            ownedAccountContainerIds = setOf(owned),
            ownedAccountLast4s = setOf("3478"),
            rawSmsById = emptyMap(),
        )
        assertEquals(Money.zero(Currency.SAR), summary.outflow.cashWithdrawals)
        assertEquals(Money.zero(Currency.SAR), summary.outflow.total)
    }

    @Test
    fun orphanOutflow_notDuplicatedAcrossOwnedAccounts() {
        val accountA = "account:bank_aljazira:3001"
        val accountB = "account:bank_aljazira:3002"
        val orphanWithdrawal = tx(
            id = "orphan-cash",
            type = FinancialTransactionType.CASH_WITHDRAWAL,
            amount = "100.00",
            source = null,
            linked = emptyList(),
        )
        val summaryA = CurrentAccountSummaryCalculator.summarize(
            transactions = listOf(orphanWithdrawal),
            parsedRecords = emptyList(),
            ownedAccountContainerIds = setOf(accountA),
            ownedAccountLast4s = setOf("3001"),
        )
        val summaryB = CurrentAccountSummaryCalculator.summarize(
            transactions = listOf(orphanWithdrawal),
            parsedRecords = emptyList(),
            ownedAccountContainerIds = setOf(accountB),
            ownedAccountLast4s = setOf("3002"),
        )
        assertEquals(Money.zero(Currency.SAR), summaryA.outflow.cashWithdrawals)
        assertEquals(Money.zero(Currency.SAR), summaryB.outflow.cashWithdrawals)
    }

    @Test
    fun accountRemaining_includesSelfTransfersInPerAccountCashPosition() {
        val accountA = "account:bank_aljazira:3001"
        val accountB = "account:bank_aljazira:3002"
        val summary = CurrentAccountSummaryCalculator.summarize(
            transactions = listOf(
                tx(
                    id = "transfer-in",
                    type = FinancialTransactionType.EXTERNAL_TRANSFER_IN,
                    amount = "1000",
                    dest = accountA,
                ),
                tx(
                    id = "purchase",
                    type = FinancialTransactionType.EXPENSE,
                    amount = "500",
                    source = accountA,
                ),
                tx(
                    id = "self-out",
                    type = FinancialTransactionType.SELF_TRANSFER,
                    amount = "200",
                    source = accountA,
                    dest = accountB,
                ),
            ),
            parsedRecords = emptyList(),
            ownedAccountContainerIds = setOf(accountA),
            ownedAccountLast4s = setOf("3001"),
        )
        assertEquals(Money.of("1000.00", Currency.SAR), summary.inflow.total)
        assertEquals(Money.of("700.00", Currency.SAR), summary.outflow.total)
        assertEquals(Money.of("200.00", Currency.SAR), summary.outflow.selfTransfersOut)
        assertEquals(
            SignedMoneyAmount.of(Money.of("300.00", Currency.SAR)),
            summary.cashPosition().remaining,
        )
        assertEquals(
            SignedMoneyAmount.of(Money.of("500.00", Currency.SAR)),
            summary.externalMovement().remaining,
        )
    }

    @Test
    fun accountOutflow_includesAllListedExpenseCategories() {
        val accountId = "account:bank_aljazira:3001"
        val cardId = "card:bank_aljazira:7271"
        val otherAccount = "account:bank_aljazira:3002"
        val summary = CurrentAccountSummaryCalculator.summarize(
            transactions = listOf(
                tx("xfer-out", FinancialTransactionType.EXTERNAL_TRANSFER_OUT, "100", source = accountId),
                tx("card-pay", FinancialTransactionType.CREDIT_CARD_PAYMENT, "50", source = accountId, dest = cardId),
                tx("cash", FinancialTransactionType.CASH_WITHDRAWAL, "30", source = accountId),
                tx("bill", FinancialTransactionType.BILL_PAYMENT, "40", source = accountId),
                tx("pos", FinancialTransactionType.EXPENSE, "60", source = accountId),
                tx("fee", FinancialTransactionType.FEE, "10", source = accountId),
                tx(
                    id = "self",
                    type = FinancialTransactionType.SELF_TRANSFER,
                    amount = "200",
                    source = accountId,
                    dest = otherAccount,
                ),
            ),
            parsedRecords = emptyList(),
            ownedAccountContainerIds = setOf(accountId),
            ownedAccountLast4s = setOf("3001"),
        )
        assertEquals(Money.of("100.00", Currency.SAR), summary.outflow.externalTransfersOut)
        assertEquals(Money.of("50.00", Currency.SAR), summary.outflow.creditCardPayments)
        assertEquals(Money.of("30.00", Currency.SAR), summary.outflow.cashWithdrawals)
        assertEquals(Money.of("40.00", Currency.SAR), summary.outflow.billPayments)
        assertEquals(Money.of("60.00", Currency.SAR), summary.outflow.posPurchases)
        assertEquals(Money.of("10.00", Currency.SAR), summary.outflow.fees)
        assertEquals(Money.of("200.00", Currency.SAR), summary.outflow.selfTransfersOut)
        assertEquals(
            Money.of("490.00", Currency.SAR),
            summary.outflow.total,
        )
    }

    @Test
    fun expenseResolvedFromReview_countsUsingLinkedAccountAndSmsFamily() {
        val owned = "account:bank_aljazira:3001"
        val cardPay = tx(
            id = "card-pay-expense",
            type = FinancialTransactionType.EXPENSE,
            amount = "802.62",
            source = null,
            linked = listOf("evt-card-pay"),
        )
        val cash = tx(
            id = "cash-expense",
            type = FinancialTransactionType.EXPENSE,
            amount = "500.00",
            source = null,
            linked = listOf("evt-cash"),
        )
        val bill = tx(
            id = "bill-expense",
            type = FinancialTransactionType.EXPENSE,
            amount = "210.00",
            source = null,
            linked = listOf("evt-bill"),
        )
        val parsedRecords = listOf(
            parsedRecord(
                id = "evt-card-pay",
                family = MessageFamily.CARD_PAYMENT,
                sourceLast4 = "3001",
                cardLast4 = "7271",
                rawBody = "سداد بطاقة ائتمانية\nمن حساب: 3001\nبطاقة: 7271",
            ),
            parsedRecord(
                id = "evt-cash",
                family = MessageFamily.WITHDRAWAL,
                sourceLast4 = "3001",
                rawBody = "سحب نقدي\nمن حساب: 3001",
            ),
            parsedRecord(
                id = "evt-bill",
                family = MessageFamily.BILL_PAYMENT,
                sourceLast4 = "3001",
                rawBody = "سداد فاتورة\nالمفوتر: TEST",
            ),
        )
        val rawSmsById = parsedRecords.associate { record ->
            record.event.rawSmsId to RawSms(
                id = record.event.rawSmsId,
                sender = "AlJazira",
                body = record.event.counterparty.orEmpty(),
                receivedAt = Instant.parse("2026-08-10T12:00:00Z"),
                deviceMessageId = record.event.id,
                bodyHash = record.event.id,
            )
        }
        val summary = CurrentAccountSummaryCalculator.summarize(
            transactions = listOf(cardPay, cash, bill),
            parsedRecords = parsedRecords,
            ownedAccountContainerIds = setOf(owned),
            ownedAccountLast4s = setOf("3001"),
            rawSmsById = rawSmsById,
        )
        assertEquals(Money.of("802.62", Currency.SAR), summary.outflow.creditCardPayments)
        assertEquals(Money.of("500.00", Currency.SAR), summary.outflow.cashWithdrawals)
        assertEquals(Money.of("210.00", Currency.SAR), summary.outflow.billPayments)
        assertEquals(Money.zero(Currency.SAR), summary.outflow.posPurchases)
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
            ownedAccountLast4s = setOf("3001"),
        )
        assertEquals(Money.of("90.00", Currency.SAR), summary.outflow.posPurchases)
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
            ownedAccountLast4s = setOf("3001", "3002"),
        )
        assertEquals(Money.of("500.00", Currency.SAR), summary.inflow.selfTransfersIn)
        assertEquals(Money.of("500.00", Currency.SAR), summary.outflow.selfTransfersOut)
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

    private fun parsedRecord(
        id: String,
        family: MessageFamily,
        sourceLast4: String? = null,
        cardLast4: String? = null,
        rawBody: String? = null,
    ): ParsedEventRecord {
        val event = ParsedEvent(
            id = id,
            rawSmsId = "sms-$id",
            bank = Bank.BANK_ALJAZIRA,
            messageFamily = family,
            direction = com.baraa.masroof.domain.model.MoneyDirection.INCOMING,
            amount = Money.of("1.00", Currency.SAR),
            purchaseChannel = null,
            sourceAccountRef = sourceLast4?.let { AccountReference(Bank.BANK_ALJAZIRA, it) },
            destinationAccountRef = null,
            cardRef = cardLast4?.let { CardReference(Bank.BANK_ALJAZIRA, it) },
            merchant = null,
            counterparty = rawBody,
            occurredAt = Instant.parse("2026-08-10T12:00:00Z"),
            bankNetworkType = null,
            confidence = com.baraa.masroof.domain.model.Confidence(1.0),
            parseStatus = com.baraa.masroof.domain.model.ParseStatus.SUCCESS,
        )
        return ParsedEventRecord(event = event, details = ParsedEventDetails())
    }
}
