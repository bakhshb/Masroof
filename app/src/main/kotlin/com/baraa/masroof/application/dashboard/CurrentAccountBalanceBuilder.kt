package com.baraa.masroof.application.dashboard

import com.baraa.masroof.bank.aljazira.CreditCardMessageHeuristics
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.ids.FinancialContainerIdParser
import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

data class CurrentAccountBalanceSnapshot(
    val accountId: String,
    val last4: String,
    val amount: Money,
    val updatedAt: Instant,
)

/**
 * Remaining cash in an owned current account, from the latest non-credit-card
 * SMS `availableBalance`, rolled forward with later account transactions.
 *
 * Period inflow/outflow is not a balance and must not be shown as one.
 */
object CurrentAccountBalanceBuilder {
    fun latestSnapshots(
        ownedAccounts: List<AccountRegistryEntry>,
        parsedRecords: List<ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
    ): Map<String, CurrentAccountBalanceSnapshot> {
        val ownedLast4s = ownedAccounts.map { it.maskedNumber.trim().takeLast(4) }.toSet()
        val ownedIds = ownedAccounts.mapNotNull {
            FinancialContainerIdFactory.accountId(it.bank, it.maskedNumber)
        }.toSet()
        val best = mutableMapOf<String, CurrentAccountBalanceSnapshot>()

        for (record in parsedRecords) {
            val amount = record.details.availableBalance ?: continue
            val raw = rawSmsById[record.event.rawSmsId] ?: continue
            if (CreditCardMessageHeuristics.isCreditCardSms(raw.body)) continue

            val at = record.event.occurredAt ?: raw.receivedAt
            val accountId = resolveCurrentAccountId(record) ?: continue
            val last4 = FinancialContainerIdParser.accountMaskedNumber(accountId) ?: continue
            if (accountId !in ownedIds && last4 !in ownedLast4s) continue

            val key = ownedAccounts.firstOrNull { account ->
                FinancialContainerIdFactory.accountId(account.bank, account.maskedNumber) == accountId ||
                    account.maskedNumber.trim().takeLast(4) == last4
            }?.let { FinancialContainerIdFactory.accountId(it.bank, it.maskedNumber) } ?: accountId

            val existing = best[key]
            if (existing == null || at.isAfter(existing.updatedAt)) {
                best[key] = CurrentAccountBalanceSnapshot(
                    accountId = key,
                    last4 = last4,
                    amount = amount,
                    updatedAt = at,
                )
            }
        }
        return best
    }

    fun remainingByAccount(
        snapshots: Map<String, CurrentAccountBalanceSnapshot>,
        transactions: List<FinancialTransaction>,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money> = emptyMap(),
    ): Map<String, SignedMoneyAmount> {
        val remaining = snapshots.mapValues { (_, snapshot) ->
            SignedMoneyAmount.of(snapshot.amount)
        }.toMutableMap()

        val ordered = transactions.sortedBy { it.occurredAt }
        for (tx in ordered) {
            if (tx.sourceContainerId?.startsWith("card:") == true) continue
            val amount = effectiveAmount(tx, primaryCurrency, sarEquivalents) ?: continue
            for ((accountId, snapshot) in snapshots) {
                if (!tx.occurredAt.isAfter(snapshot.updatedAt)) continue
                val delta = deltaForAccount(tx, accountId, snapshot.last4, amount.amount) ?: continue
                val current = remaining[accountId] ?: continue
                remaining[accountId] = SignedMoneyAmount(
                    current.amount.add(delta).setScale(Money.SCALE, RoundingMode.HALF_EVEN),
                    current.currency,
                )
            }
        }
        return remaining
    }

    private fun resolveCurrentAccountId(record: ParsedEventRecord): String? {
        val event = record.event
        return event.sourceAccountRef?.let(FinancialContainerIdFactory::accountId)
            ?: event.destinationAccountRef?.let(FinancialContainerIdFactory::accountId)
    }

    private fun deltaForAccount(
        tx: FinancialTransaction,
        accountId: String,
        last4: String,
        amount: BigDecimal,
    ): BigDecimal? {
        val isSource = matchesAccount(tx.sourceContainerId, accountId, last4)
        val isDest = matchesAccount(tx.destinationContainerId, accountId, last4)
        if (!isSource && !isDest) return null

        return when (tx.type) {
            FinancialTransactionType.INCOME,
            FinancialTransactionType.EXTERNAL_TRANSFER_IN,
            FinancialTransactionType.REFUND,
            -> if (isDest) amount else null

            FinancialTransactionType.EXPENSE,
            FinancialTransactionType.FEE,
            FinancialTransactionType.BILL_PAYMENT,
            FinancialTransactionType.CREDIT_CARD_PAYMENT,
            FinancialTransactionType.CASH_WITHDRAWAL,
            FinancialTransactionType.EXTERNAL_TRANSFER_OUT,
            -> if (isSource) amount.negate() else null

            FinancialTransactionType.SELF_TRANSFER -> {
                var delta = BigDecimal.ZERO
                if (isSource) delta = delta.subtract(amount)
                if (isDest) delta = delta.add(amount)
                delta.takeIf { it.signum() != 0 }
            }

            FinancialTransactionType.ADJUSTMENT,
            FinancialTransactionType.UNKNOWN,
            -> null
        }
    }

    private fun matchesAccount(containerId: String?, accountId: String, last4: String): Boolean {
        if (containerId.isNullOrBlank() || !containerId.startsWith("account:")) return false
        if (containerId == accountId) return true
        return FinancialContainerIdParser.accountMaskedNumber(containerId) == last4
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
