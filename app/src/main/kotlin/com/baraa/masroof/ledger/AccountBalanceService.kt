package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.JournalWithPostings
import com.baraa.masroof.data.db.OpeningBalanceKind
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure balance calculator. Opening balances are the start-of-day position on
 * openingBalanceDate. A journal with the same effective date is applied after
 * it. Missing effective time is normalized by persistence/generation to noon
 * local time; date-only queries use LocalTime.MAX (end of that local day).
 */
object AccountBalanceService {
    fun balance(
        account: FinancialAccount,
        journals: List<JournalWithPostings>,
        asOfDate: LocalDate,
        asOfTime: LocalTime = LocalTime.MAX,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): BigDecimal {
        val openingDate = account.openingBalanceDate.toLocalDate(zoneId)
        var value = effectiveOpening(account)
        journals.asSequence()
            .filter { it.journal.postingStatus == JournalPostingStatus.POSTED }
            .filter { it.journal.effectiveDate >= openingDate }
            .filter {
                it.journal.effectiveDate < asOfDate ||
                    (it.journal.effectiveDate == asOfDate && it.journal.effectiveTime <= asOfTime)
            }
            .flatMap { it.postings.asSequence() }
            .filter { it.accountId == account.id && it.currency == account.currency }
            .forEach { posting ->
                val increase = when (account.accountNature) {
                    AccountNature.ASSET -> posting.postingSide == PostingSide.DEBIT
                    AccountNature.LIABILITY -> posting.postingSide == PostingSide.CREDIT
                }
                value = if (increase) value.add(posting.amount) else value.subtract(posting.amount)
            }
        return value
    }

    fun balances(
        accounts: List<FinancialAccount>,
        journals: List<JournalWithPostings>,
        asOfDate: LocalDate,
        asOfTime: LocalTime = LocalTime.MAX,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Map<Long, BigDecimal> = accounts.filter { it.isActive && it.systemAccountKey == null }.associate { account ->
        account.id to balance(account, journals, asOfDate, asOfTime, zoneId)
    }

    fun totals(
        accounts: List<FinancialAccount>,
        journals: List<JournalWithPostings>,
        asOfDate: LocalDate,
        asOfTime: LocalTime = LocalTime.MAX,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): BalanceTotals {
        val active = accounts.filter { it.isActive && it.systemAccountKey == null }
        val values = balances(active, journals, asOfDate, asOfTime, zoneId)
        val currencies = active.map { it.currency }.toSet()
        val assets = currencies.associateWith { currency ->
            active.filter { it.currency == currency && it.accountNature == AccountNature.ASSET && it.includeInNetWorth }
                .fold(BigDecimal.ZERO) { total, account -> total.add(values.getValue(account.id)) }
        }
        val liabilities = currencies.associateWith { currency ->
            active.filter { it.currency == currency && it.accountNature == AccountNature.LIABILITY && it.includeInNetWorth }
                .fold(BigDecimal.ZERO) { total, account -> total.add(values.getValue(account.id)) }
        }
        val liquidity = currencies.associateWith { currency ->
            active.filter { it.currency == currency && it.accountNature == AccountNature.ASSET && it.includeInLiquidity }
                .fold(BigDecimal.ZERO) { total, account -> total.add(values.getValue(account.id)) }
        }
        return BalanceTotals(
            assets = assets,
            liabilities = liabilities,
            liquidity = liquidity,
            netWorth = currencies.associateWith { assets.getValue(it).subtract(liabilities.getValue(it)) },
            consolidatedNetWorthIncomplete = currencies.any { it != Currency.SAR },
        )
    }

    private fun Long.toLocalDate(zoneId: ZoneId): LocalDate =
        if (this <= 0L) LocalDate.MIN else Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

    /** Matches [AccountBalanceCalculator] opening semantics for credit cards. */
    private fun effectiveOpening(account: FinancialAccount): BigDecimal {
        if (account.accountType != AccountType.CREDIT_CARD) return account.openingBalance
        return when (account.openingBalanceKind) {
            OpeningBalanceKind.OUTSTANDING -> account.openingBalance
            OpeningBalanceKind.AVAILABLE -> {
                val limit = account.creditLimit ?: BigDecimal.ZERO
                if (limit.signum() <= 0) account.openingBalance
                else limit.subtract(account.openingBalance).coerceAtLeast(BigDecimal.ZERO)
            }
        }
    }
}

data class BalanceTotals(
    val assets: Map<Currency, BigDecimal>,
    val liabilities: Map<Currency, BigDecimal>,
    val liquidity: Map<Currency, BigDecimal>,
    val netWorth: Map<Currency, BigDecimal>,
    val consolidatedNetWorthIncomplete: Boolean,
)
