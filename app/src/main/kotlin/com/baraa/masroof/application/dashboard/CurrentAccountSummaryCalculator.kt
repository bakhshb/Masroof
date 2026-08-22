package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord

object CurrentAccountSummaryCalculator {
    fun summarize(
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
        primaryCurrency: Currency = Currency.SAR,
        sarEquivalents: Map<String, Money> = emptyMap(),
        ownedAccountContainerIds: Set<String> = emptySet(),
        ownedAccountLast4s: Set<String> = emptySet(),
        rawSmsById: Map<String, RawSms> = emptyMap(),
        scopeMode: AccountFlowScopeMode = AccountFlowScopeMode.Fleet,
    ): CurrentAccountSummary {
        val billPaymentTxIds = resolveBillPaymentTransactionIds(transactions, parsedRecords)
        val parsedRecordsById = parsedRecords.associateBy { it.event.id }
        val scope = CurrentAccountTransactionScope(
            ownedContainerIds = ownedAccountContainerIds,
            ownedAccountLast4s = ownedAccountLast4s,
            mode = scopeMode,
        )

        var salary = Money.zero(primaryCurrency)
        var otherIncome = Money.zero(primaryCurrency)
        var externalTransfersIn = Money.zero(primaryCurrency)
        var selfTransfersIn = Money.zero(primaryCurrency)
        var selfTransfersOut = Money.zero(primaryCurrency)
        var creditCardPayments = Money.zero(primaryCurrency)
        var billPayments = Money.zero(primaryCurrency)
        var externalTransfersOut = Money.zero(primaryCurrency)
        var cashWithdrawals = Money.zero(primaryCurrency)
        var posPurchases = Money.zero(primaryCurrency)
        var fees = Money.zero(primaryCurrency)

        for (tx in transactions) {
            val amount = effectiveAmount(tx, primaryCurrency, sarEquivalents) ?: continue
            when (tx.type) {
                FinancialTransactionType.INCOME -> {
                    if (!scope.involvesOwnedDestination(tx, parsedRecordsById, rawSmsById)) continue
                    if (SalaryIncomeHeuristics.isSalaryIncome(tx, parsedRecordsById, rawSmsById)) {
                        salary += amount
                    } else {
                        otherIncome += amount
                    }
                }

                FinancialTransactionType.EXTERNAL_TRANSFER_IN -> {
                    if (!scope.involvesOwnedDestination(tx, parsedRecordsById, rawSmsById)) continue
                    if (SalaryIncomeHeuristics.isSalaryIncome(tx, parsedRecordsById, rawSmsById)) {
                        salary += amount
                    } else {
                        externalTransfersIn += amount
                    }
                }

                FinancialTransactionType.CREDIT_CARD_PAYMENT -> {
                    if (!scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) continue
                    creditCardPayments += amount
                }

                FinancialTransactionType.EXTERNAL_TRANSFER_OUT -> {
                    if (!scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) continue
                    externalTransfersOut += amount
                }

                FinancialTransactionType.CASH_WITHDRAWAL -> {
                    if (!scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) continue
                    cashWithdrawals += amount
                }

                FinancialTransactionType.BILL_PAYMENT -> {
                    if (!scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) continue
                    billPayments += amount
                }

                FinancialTransactionType.EXPENSE -> {
                    if (scope.isCreditCardSourcedExpenseWithoutOwnedAccount(
                            tx,
                            parsedRecordsById,
                            rawSmsById,
                        )
                    ) {
                        continue
                    }
                    if (!scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) continue
                    when {
                        scope.isCreditCardPayment(tx, parsedRecordsById, rawSmsById) ->
                            creditCardPayments += amount

                        scope.isCashWithdrawal(tx, parsedRecordsById, rawSmsById) ->
                            cashWithdrawals += amount

                        scope.isBillPayment(tx, billPaymentTxIds, parsedRecordsById, rawSmsById) ->
                            billPayments += amount

                        else -> posPurchases += amount
                    }
                }

                FinancialTransactionType.FEE -> {
                    if (scope.isCreditCardSourcedExpenseWithoutOwnedAccount(
                            tx,
                            parsedRecordsById,
                            rawSmsById,
                        )
                    ) {
                        continue
                    }
                    if (!scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) continue
                    when {
                        scope.isBillPayment(tx, billPaymentTxIds, parsedRecordsById, rawSmsById) ->
                            billPayments += amount

                        scope.isCashWithdrawal(tx, parsedRecordsById, rawSmsById) ->
                            cashWithdrawals += amount

                        else -> fees += amount
                    }
                }

                FinancialTransactionType.SELF_TRANSFER -> {
                    if (scope.involvesOwnedDestination(tx, parsedRecordsById, rawSmsById)) {
                        selfTransfersIn += amount
                    }
                    if (scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) {
                        selfTransfersOut += amount
                    }
                }

                FinancialTransactionType.REFUND,
                FinancialTransactionType.ADJUSTMENT,
                FinancialTransactionType.UNKNOWN,
                -> Unit
            }
        }

        return CurrentAccountSummary.of(
            currency = primaryCurrency,
            salary = salary,
            otherIncome = otherIncome,
            externalTransfersIn = externalTransfersIn,
            selfTransfersIn = selfTransfersIn,
            selfTransfersOut = selfTransfersOut,
            creditCardPayments = creditCardPayments,
            billPayments = billPayments,
            externalTransfersOut = externalTransfersOut,
            cashWithdrawals = cashWithdrawals,
            posPurchases = posPurchases,
            fees = fees,
        )
    }

    fun spendingSplit(
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
        primaryCurrency: Currency = Currency.SAR,
        sarEquivalents: Map<String, Money> = emptyMap(),
        ownedAccountContainerIds: Set<String> = emptySet(),
        ownedAccountLast4s: Set<String> = emptySet(),
        rawSmsById: Map<String, RawSms> = emptyMap(),
    ): SpendingSplitSummary {
        val currentAccount = summarize(
            transactions = transactions,
            parsedRecords = parsedRecords,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
            ownedAccountContainerIds = ownedAccountContainerIds,
            ownedAccountLast4s = ownedAccountLast4s,
            rawSmsById = rawSmsById,
        )

        var cardGross = Money.zero(primaryCurrency)
        var cardRefunds = Money.zero(primaryCurrency)

        for (tx in transactions) {
            val amount = effectiveAmount(tx, primaryCurrency, sarEquivalents) ?: continue
            when (tx.type) {
                FinancialTransactionType.EXPENSE,
                FinancialTransactionType.BILL_PAYMENT,
                FinancialTransactionType.FEE,
                -> {
                    if (isCreditCardContainer(tx.sourceContainerId)) {
                        cardGross += amount
                    }
                }

                FinancialTransactionType.REFUND -> {
                    if (isCreditCardContainer(tx.destinationContainerId)) {
                        cardRefunds += amount
                    }
                }

                else -> Unit
            }
        }

        return SpendingSplitSummary(
            currency = primaryCurrency,
            totalSpending = currentAccount.outflow.coreTotal,
            creditCardPurchases = SignedMoneyAmount.difference(cardGross, cardRefunds),
        )
    }

    private fun resolveBillPaymentTransactionIds(
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
    ): Set<String> {
        val familyByEventId = parsedRecords.associate { it.event.id to it.event.messageFamily }
        return transactions.mapNotNull { tx ->
            val families = tx.linkedParsedEventIds.mapNotNull { familyByEventId[it] }
            if (families.any { it == MessageFamily.BILL_PAYMENT }) tx.id else null
        }.toSet()
    }

    private fun isCreditCardContainer(containerId: String?): Boolean =
        containerId?.startsWith("card:") == true

    private fun effectiveAmount(
        tx: FinancialTransaction,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
    ): Money? {
        if (tx.amount.currency == primaryCurrency) return tx.amount
        return sarEquivalents[tx.id]
    }
}
