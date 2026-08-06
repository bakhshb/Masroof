package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.JournalWithPostings
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class HistoricalTrackingStatus { TRACKED, NOT_STARTED, INCOMPLETE, UNSUPPORTED_CURRENCY }
enum class HistoricalCompletenessStatus { COMPLETE, PARTIAL, NO_OPENING_BALANCE, UNPOSTED_TRANSACTIONS, FOREIGN_CURRENCY_EXCLUDED, BEFORE_TRACKING_START }

data class HistoricalAccountBalance(
    val accountId: Long, val accountDisplayLabel: String, val accountType: AccountType,
    val accountNature: AccountNature, val currency: Currency, val openingBalanceDate: LocalDate?,
    val startOfDayBalance: BigDecimal, val totalDebitsForDay: BigDecimal,
    val totalCreditsForDay: BigDecimal, val netMovementForDay: BigDecimal,
    val endOfDayBalance: BigDecimal, val includedInLiquidity: Boolean,
    val includedInNetWorth: Boolean, val trackingStatus: HistoricalTrackingStatus, val isActive: Boolean,
)

data class DailyFinancialMovement(
    val income: BigDecimal = BigDecimal.ZERO, val expenses: BigDecimal = BigDecimal.ZERO,
    val refunds: BigDecimal = BigDecimal.ZERO, val bankFees: BigDecimal = BigDecimal.ZERO,
    val investments: BigDecimal = BigDecimal.ZERO, val internalTransfers: BigDecimal = BigDecimal.ZERO,
    val creditCardPayments: BigDecimal = BigDecimal.ZERO, val cashWithdrawals: BigDecimal = BigDecimal.ZERO,
    val manualAdjustments: BigDecimal = BigDecimal.ZERO, val reversals: BigDecimal = BigDecimal.ZERO,
    val netCashMovement: BigDecimal = BigDecimal.ZERO,
) { val netExpenses: BigDecimal get() = expenses.add(bankFees).subtract(refunds) }

data class HistoricalDataCompleteness(val statuses: Set<HistoricalCompletenessStatus>) {
    val isComplete get() = statuses == setOf(HistoricalCompletenessStatus.COMPLETE)
}
data class HistoricalFinancialSummary(
    val selectedDate: LocalDate, val defaultCurrency: Currency, val startOfDayAssets: BigDecimal,
    val endOfDayAssets: BigDecimal, val startOfDayLiabilities: BigDecimal, val endOfDayLiabilities: BigDecimal,
    val startOfDayLiquidity: BigDecimal, val endOfDayLiquidity: BigDecimal, val startOfDayNetWorth: BigDecimal,
    val endOfDayNetWorth: BigDecimal, val movement: DailyFinancialMovement, val postedJournalCount: Int,
    val unpostedTransactionCount: Int, val completeness: HistoricalDataCompleteness, val accounts: List<HistoricalAccountBalance>,
)
data class MonthlyFinancialHistory(val month: YearMonth, val daily: Map<LocalDate, HistoricalFinancialSummary>) {
    /** Sum of daily movement buckets for the month (not the last day's movement). */
    fun monthMovement(): DailyFinancialMovement {
        var income = BigDecimal.ZERO
        var expenses = BigDecimal.ZERO
        var refunds = BigDecimal.ZERO
        var bankFees = BigDecimal.ZERO
        var investments = BigDecimal.ZERO
        var internalTransfers = BigDecimal.ZERO
        var creditCardPayments = BigDecimal.ZERO
        var cashWithdrawals = BigDecimal.ZERO
        var manualAdjustments = BigDecimal.ZERO
        var reversals = BigDecimal.ZERO
        daily.values.forEach { day ->
            val m = day.movement
            income += m.income
            expenses += m.expenses
            refunds += m.refunds
            bankFees += m.bankFees
            investments += m.investments
            internalTransfers += m.internalTransfers
            creditCardPayments += m.creditCardPayments
            cashWithdrawals += m.cashWithdrawals
            manualAdjustments += m.manualAdjustments
            reversals += m.reversals
        }
        val first = daily.values.firstOrNull()
        val last = daily.values.lastOrNull()
        val netCash = (last?.endOfDayLiquidity ?: BigDecimal.ZERO) - (first?.startOfDayLiquidity ?: BigDecimal.ZERO)
        return DailyFinancialMovement(
            income = income, expenses = expenses, refunds = refunds, bankFees = bankFees,
            investments = investments, internalTransfers = internalTransfers,
            creditCardPayments = creditCardPayments, cashWithdrawals = cashWithdrawals,
            manualAdjustments = manualAdjustments, reversals = reversals, netCashMovement = netCash,
        )
    }
}

/** Pure, in-memory historical calculation. Only opening balances and POSTED journals are inputs. */
object HistoricalFinancialService {
    fun calculateMonth(
        month: YearMonth, accounts: List<FinancialAccount>, journals: List<JournalWithPostings>,
        unpostedByDate: Map<LocalDate, Int> = emptyMap(), zoneId: ZoneId = ZoneId.systemDefault(),
    ): MonthlyFinancialHistory {
        val userAccounts = accounts.filter { it.systemAccountKey == null && it.isOwnedByUser }
        val throughMonth = journals.filter { it.journal.postingStatus == JournalPostingStatus.POSTED && !it.journal.effectiveDate.isAfter(month.atEndOfMonth()) }
        return MonthlyFinancialHistory(month, (1..month.lengthOfMonth()).associate { day ->
            val date = month.atDay(day)
            date to calculateDay(date, userAccounts, throughMonth, unpostedByDate[date] ?: 0, zoneId)
        })
    }

    fun calculateDay(date: LocalDate, accounts: List<FinancialAccount>, journals: List<JournalWithPostings>, unposted: Int = 0, zoneId: ZoneId = ZoneId.systemDefault()): HistoricalFinancialSummary {
        val journalToday = journals.filter { it.journal.postingStatus == JournalPostingStatus.POSTED && it.journal.effectiveDate == date }
        val rows = accounts.filter { it.systemAccountKey == null && it.isOwnedByUser }.map { account ->
            val opening = account.openingBalanceDate.takeIf { it > 0 }?.let { java.time.Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
            val status = when { opening == null -> HistoricalTrackingStatus.INCOMPLETE; date < opening -> HistoricalTrackingStatus.NOT_STARTED; account.currency != Currency.SAR -> HistoricalTrackingStatus.UNSUPPORTED_CURRENCY; else -> HistoricalTrackingStatus.TRACKED }
            val prior = if (status == HistoricalTrackingStatus.NOT_STARTED || opening == null) BigDecimal.ZERO else AccountBalanceService.balance(account, journals, date.minusDays(1), java.time.LocalTime.MAX, zoneId)
            val postings = journalToday.flatMap { it.postings }.filter { it.accountId == account.id && it.currency == account.currency }
            val debits = postings.filter { it.postingSide == PostingSide.DEBIT }.fold(BigDecimal.ZERO) { a, p -> a + p.amount }
            val credits = postings.filter { it.postingSide == PostingSide.CREDIT }.fold(BigDecimal.ZERO) { a, p -> a + p.amount }
            val movement = if (account.accountNature == AccountNature.ASSET) debits - credits else credits - debits
            HistoricalAccountBalance(account.id, account.displayName, account.accountType, account.accountNature, account.currency, opening, prior, debits, credits, movement, prior + movement, account.includeInLiquidity, account.includeInNetWorth, status, account.isActive)
        }
        fun total(nature: AccountNature, end: Boolean, predicate: (HistoricalAccountBalance) -> Boolean) = rows.filter { it.trackingStatus == HistoricalTrackingStatus.TRACKED && it.accountNature == nature && predicate(it) }.fold(BigDecimal.ZERO) { a, r -> a + if (end) r.endOfDayBalance else r.startOfDayBalance }
        val sa = total(AccountNature.ASSET, false) { it.includedInNetWorth }; val ea = total(AccountNature.ASSET, true) { it.includedInNetWorth }
        val sl = total(AccountNature.LIABILITY, false) { it.includedInNetWorth }; val el = total(AccountNature.LIABILITY, true) { it.includedInNetWorth }
        val startLiquidity = total(AccountNature.ASSET, false) { it.includedInLiquidity }; val endLiquidity = total(AccountNature.ASSET, true) { it.includedInLiquidity }
        var movement = DailyFinancialMovement(netCashMovement = endLiquidity - startLiquidity)
        journalToday.forEach { j ->
            val amount = j.postings.firstOrNull { p -> rows.any { it.accountId == p.accountId } }?.amount ?: BigDecimal.ZERO
            movement = when (j.journal.journalType) {
                JournalType.INCOME -> movement.copy(income = movement.income + amount); JournalType.EXPENSE -> movement.copy(expenses = movement.expenses + amount)
                JournalType.REFUND -> movement.copy(refunds = movement.refunds + amount); JournalType.BANK_FEE -> movement.copy(bankFees = movement.bankFees + amount)
                JournalType.INVESTMENT_TRANSFER -> movement.copy(investments = movement.investments + amount); JournalType.INTERNAL_TRANSFER -> movement.copy(internalTransfers = movement.internalTransfers + amount)
                JournalType.CREDIT_CARD_PAYMENT -> movement.copy(creditCardPayments = movement.creditCardPayments + amount); JournalType.CASH_WITHDRAWAL -> movement.copy(cashWithdrawals = movement.cashWithdrawals + amount)
                JournalType.MANUAL_ADJUSTMENT -> movement.copy(manualAdjustments = movement.manualAdjustments + amount); JournalType.REVERSAL -> movement.copy(reversals = movement.reversals + amount); else -> movement
            }
        }
        val flags = mutableSetOf<HistoricalCompletenessStatus>(); if (rows.any { it.trackingStatus == HistoricalTrackingStatus.INCOMPLETE }) flags += HistoricalCompletenessStatus.NO_OPENING_BALANCE
        if (rows.any { it.trackingStatus == HistoricalTrackingStatus.NOT_STARTED }) flags += HistoricalCompletenessStatus.BEFORE_TRACKING_START
        if (rows.any { it.currency != Currency.SAR }) flags += HistoricalCompletenessStatus.FOREIGN_CURRENCY_EXCLUDED
        if (unposted > 0) flags += HistoricalCompletenessStatus.UNPOSTED_TRANSACTIONS
        if (flags.isEmpty()) flags += HistoricalCompletenessStatus.COMPLETE
        return HistoricalFinancialSummary(date, Currency.SAR, sa, ea, sl, el, startLiquidity, endLiquidity, sa-sl, ea-el, movement, journalToday.size, unposted, HistoricalDataCompleteness(flags), rows)
    }
}
