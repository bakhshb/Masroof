package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.application.locale.AppLocaleRepository
import com.baraa.masroof.domain.repository.AccountRegistryRepository
import com.baraa.masroof.domain.repository.CardRegistryRepository
import com.baraa.masroof.domain.repository.LoanRegistryRepository
import com.baraa.masroof.domain.repository.FinancialTransactionRepository
import com.baraa.masroof.domain.repository.ReviewRepository
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class DashboardProjectionBuilder(
    private val financialTransactionRepository: FinancialTransactionRepository,
    private val reviewRepository: ReviewRepository,
    private val accountRegistryRepository: AccountRegistryRepository,
    private val cardRegistryRepository: CardRegistryRepository,
    private val loanRegistryRepository: LoanRegistryRepository,
    private val appLocaleRepository: AppLocaleRepository,
    private val sarEquivalentResolver: TransactionSarEquivalentResolver,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val primaryCurrency: Currency = Currency.SAR,
) {
    suspend fun build(
        period: FinancialPeriod,
        parsedRecords: List<ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
        enrichedTransactions: List<FinancialTransaction>,
    ): DashboardProjection {
        val reviewRequiredCount = reviewRepository.listRequired().size
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
            .filter { it.bank != Bank.UNKNOWN && it.ownership == OwnershipStatus.OWNED }
        val ownedAccountContainerIds = ownedAccounts
            .mapNotNull { FinancialContainerIdFactory.accountId(it.bank, it.maskedNumber) }
            .toSet()
        val ownedAccountLast4s = CurrentAccountTransactionScope.ownedAccountLast4sFromMaskedNumbers(
            ownedAccounts.map { it.maskedNumber },
        )
        val cardRegistry = cardRegistryRepository.listAll()
        val debitCardScope = DebitCardScopeFactory.fromRegistry(
            cards = cardRegistry,
            parsedRecords = parsedRecords,
            rawSmsById = rawSmsById,
            registryAccounts = ownedAccounts,
        )

        val summary = MonthlyFinancialSummaryCalculator.summarize(
            period = period,
            transactions = dedupedTransactions,
            reviewRequiredCount = reviewRequiredCount,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
        )
        val fleet = CurrentAccountSummaryCalculator.summarize(
            transactions = dedupedTransactions,
            parsedRecords = parsedRecords,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
            ownedAccountContainerIds = ownedAccountContainerIds,
            ownedAccountLast4s = ownedAccountLast4s,
            rawSmsById = rawSmsById,
            debitCardScope = debitCardScope,
        )
        val spendingSplit = CurrentAccountSummaryCalculator.spendingSplit(
            transactions = dedupedTransactions,
            parsedRecords = parsedRecords,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
            ownedAccountContainerIds = ownedAccountContainerIds,
            ownedAccountLast4s = ownedAccountLast4s,
            rawSmsById = rawSmsById,
            debitCardScope = debitCardScope,
        )
        val perAccount = OwnedAccountPeriodSummaryCalculator.summarize(
            ownedAccounts = ownedAccounts,
            transactions = dedupedTransactions,
            parsedRecords = parsedRecords,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
            rawSmsById = rawSmsById,
            debitCardScope = debitCardScope,
        )
        val accountsFleet = AccountsSummary.fromSummaries(
            accounts = ownedAccounts.map { it.bank to it.maskedNumber },
            summaries = perAccount.map { it.summary },
        )
        val flowDetail = CurrentAccountFlowDetailGrouper.group(
            transactions = dedupedTransactions,
            parsedRecords = parsedRecords,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
            ownedAccountContainerIds = ownedAccountContainerIds,
            ownedAccountLast4s = ownedAccountLast4s,
            rawSmsById = rawSmsById,
            debitCardScope = debitCardScope,
        )
        val transactionAccountInvolvement = AccountTransactionInvolvementResolver.buildIndex(
            transactions = dedupedTransactions,
            parsedRecords = parsedRecords,
            rawSmsById = rawSmsById,
            ownedAccounts = ownedAccounts,
        )
        val transactionCardInvolvement = CardTransactionInvolvementResolver.buildIndex(
            transactions = dedupedTransactions,
            parsedRecords = parsedRecords,
            rawSmsById = rawSmsById,
        )

        val periodEndExclusive = FinancialPeriodPolicy.toExclusiveEndInstant(period.endDateExclusive, zoneId)
        val statementStart = CreditCardOverviewBuilder.resolveStatementSpendingStart(
            parsedRecords = parsedRecords,
            rawSmsById = rawSmsById,
            zoneId = zoneId,
            periodEndExclusive = periodEndExclusive,
        )
        val cardQueryStart = minOf(
            FinancialPeriodPolicy.toInclusiveStartInstant(period.startDate, zoneId),
            statementStart,
        )
        val cardTransactions = financialTransactionRepository.listOccurredBetween(
            startInclusive = cardQueryStart,
            endExclusive = periodEndExclusive,
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
        val displayLocale = AppLocale.displayLocale(appLocaleRepository.getLanguageTag())
        val creditCardsFlat = CreditCardOverviewBuilder.build(
            salaryPeriod = period,
            cardTransactions = enrichedCardTransactions,
            parsedRecords = parsedRecords,
            rawSmsById = rawSmsById,
            zoneId = zoneId,
            primaryCurrency = primaryCurrency,
            sarEquivalents = cardSarEquivalents,
            displayLocale = displayLocale,
        )
        val debitSpend = DebitCardOverviewBuilder.buildSpendingByCardKey(
            salaryPeriod = period,
            debitCards = cardRegistry,
            transactions = dedupedTransactions,
            parsedRecords = parsedRecords,
            rawSmsById = rawSmsById,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
            ownedAccountContainerIds = ownedAccountContainerIds,
            ownedAccountLast4s = ownedAccountLast4s,
            zoneId = zoneId,
            displayLocale = displayLocale,
        )
        val creditFacilities = CreditFacilityOverviewBuilder.build(
            overview = creditCardsFlat,
            registryCards = cardRegistry,
            registryAccounts = ownedAccounts,
            debitSpendingByCardKey = debitSpend.spendingByCardKey,
            debitSalaryPeriodLabel = debitSpend.salaryPeriodLabel ?: creditCardsFlat.salaryPeriodLabel,
            parsedRecords = parsedRecords,
            rawSmsById = rawSmsById,
        )
        val loansOverview = LoanOverviewBuilder.build(
            salaryPeriod = period,
            loans = loanRegistryRepository.listAll(),
            transactions = dedupedTransactions,
            parsedRecords = parsedRecords,
            rawSmsById = rawSmsById,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
            zoneId = zoneId,
            displayLocale = displayLocale,
        )
        val bankHierarchy = BankHierarchyBuilder.build(
            ownedAccounts = ownedAccounts,
            accountsFleet = accountsFleet,
            creditFacilities = creditFacilities,
            loans = loanRegistryRepository.listAll(),
        )

        val current = FinancialPeriodPolicy.periodContaining(LocalDate.now(clock))
        return DashboardProjection(
            period = period,
            isCurrentPeriod = period == current,
            summary = summary,
            fleet = fleet,
            spendingSplit = spendingSplit,
            accountsFleet = accountsFleet,
            perAccount = perAccount,
            creditFacilities = creditFacilities,
            loansOverview = loansOverview,
            bankHierarchy = bankHierarchy,
            flowDetail = flowDetail,
            transactionAccountInvolvement = transactionAccountInvolvement,
            transactionCardInvolvement = transactionCardInvolvement,
            transactionDebitSpendInvolvement = debitSpend.transactionDebitSpendInvolvement,
            transactions = dedupedTransactions,
            meta = DashboardMeta(
                transactionCount = summary.transactionCount,
                reviewRequiredCount = reviewRequiredCount,
                excludedOtherCurrencyCount = summary.excludedOtherCurrencyCount,
            ),
            accountRegistry = ownedAccounts,
            cardRegistry = cardRegistry,
        )
    }
}
