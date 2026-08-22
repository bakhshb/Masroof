package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord

enum class SelfTransferLeg {
    IN,
    OUT,
}

sealed interface FlowAssignment {
    data class Expense(val category: FlowExpenseCategory) : FlowAssignment

    data class Income(val category: FlowIncomeCategory) : FlowAssignment

    data class SelfTransfer(val leg: SelfTransferLeg) : FlowAssignment

    data object Excluded : FlowAssignment
}

data class AccountFlowClassificationContext(
    val parsedRecordsById: Map<String, ParsedEventRecord>,
    val rawSmsById: Map<String, RawSms>,
    val billPaymentTxIds: Set<String>,
    val primaryCurrency: Currency,
    val sarEquivalents: Map<String, Money>,
)

object AccountFlowClassifier {
    fun classify(
        tx: FinancialTransaction,
        scope: CurrentAccountTransactionScope,
        context: AccountFlowClassificationContext,
    ): List<FlowAssignment> {
        val amount = TransactionAmountResolver.effectiveAmount(
            tx = tx,
            primaryCurrency = context.primaryCurrency,
            sarEquivalents = context.sarEquivalents,
        ) ?: return emptyList()

        return classifyWithAmount(tx, scope, context, amount)
    }

    internal fun classifyWithAmount(
        tx: FinancialTransaction,
        scope: CurrentAccountTransactionScope,
        context: AccountFlowClassificationContext,
        amount: Money,
    ): List<FlowAssignment> {
        val parsedRecordsById = context.parsedRecordsById
        val rawSmsById = context.rawSmsById
        val billPaymentTxIds = context.billPaymentTxIds

        return when (tx.type) {
            FinancialTransactionType.INCOME -> {
                if (!scope.involvesOwnedDestination(tx, parsedRecordsById, rawSmsById)) {
                    return emptyList()
                }
                listOf(
                    if (SalaryIncomeHeuristics.isSalaryIncome(tx, parsedRecordsById, rawSmsById)) {
                        FlowAssignment.Income(FlowIncomeCategory.SALARY)
                    } else {
                        FlowAssignment.Income(FlowIncomeCategory.OTHER_INCOME)
                    },
                )
            }

            FinancialTransactionType.EXTERNAL_TRANSFER_IN -> {
                if (!scope.involvesOwnedDestination(tx, parsedRecordsById, rawSmsById)) {
                    return emptyList()
                }
                listOf(
                    if (SalaryIncomeHeuristics.isSalaryIncome(tx, parsedRecordsById, rawSmsById)) {
                        FlowAssignment.Income(FlowIncomeCategory.SALARY)
                    } else {
                        FlowAssignment.Income(FlowIncomeCategory.EXTERNAL_TRANSFER_IN)
                    },
                )
            }

            FinancialTransactionType.CREDIT_CARD_PAYMENT ->
                if (!scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) {
                    emptyList()
                } else {
                    listOf(FlowAssignment.Expense(FlowExpenseCategory.CREDIT_CARD_PAYMENT))
                }

            FinancialTransactionType.EXTERNAL_TRANSFER_OUT ->
                if (!scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) {
                    emptyList()
                } else {
                    listOf(FlowAssignment.Expense(FlowExpenseCategory.EXTERNAL_TRANSFER_OUT))
                }

            FinancialTransactionType.CASH_WITHDRAWAL ->
                if (!scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) {
                    emptyList()
                } else {
                    listOf(FlowAssignment.Expense(FlowExpenseCategory.CASH_WITHDRAWAL))
                }

            FinancialTransactionType.BILL_PAYMENT ->
                if (!scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) {
                    emptyList()
                } else {
                    listOf(FlowAssignment.Expense(FlowExpenseCategory.BILL_PAYMENT))
                }

            FinancialTransactionType.EXPENSE -> {
                if (scope.isCreditCardSourcedExpenseWithoutOwnedAccount(tx, parsedRecordsById, rawSmsById)) {
                    return emptyList()
                }
                if (!scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) {
                    return emptyList()
                }
                listOf(
                    when {
                        scope.isCreditCardPayment(tx, parsedRecordsById, rawSmsById) ->
                            FlowAssignment.Expense(FlowExpenseCategory.CREDIT_CARD_PAYMENT)

                        scope.isCashWithdrawal(tx, parsedRecordsById, rawSmsById) ->
                            FlowAssignment.Expense(FlowExpenseCategory.CASH_WITHDRAWAL)

                        scope.isBillPayment(tx, billPaymentTxIds, parsedRecordsById, rawSmsById) ->
                            FlowAssignment.Expense(FlowExpenseCategory.BILL_PAYMENT)

                        else -> FlowAssignment.Expense(FlowExpenseCategory.POS_PURCHASE)
                    },
                )
            }

            FinancialTransactionType.FEE -> {
                if (scope.isCreditCardSourcedExpenseWithoutOwnedAccount(tx, parsedRecordsById, rawSmsById)) {
                    return emptyList()
                }
                if (!scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) {
                    return emptyList()
                }
                listOf(
                    if (scope.isBillPayment(tx, billPaymentTxIds, parsedRecordsById, rawSmsById)) {
                        FlowAssignment.Expense(FlowExpenseCategory.BILL_PAYMENT)
                    } else {
                        FlowAssignment.Expense(FlowExpenseCategory.FEE)
                    },
                )
            }

            FinancialTransactionType.SELF_TRANSFER -> buildList {
                if (scope.involvesOwnedDestination(tx, parsedRecordsById, rawSmsById)) {
                    add(FlowAssignment.SelfTransfer(SelfTransferLeg.IN))
                }
                if (scope.involvesOwnedSource(tx, parsedRecordsById, rawSmsById)) {
                    add(FlowAssignment.SelfTransfer(SelfTransferLeg.OUT))
                }
            }

            FinancialTransactionType.REFUND,
            FinancialTransactionType.ADJUSTMENT,
            FinancialTransactionType.UNKNOWN,
            -> emptyList()
        }
    }

    fun resolveBillPaymentTransactionIds(
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
    ): Set<String> {
        val familyByEventId = parsedRecords.associate { it.event.id to it.event.messageFamily }
        return transactions.mapNotNull { tx ->
            val families = tx.linkedParsedEventIds.mapNotNull { familyByEventId[it] }
            if (families.any { it == MessageFamily.BILL_PAYMENT }) tx.id else null
        }.toSet()
    }

    fun buildContext(
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
        rawSmsById: Map<String, RawSms>,
    ): AccountFlowClassificationContext =
        AccountFlowClassificationContext(
            parsedRecordsById = parsedRecords.associateBy { it.event.id },
            rawSmsById = rawSmsById,
            billPaymentTxIds = resolveBillPaymentTransactionIds(transactions, parsedRecords),
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
        )
}
