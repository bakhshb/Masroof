package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.parsing.repository.ParsedEventRecord

object CurrentAccountSummaryCalculator {
    fun summarize(
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
        primaryCurrency: Currency = Currency.SAR,
        sarEquivalents: Map<String, Money> = emptyMap(),
        ownedAccountContainerIds: Set<String> = emptySet(),
    ): CurrentAccountSummary {
        val billPaymentTxIds = resolveBillPaymentTransactionIds(transactions, parsedRecords)

        var income = Money.zero(primaryCurrency)
        var externalTransfersIn = Money.zero(primaryCurrency)
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
                    if (!involvesOwnedAccount(tx.sourceContainerId, ownedAccountContainerIds)) continue
                    income += amount
                }

                FinancialTransactionType.EXTERNAL_TRANSFER_IN -> {
                    if (!involvesOwnedAccount(tx.destinationContainerId, ownedAccountContainerIds)) continue
                    externalTransfersIn += amount
                }

                FinancialTransactionType.CREDIT_CARD_PAYMENT -> {
                    if (!involvesOwnedAccount(tx.sourceContainerId, ownedAccountContainerIds)) continue
                    creditCardPayments += amount
                }

                FinancialTransactionType.EXTERNAL_TRANSFER_OUT -> {
                    if (!involvesOwnedAccount(tx.sourceContainerId, ownedAccountContainerIds)) continue
                    externalTransfersOut += amount
                }

                FinancialTransactionType.CASH_WITHDRAWAL -> {
                    if (!involvesOwnedAccount(tx.sourceContainerId, ownedAccountContainerIds)) continue
                    cashWithdrawals += amount
                }

                FinancialTransactionType.EXPENSE -> {
                    if (isCreditCardContainer(tx.sourceContainerId)) continue
                    if (!involvesOwnedAccount(tx.sourceContainerId, ownedAccountContainerIds)) continue
                    if (tx.id in billPaymentTxIds) {
                        billPayments += amount
                    } else {
                        posPurchases += amount
                    }
                }

                FinancialTransactionType.FEE -> {
                    if (isCreditCardContainer(tx.sourceContainerId)) continue
                    if (!involvesOwnedAccount(tx.sourceContainerId, ownedAccountContainerIds)) continue
                    fees += amount
                }

                FinancialTransactionType.REFUND,
                FinancialTransactionType.SELF_TRANSFER,
                FinancialTransactionType.ADJUSTMENT,
                FinancialTransactionType.UNKNOWN,
                -> Unit
            }
        }

        return CurrentAccountSummary(
            currency = primaryCurrency,
            income = income,
            externalTransfersIn = externalTransfersIn,
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
    ): SpendingSplitSummary {
        var accountGross = Money.zero(primaryCurrency)
        var cardGross = Money.zero(primaryCurrency)
        var cardRefunds = Money.zero(primaryCurrency)

        for (tx in transactions) {
            val amount = effectiveAmount(tx, primaryCurrency, sarEquivalents) ?: continue
            when (tx.type) {
                FinancialTransactionType.EXPENSE,
                FinancialTransactionType.FEE,
                -> {
                    if (isCreditCardContainer(tx.sourceContainerId)) {
                        cardGross += amount
                    } else {
                        if (!involvesOwnedAccount(tx.sourceContainerId, ownedAccountContainerIds)) continue
                        accountGross += amount
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
            fromCurrentAccount = accountGross,
            onCreditCard = SignedMoneyAmount.difference(cardGross, cardRefunds),
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

    private fun involvesOwnedAccount(
        containerId: String?,
        ownedAccountContainerIds: Set<String>,
    ): Boolean {
        if (ownedAccountContainerIds.isEmpty()) return true
        return containerId != null && containerId in ownedAccountContainerIds
    }

    private fun effectiveAmount(
        tx: FinancialTransaction,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
    ): Money? {
        if (tx.amount.currency == primaryCurrency) return tx.amount
        return sarEquivalents[tx.id]
    }
}
