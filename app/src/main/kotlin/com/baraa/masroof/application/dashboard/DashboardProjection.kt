package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.period.FinancialPeriod

data class DashboardMeta(
    val transactionCount: Int,
    val reviewRequiredCount: Int,
    val excludedOtherCurrencyCount: Int,
)

/**
 * Single dashboard read model — UI should render fields from here only.
 */
data class DashboardProjection(
    val period: FinancialPeriod,
    val isCurrentPeriod: Boolean,
    val summary: MonthlyFinancialSummary,
    val fleet: CurrentAccountSummary,
    val spendingSplit: SpendingSplitSummary,
    val accountsFleet: AccountsSummary,
    val perAccount: List<OwnedAccountPeriodSummary>,
    val creditFacilities: CreditFacilitiesOverview,
    val loansOverview: LoansOverview,
    val bankHierarchy: BankHierarchyOverview,
    val flowDetail: CurrentAccountFlowDetailGrouping,
    val transactionAccountInvolvement: Map<String, Set<String>> = emptyMap(),
    val transactionCardInvolvement: Map<String, Set<String>> = emptyMap(),
    val transactionLoanInvolvement: Map<String, Set<String>> = emptyMap(),
    val transactionDebitSpendInvolvement: Map<String, Set<String>> = emptyMap(),
    val transactions: List<FinancialTransaction>,
    val meta: DashboardMeta,
    val accountRegistry: List<AccountRegistryEntry>,
    val cardRegistry: List<CardRegistryEntry>,
) {
    fun toOverview(): DashboardOverview =
        DashboardOverview(
            period = period,
            summary = summary,
            currentAccount = fleet,
            spendingSplit = spendingSplit,
            transactions = transactions,
            creditFacilities = creditFacilities,
            loansOverview = loansOverview,
            bankHierarchy = bankHierarchy,
            accountsFleet = accountsFleet,
            ownedAccountPeriodSummaries = perAccount,
            flowDetailGrouping = flowDetail,
            transactionAccountInvolvement = transactionAccountInvolvement,
            transactionCardInvolvement = transactionCardInvolvement,
            transactionLoanInvolvement = transactionLoanInvolvement,
            transactionDebitSpendInvolvement = transactionDebitSpendInvolvement,
            isCurrentPeriod = isCurrentPeriod,
        )
}
