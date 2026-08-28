package com.baraa.masroof.presentation.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.AccountsSummary
import com.baraa.masroof.application.dashboard.CreditFacilitiesOverview
import com.baraa.masroof.application.dashboard.LoansOverview
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.application.dashboard.aggregateCreditSalaryPeriodSpending
import com.baraa.masroof.application.dashboard.aggregateCreditStatementSpending
import com.baraa.masroof.application.dashboard.aggregateDebitSalaryPeriodSpending
import com.baraa.masroof.application.dashboard.aggregateFacilityDue
import com.baraa.masroof.application.dashboard.SpendingAmounts
import com.baraa.masroof.application.dashboard.aggregateRemainingBalance
import com.baraa.masroof.application.dashboard.aggregateSalaryPeriodPayment
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.presentation.common.MasroofCardAccent
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun accountsSummaryHeroSpec(
    fleet: AccountsSummary,
): DashboardSummaryHeroSpec {
    val totalInflow = fleet.totalInflow
    val totalOutflow = fleet.totalOutflow
    val unavailableLabel = stringResource(R.string.dashboard_value_unavailable)
    val locale = accountsSummaryHeroLocale
    val languageTag = locale.toLanguageTag()
    val formulaHint = if (totalInflow != null && totalOutflow != null) {
        stringResource(
            R.string.dashboard_remaining_formula,
            formatHeroMoney(totalInflow, languageTag),
            formatHeroMoney(totalOutflow, languageTag),
        )
    } else {
        null
    }

    return buildAccountsSummaryHeroSpec(
        fleet = fleet,
        remainingTitle = stringResource(R.string.dashboard_accounts_remaining_total_title),
        inflowTitle = stringResource(R.string.dashboard_total_inflow),
        outflowTitle = stringResource(R.string.dashboard_total_spent),
        unavailableLabel = unavailableLabel,
        formulaHint = formulaHint,
        footerHint = stringResource(R.string.dashboard_accounts_fleet_total_hint),
        languageTag = languageTag,
    )
}

private val accountsSummaryHeroLocale: Locale
    @Composable get() = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]

@Composable
fun creditFacilitiesSummaryHeroSpec(
    overview: CreditFacilitiesOverview,
    locale: Locale,
): DashboardSummaryHeroSpec {
    val aggregateDue = overview.aggregateFacilityDue()
    val salaryPeriodLabel = overview.facilities.firstOrNull()?.salaryPeriodLabel
        ?: overview.debitCards.firstOrNull()?.salaryPeriodLabel
    val statementPeriodLabel = overview.facilities.firstOrNull()?.aggregateStatementPeriodLabel
    val periodSpending = when {
        overview.facilities.isNotEmpty() && overview.debitCards.isNotEmpty() ->
            SpendingAmounts.sum(
                listOf(
                    overview.aggregateCreditSalaryPeriodSpending(),
                    overview.aggregateDebitSalaryPeriodSpending(),
                ),
            )
        overview.facilities.isNotEmpty() -> overview.aggregateCreditSalaryPeriodSpending()
        else -> overview.aggregateDebitSalaryPeriodSpending()
    }
    return buildCreditSummaryHeroSpec(
        aggregateDue = aggregateDue?.amount,
        dueDateLabel = aggregateDue?.dueDate?.let { dueDate ->
            stringResource(
                R.string.dashboard_credit_card_due_date,
                DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(dueDate),
            )
        },
        periodSpending = periodSpending,
        statementSpending = overview.aggregateCreditStatementSpending(),
        dueTitle = stringResource(R.string.dashboard_credit_card_aggregate_due),
        periodSpendingTitle = salaryPeriodLabel?.let {
            stringResource(R.string.dashboard_credit_cards_aggregate_period_spending, it)
        } ?: stringResource(R.string.dashboard_credit_cards_aggregate_period_spending_fallback),
        statementSpendingTitle = statementPeriodLabel?.let {
            stringResource(R.string.dashboard_credit_cards_aggregate_statement_spending, it)
        } ?: stringResource(R.string.dashboard_credit_cards_aggregate_statement_spending_fallback),
        unavailableLabel = stringResource(R.string.dashboard_value_unavailable),
        languageTag = locale.toLanguageTag(),
    )
}

@Composable
fun loansSummaryHeroSpec(
    overview: LoansOverview,
): DashboardSummaryHeroSpec {
    val locale = accountsSummaryHeroLocale
    val paymentTitle = if (overview.salaryPeriodLabel != null) {
        stringResource(R.string.dashboard_loans_aggregate_period_payment, overview.salaryPeriodLabel)
    } else {
        stringResource(R.string.dashboard_loans_aggregate_period_payment_fallback)
    }

    return buildLoansSummaryHeroSpec(
        overview = overview,
        remainingTitle = stringResource(R.string.dashboard_loans_remaining_total_title),
        paymentTitle = paymentTitle,
        unavailableLabel = stringResource(R.string.dashboard_value_unavailable),
        languageTag = locale.toLanguageTag(),
    )
}

internal fun buildAccountsSummaryHeroSpec(
    fleet: AccountsSummary,
    remainingTitle: String,
    inflowTitle: String,
    outflowTitle: String,
    unavailableLabel: String,
    formulaHint: String?,
    footerHint: String,
    languageTag: String = AppLocale.TAG_AR,
): DashboardSummaryHeroSpec {
    val totalRemaining = fleet.totalRemaining
    return DashboardSummaryHeroSpec(
        accent = MasroofCardAccent.Account,
        primary = DashboardSummaryMetricItem(
            title = remainingTitle,
            amount = totalRemaining?.let { formatHeroMoney(it, languageTag) } ?: unavailableLabel,
            tone = DashboardMetricTone.Signed,
            signedAmount = totalRemaining?.amount,
            hint = formulaHint,
        ),
        secondary = listOf(
            moneyHeroMetric(inflowTitle, fleet.totalInflow, DashboardMetricTone.Inflow, unavailableLabel, languageTag),
            moneyHeroMetric(outflowTitle, fleet.totalOutflow, DashboardMetricTone.Outflow, unavailableLabel, languageTag),
        ),
        footerHint = footerHint,
    )
}

internal fun buildCreditSummaryHeroSpec(
    aggregateDue: Money?,
    dueDateLabel: String?,
    periodSpending: SignedMoneyAmount,
    statementSpending: SignedMoneyAmount,
    dueTitle: String,
    periodSpendingTitle: String,
    statementSpendingTitle: String,
    unavailableLabel: String,
    languageTag: String = AppLocale.TAG_AR,
): DashboardSummaryHeroSpec {
    return DashboardSummaryHeroSpec(
        accent = MasroofCardAccent.Credit,
        primary = DashboardSummaryMetricItem(
            title = dueTitle,
            amount = aggregateDue?.let { formatHeroMoney(it, languageTag) } ?: unavailableLabel,
            tone = DashboardMetricTone.Liability,
            hint = dueDateLabel,
        ),
        secondary = listOf(
            DashboardSummaryMetricItem(
                title = periodSpendingTitle,
                amount = formatHeroMoney(periodSpending, languageTag),
                tone = spendingMetricTone(periodSpending),
            ),
            DashboardSummaryMetricItem(
                title = statementSpendingTitle,
                amount = formatHeroMoney(statementSpending, languageTag),
                tone = spendingMetricTone(statementSpending),
            ),
        ),
    )
}

internal fun buildLoansSummaryHeroSpec(
    overview: LoansOverview,
    remainingTitle: String,
    paymentTitle: String,
    unavailableLabel: String,
    languageTag: String = AppLocale.TAG_AR,
): DashboardSummaryHeroSpec {
    val aggregateRemaining = overview.aggregateRemainingBalance()
    val aggregatePayment = overview.aggregateSalaryPeriodPayment()
    return DashboardSummaryHeroSpec(
        accent = MasroofCardAccent.Liability,
        primary = DashboardSummaryMetricItem(
            title = remainingTitle,
            amount = aggregateRemaining?.let { formatHeroMoney(it, languageTag) } ?: unavailableLabel,
            tone = DashboardMetricTone.Liability,
        ),
        secondary = listOf(
            DashboardSummaryMetricItem(
                title = paymentTitle,
                amount = formatHeroMoney(aggregatePayment, languageTag),
                tone = spendingMetricTone(aggregatePayment),
            ),
        ),
    )
}

private fun moneyHeroMetric(
    title: String,
    money: Money?,
    tone: DashboardMetricTone,
    unavailableLabel: String,
    languageTag: String,
): DashboardSummaryMetricItem =
    DashboardSummaryMetricItem(
        title = title,
        amount = money?.let { formatHeroMoney(it, languageTag) } ?: unavailableLabel,
        tone = tone,
    )

private fun formatHeroMoney(money: Money, languageTag: String): String =
    MoneyUiFormatter.format(money, languageTag)

private fun formatHeroMoney(signed: SignedMoneyAmount, languageTag: String): String =
    MoneyUiFormatter.format(signed, languageTag)
