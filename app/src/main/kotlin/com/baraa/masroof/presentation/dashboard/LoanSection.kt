package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.LoanOverview
import com.baraa.masroof.application.dashboard.LoansOverview
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofCardAccent
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.SectionHeader
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

private val dashboardLoanTileMinHeight = 200.dp

@Composable
fun LoansSection(
    overview: LoansOverview,
    modifier: Modifier = Modifier,
    onViewAll: (() -> Unit)? = null,
    onOpenLoan: ((LoanOverview) -> Unit)? = null,
    tileModifier: Modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = dashboardLoanTileMinHeight),
) {
    if (!overview.hasContent) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(R.string.dashboard_loans_summary_title),
            icon = MasroofIcons.moneyMovement,
            onViewAll = onViewAll,
            viewAllLabel = stringResource(R.string.dashboard_view_all),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(overview.loans, key = { LoanOwnershipKey.of(it) }) { loan ->
                LoanSummaryTile(
                    loan = loan,
                    modifier = tileModifier.then(
                        if (onOpenLoan != null) {
                            Modifier.clickable { onOpenLoan(loan) }
                        } else {
                            Modifier
                        },
                    ),
                )
            }
        }
    }
}

@Composable
fun LoanSummaryTile(
    loan: LoanOverview,
    modifier: Modifier = Modifier,
    showNavigationIcon: Boolean = true,
) {
    val extended = MasroofThemeExtras.extendedColors
    val remainingLabel = stringResource(R.string.dashboard_loan_remaining)
    val paymentLabel = if (loan.salaryPeriodLabel != null) {
        stringResource(R.string.dashboard_loan_period_payment, loan.salaryPeriodLabel)
    } else {
        stringResource(R.string.dashboard_loan_period_payment_fallback)
    }

    MasroofCard(modifier = modifier.fillMaxHeight(), accent = MasroofCardAccent.Credit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.dashboard_loan_type_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    loan.displayLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            if (showNavigationIcon) {
                Icon(
                    imageVector = MasroofIcons.periodNext,
                    contentDescription = null,
                    tint = extended.liability,
                )
            }
        }

        Text(
            remainingLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            loan.remainingBalance?.let { formatLocalizedMoney(it) }
                ?: stringResource(R.string.dashboard_value_unavailable),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = extended.liability,
        )

        Text(
            paymentLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            formatLocalizedMoney(loan.salaryPeriodPayment),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun LoanDetailSummaryCard(
    loan: LoanOverview,
    modifier: Modifier = Modifier,
) {
    val metrics = buildList {
        add(
            DashboardSummaryMetricItem(
                title = stringResource(R.string.dashboard_loan_remaining),
                amount = loan.remainingBalance?.let { formatLocalizedMoney(it) }
                    ?: stringResource(R.string.dashboard_value_unavailable),
                tone = DashboardMetricTone.Liability,
            ),
        )
        add(
            DashboardSummaryMetricItem(
                title = if (loan.salaryPeriodLabel != null) {
                    stringResource(R.string.dashboard_loan_period_payment, loan.salaryPeriodLabel)
                } else {
                    stringResource(R.string.dashboard_loan_period_payment_fallback)
                },
                amount = formatLocalizedMoney(loan.salaryPeriodPayment),
                tone = spendingMetricTone(loan.salaryPeriodPayment),
            ),
        )
    }

    MasroofCard(modifier = modifier, accent = MasroofCardAccent.Credit) {
        Text(
            loan.displayLabel,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        DashboardSummaryMetricGrid(
            metrics = metrics,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
