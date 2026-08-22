package com.baraa.masroof.application.dashboard

import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.application.locale.AppLocaleRepository
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import com.baraa.masroof.domain.repository.AccountRegistryRepository
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
    val currentAccount: CurrentAccountSummary,
    val spendingSplit: SpendingSplitSummary,
    /** All transactions in the selected period, newest first. */
    val transactions: List<FinancialTransaction>,
    val creditCards: CreditCardsOverview,
    val ownedAccountPeriodSummaries: List<OwnedAccountPeriodSummary>,
    val flowDetailGrouping: CurrentAccountFlowDetailGrouping,
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
    private val appLocaleRepository: AppLocaleRepository,
    private val accountRegistryRepository: AccountRegistryRepository,
    private val sarEquivalentResolver: TransactionSarEquivalentResolver,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val primaryCurrency: Currency = Currency.SAR,
) : DashboardOverviewLoader {
    override suspend fun loadOverview(period: FinancialPeriod): DashboardOverview {
        val displayLocale = AppLocale.displayLocale(appLocaleRepository.getLanguageTag())
        val startInclusive = FinancialPeriodPolicy.toInclusiveStartInstant(period.startDate, zoneId)
        val endExclusive = FinancialPeriodPolicy.toExclusiveEndInstant(period.endDateExclusive, zoneId)
        val transactions = financialTransactionRepository.listOccurredBetween(
            startInclusive = startInclusive,
            endExclusive = endExclusive,
        )
        val reviewRequiredCount = reviewRepository.listRequired().size
        val parsedRecords = parsedEventRepository.listAll()
        val rawSmsById = parsedRecords
            .map { it.event.rawSmsId }
            .distinct()
            .mapNotNull { id -> rawSmsRepository.getById(id)?.let { id to it } }
            .toMap()
        val enrichedTransactions = TransactionDisplayEnricher.enrichMerchants(
            transactions = transactions,
            parsedRecords = parsedRecords,
        )
        val sarResolutions = sarEquivalentResolver.resolve(
            transactions = enrichedTransactions,
            parsedRecords = parsedRecords,
            rawSmsById = rawSmsById,
            primaryCurrency = primaryCurrency,
        )
        val syncedTransactions = AppliedExchangeRateSyncer.sync(
            transactions = enrichedTransactions,
            resolutions = sarResolutions,
            repository = financialTransactionRepository,
        )
        val dedupedTransactions = SelfTransferDeduplicator.filter(
            transactions = syncedTransactions,
            parsedRecords = parsedRecords,
        )
        val sarEquivalents = sarResolutions.sarAmounts()
        val ownedAccounts = accountRegistryRepository.listAll()
            .asSequence()
            .filter { it.bank != Bank.UNKNOWN }
            .filter { it.ownership == OwnershipStatus.OWNED }
            .toList()
        val ownedAccountContainerIds = ownedAccounts
            .mapNotNull { FinancialContainerIdFactory.accountId(it.bank, it.maskedNumber) }
            .toSet()
        val ownedAccountLast4s = CurrentAccountTransactionScope.ownedAccountLast4sFromMaskedNumbers(
            ownedAccounts.map { it.maskedNumber },
        )
        val summary = MonthlyFinancialSummaryCalculator.summarize(
            period = period,
            transactions = dedupedTransactions,
            reviewRequiredCount = reviewRequiredCount,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
        )
        val currentAccount = CurrentAccountSummaryCalculator.summarize(
            transactions = dedupedTransactions,
            parsedRecords = parsedRecords,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
            ownedAccountContainerIds = ownedAccountContainerIds,
            ownedAccountLast4s = ownedAccountLast4s,
            rawSmsById = rawSmsById,
        )
        val spendingSplit = CurrentAccountSummaryCalculator.spendingSplit(
            transactions = dedupedTransactions,
            parsedRecords = parsedRecords,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
            ownedAccountContainerIds = ownedAccountContainerIds,
            ownedAccountLast4s = ownedAccountLast4s,
            rawSmsById = rawSmsById,
        )
        val statementStart = CreditCardOverviewBuilder.resolveStatementSpendingStart(
            parsedRecords = parsedRecords,
            rawSmsById = rawSmsById,
            zoneId = zoneId,
            clock = clock,
        )
        val cardQueryStart = minOf(startInclusive, statementStart)
        val cardQueryEndExclusive = LocalDate.now(clock)
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
        val cardTransactions = financialTransactionRepository.listOccurredBetween(
            startInclusive = cardQueryStart,
            endExclusive = cardQueryEndExclusive,
        )
        val enrichedCardTransactions = TransactionDisplayEnricher.enrichMerchants(
            transactions = cardTransactions,
            parsedRecords = parsedRecords,
        )
        val cardSarResolutions = sarEquivalentResolver.resolve(
            transactions = enrichedCardTransactions,
            parsedRecords = parsedRecords,
            rawSmsById = rawSmsById,
            primaryCurrency = primaryCurrency,
        )
        AppliedExchangeRateSyncer.sync(
            transactions = enrichedCardTransactions,
            resolutions = cardSarResolutions,
            repository = financialTransactionRepository,
        )
        val cardSarEquivalents = cardSarResolutions.sarAmounts()
        val creditCards = CreditCardOverviewBuilder.build(
            salaryPeriod = period,
            cardTransactions = enrichedCardTransactions,
            parsedRecords = parsedRecords,
            rawSmsById = rawSmsById,
            zoneId = zoneId,
            clock = clock,
            primaryCurrency = primaryCurrency,
            sarEquivalents = cardSarEquivalents,
            displayLocale = displayLocale,
        )
        val ownedAccountPeriodSummaries = OwnedAccountPeriodSummaryCalculator.summarize(
            ownedAccounts = ownedAccounts,
            transactions = dedupedTransactions,
            parsedRecords = parsedRecords,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
            rawSmsById = rawSmsById,
        )
        val flowDetailGrouping = CurrentAccountFlowDetailGrouper.group(
            transactions = dedupedTransactions,
            parsedRecords = parsedRecords,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
            ownedAccountContainerIds = ownedAccountContainerIds,
            ownedAccountLast4s = ownedAccountLast4s,
            rawSmsById = rawSmsById,
        )
        val current = FinancialPeriodPolicy.periodContaining(LocalDate.now(clock))
        return DashboardOverview(
            period = period,
            summary = summary,
            currentAccount = currentAccount,
            spendingSplit = spendingSplit,
            transactions = dedupedTransactions,
            creditCards = creditCards,
            ownedAccountPeriodSummaries = ownedAccountPeriodSummaries,
            flowDetailGrouping = flowDetailGrouping,
            isCurrentPeriod = period == current,
        )
    }

    suspend fun loadCurrentOverview(): DashboardOverview =
        loadOverview(FinancialPeriodPolicy.periodContaining(LocalDate.now(clock)))
}
