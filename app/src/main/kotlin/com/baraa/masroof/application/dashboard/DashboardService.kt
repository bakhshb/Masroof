package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import com.baraa.masroof.domain.repository.FinancialTransactionRepository
import com.baraa.masroof.domain.repository.RawSmsRepository
import com.baraa.masroof.domain.repository.ReviewRepository
import com.baraa.masroof.parsing.repository.ParsedEventRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

data class DashboardOverview(
    val period: FinancialPeriod,
    val summary: MonthlyFinancialSummary,
    /** All transactions in the selected period, newest first. */
    val transactions: List<FinancialTransaction>,
    val creditCards: CreditCardsOverview,
    val isCurrentPeriod: Boolean,
)

/**
 * Application service that loads period transactions and builds a dashboard projection.
 */
class DashboardService(
    private val financialTransactionRepository: FinancialTransactionRepository,
    private val reviewRepository: ReviewRepository,
    private val parsedEventRepository: ParsedEventRepository,
    private val rawSmsRepository: RawSmsRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val primaryCurrency: Currency = Currency.SAR,
) : DashboardOverviewLoader {
    override suspend fun loadOverview(period: FinancialPeriod): DashboardOverview {
        val startInclusive = FinancialPeriodPolicy.toInclusiveStartInstant(period.startDate, zoneId)
        val endExclusive = FinancialPeriodPolicy.toExclusiveEndInstant(period.endDateExclusive, zoneId)
        val transactions = financialTransactionRepository.listOccurredBetween(
            startInclusive = startInclusive,
            endExclusive = endExclusive,
        )
        val reviewRequiredCount = reviewRepository.listRequired().size
        val summary = MonthlyFinancialSummaryCalculator.summarize(
            period = period,
            transactions = transactions,
            reviewRequiredCount = reviewRequiredCount,
            primaryCurrency = primaryCurrency,
        )
        val parsedRecords = parsedEventRepository.listAll()
        val rawSmsById = parsedRecords
            .map { it.event.rawSmsId }
            .distinct()
            .mapNotNull { id -> rawSmsRepository.getById(id)?.let { id to it } }
            .toMap()
        val creditCards = CreditCardOverviewBuilder.build(
            periodTransactions = transactions,
            parsedRecords = parsedRecords,
            rawSmsById = rawSmsById,
            primaryCurrency = primaryCurrency,
        )
        val current = FinancialPeriodPolicy.periodContaining(LocalDate.now(clock))
        return DashboardOverview(
            period = period,
            summary = summary,
            transactions = transactions,
            creditCards = creditCards,
            isCurrentPeriod = period == current,
        )
    }

    suspend fun loadCurrentOverview(): DashboardOverview =
        loadOverview(FinancialPeriodPolicy.periodContaining(LocalDate.now(clock)))
}
