package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord

enum class FlowExpenseCategory {
    EXTERNAL_TRANSFER_OUT,
    CREDIT_CARD_PAYMENT,
    CASH_WITHDRAWAL,
    BILL_PAYMENT,
    POS_PURCHASE,
    FEE,
}

enum class FlowIncomeCategory {
    SALARY,
    EXTERNAL_TRANSFER_IN,
    OTHER_INCOME,
}

data class CurrentAccountFlowDetailGrouping(
    val expense: Map<FlowExpenseCategory, List<FinancialTransaction>>,
    val income: Map<FlowIncomeCategory, List<FinancialTransaction>>,
    val selfTransfersOut: List<FinancialTransaction> = emptyList(),
    val selfTransfersIn: List<FinancialTransaction> = emptyList(),
) {
    companion object {
        val EXPENSE_DISPLAY_ORDER = listOf(
            FlowExpenseCategory.EXTERNAL_TRANSFER_OUT,
            FlowExpenseCategory.CREDIT_CARD_PAYMENT,
            FlowExpenseCategory.CASH_WITHDRAWAL,
            FlowExpenseCategory.BILL_PAYMENT,
            FlowExpenseCategory.POS_PURCHASE,
            FlowExpenseCategory.FEE,
        )

        val INCOME_DISPLAY_ORDER = listOf(
            FlowIncomeCategory.SALARY,
            FlowIncomeCategory.EXTERNAL_TRANSFER_IN,
            FlowIncomeCategory.OTHER_INCOME,
        )

        fun empty(): CurrentAccountFlowDetailGrouping = CurrentAccountFlowDetailGrouping(
            expense = FlowExpenseCategory.entries.associateWith { emptyList() },
            income = FlowIncomeCategory.entries.associateWith { emptyList() },
            selfTransfersOut = emptyList(),
            selfTransfersIn = emptyList(),
        )
    }
}

object CurrentAccountFlowDetailGrouper {
    fun group(
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
        primaryCurrency: Currency = Currency.SAR,
        sarEquivalents: Map<String, Money> = emptyMap(),
        ownedAccountContainerIds: Set<String> = emptySet(),
        ownedAccountLast4s: Set<String> = emptySet(),
        rawSmsById: Map<String, RawSms> = emptyMap(),
        scopeMode: AccountFlowScopeMode = AccountFlowScopeMode.Fleet,
    ): CurrentAccountFlowDetailGrouping {
        val billPaymentTxIds = resolveBillPaymentTransactionIds(transactions, parsedRecords)
        val parsedRecordsById = parsedRecords.associateBy { it.event.id }
        val scope = CurrentAccountTransactionScope(
            ownedContainerIds = ownedAccountContainerIds,
            ownedAccountLast4s = ownedAccountLast4s,
            mode = scopeMode,
        )
        val expense = FlowExpenseCategory.entries.associateWith { mutableListOf<FinancialTransaction>() }
        val income = FlowIncomeCategory.entries.associateWith { mutableListOf<FinancialTransaction>() }
        val selfTransfersOut = mutableListOf<FinancialTransaction>()
        val selfTransfersIn = mutableListOf<FinancialTransaction>()

        for (tx in transactions) {
            if (effectiveAmount(tx, primaryCurrency, sarEquivalents) == null) continue
            when (tx.type) {
                FinancialTransactionType.INCOME -> {
                    if (!scope.involvesOwnedDestination(tx, parsedRecordsById, rawSmsById)) continue
                    if (SalaryIncomeHeuristics.isSalaryIncome(tx, parsedRecordsById, rawSmsById)) {
                        income.getValue(FlowIncomeCategory.SALARY).add(tx)
                    } else {
                        income.getValue(FlowIncomeCategory.OTHER_INCOME).add(tx)
                    }
                }

                FinancialTransactionType.EXTERNAL_TRANSFER_IN -> {
                    if (!scope.involvesOwnedDestination(tx, parsedRecordsById, rawSmsById)) continue
                    if (SalaryIncomeHeuristics.isSalaryIncome(tx, parsedRecordsById, rawSmsById)) {
                        income.getValue(FlowIncomeCategory.SALARY).add(tx)
                    } else {
                        income.getValue(FlowIncomeCategory.EXTERNAL_TRANSFER_IN).add(tx)
                    }
                }

                FinancialTransactionType.CREDIT_CARD_PAYMENT -> {
                    if (!scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) continue
                    expense.getValue(FlowExpenseCategory.CREDIT_CARD_PAYMENT).add(tx)
                }

                FinancialTransactionType.EXTERNAL_TRANSFER_OUT -> {
                    if (!scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) continue
                    expense.getValue(FlowExpenseCategory.EXTERNAL_TRANSFER_OUT).add(tx)
                }

                FinancialTransactionType.CASH_WITHDRAWAL -> {
                    if (!scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) continue
                    expense.getValue(FlowExpenseCategory.CASH_WITHDRAWAL).add(tx)
                }

                FinancialTransactionType.BILL_PAYMENT -> {
                    if (!scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) continue
                    expense.getValue(FlowExpenseCategory.BILL_PAYMENT).add(tx)
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
                            expense.getValue(FlowExpenseCategory.CREDIT_CARD_PAYMENT).add(tx)

                        scope.isCashWithdrawal(tx, parsedRecordsById, rawSmsById) ->
                            expense.getValue(FlowExpenseCategory.CASH_WITHDRAWAL).add(tx)

                        scope.isBillPayment(tx, billPaymentTxIds, parsedRecordsById, rawSmsById) ->
                            expense.getValue(FlowExpenseCategory.BILL_PAYMENT).add(tx)

                        else -> expense.getValue(FlowExpenseCategory.POS_PURCHASE).add(tx)
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
                    if (scope.isBillPayment(tx, billPaymentTxIds, parsedRecordsById, rawSmsById)) {
                        expense.getValue(FlowExpenseCategory.BILL_PAYMENT).add(tx)
                    } else {
                        expense.getValue(FlowExpenseCategory.FEE).add(tx)
                    }
                }

                FinancialTransactionType.SELF_TRANSFER -> {
                    if (scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) {
                        selfTransfersOut.add(tx)
                    }
                    if (scope.involvesOwnedDestination(tx, parsedRecordsById, rawSmsById)) {
                        selfTransfersIn.add(tx)
                    }
                }

                FinancialTransactionType.REFUND,
                FinancialTransactionType.ADJUSTMENT,
                FinancialTransactionType.UNKNOWN,
                -> Unit
            }
        }

        return CurrentAccountFlowDetailGrouping(
            expense = expense.mapValues { (_, rows) -> rows.toList() },
            income = income.mapValues { (_, rows) -> rows.toList() },
            selfTransfersOut = selfTransfersOut.toList(),
            selfTransfersIn = selfTransfersIn.toList(),
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
