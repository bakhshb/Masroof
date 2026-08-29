package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId

data class DailySpendingPoint(
    val date: LocalDate,
    val spending: SignedMoneyAmount,
)

data class DailySpendingTrend(
    val points: List<DailySpendingPoint>,
    val averageDailySpending: SignedMoneyAmount,
) {
    init {
        require(points.isNotEmpty())
        require(points.all { it.spending.currency == averageDailySpending.currency })
    }

    companion object {
        fun empty(
            period: FinancialPeriod,
            currency: Currency = Currency.SAR,
        ): DailySpendingTrend =
            DailySpendingTrendBuilder.build(
                period = period,
                transactions = emptyList(),
                parsedRecords = emptyList(),
                primaryCurrency = currency,
                sarEquivalents = emptyMap(),
                zoneId = ZoneId.systemDefault(),
                today = LocalDate.now(ZoneId.systemDefault()),
            )
    }
}

/**
 * Builds the ordinary-spending trend used in the dashboard analysis section.
 *
 * Spending matches [MonthlyFinancialSummaryCalculator]: expenses, bills, and
 * non-loan fees, less refunds. Transfers, withdrawals, repayments, and card
 * settlements are movements rather than ordinary spending.
 */
object DailySpendingTrendBuilder {
    fun build(
        period: FinancialPeriod,
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
        zoneId: ZoneId,
        today: LocalDate,
    ): DailySpendingTrend {
        val parsedRecordsById = parsedRecords.associateBy { it.event.id }
        val totalsByDay = mutableMapOf<LocalDate, BigDecimal>()

        transactions.forEach { transaction ->
            val signedAmount = transaction.signedSpendingAmount(
                primaryCurrency = primaryCurrency,
                sarEquivalents = sarEquivalents,
                parsedRecordsById = parsedRecordsById,
            ) ?: return@forEach
            val date = transaction.occurredAt.atZone(zoneId).toLocalDate()
            if (date.isBefore(period.startDate) || !date.isBefore(period.endDateExclusive)) return@forEach
            totalsByDay[date] = (totalsByDay[date] ?: BigDecimal.ZERO)
                .add(signedAmount)
                .setScale(Money.SCALE, RoundingMode.HALF_EVEN)
        }

        val lastInclusiveDate = minOf(period.displayEndDateInclusive, today)
        val points = generateSequence(period.startDate) { date ->
            date.plusDays(1).takeIf { !it.isAfter(lastInclusiveDate) }
        }.map { date ->
            DailySpendingPoint(
                date = date,
                spending = SignedMoneyAmount(
                    amount = (totalsByDay[date] ?: BigDecimal.ZERO)
                        .setScale(Money.SCALE, RoundingMode.HALF_EVEN),
                    currency = primaryCurrency,
                ),
            )
        }.toList()
        val total = points.fold(BigDecimal.ZERO) { sum, point -> sum.add(point.spending.amount) }
            .setScale(Money.SCALE, RoundingMode.HALF_EVEN)

        return DailySpendingTrend(
            points = points,
            averageDailySpending = SignedMoneyAmount(
                amount = total.divide(
                    points.size.toBigDecimal(),
                    Money.SCALE,
                    RoundingMode.HALF_EVEN,
                ),
                currency = primaryCurrency,
            ),
        )
    }

    private fun FinancialTransaction.signedSpendingAmount(
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
        parsedRecordsById: Map<String, ParsedEventRecord>,
    ): BigDecimal? {
        val amount = TransactionAmountResolver.effectiveAmount(this, primaryCurrency, sarEquivalents)
            ?: return null
        return when (type) {
            FinancialTransactionType.EXPENSE,
            FinancialTransactionType.BILL_PAYMENT,
            -> amount.amount

            FinancialTransactionType.FEE ->
                amount.amount.takeUnless { LoanRepaymentAttribution.isLoanRepayment(this, parsedRecordsById) }

            FinancialTransactionType.REFUND -> amount.amount.negate()
            else -> null
        }
    }
}
