package com.baraa.masroof.application.dashboard

import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Attributes POS purchases and cash withdrawals to owned debit (Mada) cards via parsed card refs.
 */
object DebitCardOverviewBuilder {
    fun buildSpendingByCardKey(
        salaryPeriod: FinancialPeriod,
        debitCards: List<CardRegistryEntry>,
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
        ownedAccountContainerIds: Set<String>,
        ownedAccountLast4s: Set<String>,
        zoneId: ZoneId,
        displayLocale: Locale = Locale.forLanguageTag(AppLocale.TAG_AR),
    ): Pair<Map<String, SignedMoneyAmount>, String?> {
        val ownedDebit = debitCards.filter {
            it.cardType == CardType.DEBIT && it.ownership == OwnershipStatus.OWNED
        }
        if (ownedDebit.isEmpty()) return emptyMap<String, SignedMoneyAmount>() to null

        val salaryPeriodStart = FinancialPeriodPolicy.toInclusiveStartInstant(salaryPeriod.startDate, zoneId)
        val salaryPeriodLabel = DateTimeFormatter.ofPattern("d MMMM", displayLocale)
            .format(salaryPeriod.startDate)
        val cardInvolvement = CardTransactionInvolvementResolver.buildIndex(transactions, parsedRecords)
        val context = AccountFlowClassifier.buildContext(
            transactions = transactions,
            parsedRecords = parsedRecords,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
            rawSmsById = rawSmsById,
        )

        val spending = ownedDebit.associate { entry ->
            val cardKey = CardTransactionInvolvementResolver.cardKey(entry.bank.id, entry.last4)
            val linkedContainerId = entry.linkedAccount?.let { account ->
                FinancialContainerIdFactory.accountId(account)
            }
            val linkedLast4s = entry.linkedAccountMaskedNumber?.let { masked ->
                CurrentAccountTransactionScope.ownedAccountLast4sFromMaskedNumbers(listOf(masked))
            }.orEmpty()
            val scope = when {
                linkedContainerId != null ->
                    CurrentAccountTransactionScope(
                        ownedContainerIds = setOf(linkedContainerId),
                        ownedAccountLast4s = linkedLast4s,
                        mode = AccountFlowScopeMode.SingleAccount,
                    )
                else ->
                    CurrentAccountTransactionScope(
                        ownedContainerIds = ownedAccountContainerIds,
                        ownedAccountLast4s = ownedAccountLast4s,
                        mode = AccountFlowScopeMode.Fleet,
                    )
            }

            var total = Money.zero(primaryCurrency)
            for (tx in transactions) {
                if (tx.occurredAt.isBefore(salaryPeriodStart)) continue
                if (!CardTransactionInvolvementResolver.matchesCard(tx.id, entry.bank.id, entry.last4, cardInvolvement)) {
                    continue
                }
                val amount = TransactionAmountResolver.effectiveAmount(tx, primaryCurrency, sarEquivalents)
                    ?: continue
                val isDebitSpend = AccountFlowClassifier.classify(tx, scope, context).any { assignment ->
                    assignment is FlowAssignment.Expense &&
                        (
                            assignment.category == FlowExpenseCategory.POS_PURCHASE ||
                                assignment.category == FlowExpenseCategory.CASH_WITHDRAWAL
                            )
                }
                if (isDebitSpend) {
                    total += amount
                }
            }
            cardKey to SignedMoneyAmount.of(total)
        }
        return spending to salaryPeriodLabel
    }
}
