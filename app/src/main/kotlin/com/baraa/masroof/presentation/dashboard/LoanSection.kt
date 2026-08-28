package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.LoanOverview
import com.baraa.masroof.application.dashboard.LoansOverview
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.SectionHeader
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dashboardLoanTileMinHeight = 235.dp
private val dashboardLoanTileWidth = 288.dp

@Composable
fun LoansSection(
    overview: LoansOverview,
    modifier: Modifier = Modifier,
    onViewAll: (() -> Unit)? = null,
    onOpenLoan: ((LoanOverview) -> Unit)? = null,
    tileModifier: Modifier = Modifier
        .width(dashboardLoanTileWidth)
        .heightIn(min = dashboardLoanTileMinHeight),
) {
    if (!overview.hasContent) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(R.string.dashboard_loans_summary_title),
            icon = MasroofIcons.savings,
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
                    modifier = tileModifier,
                    onClick = onOpenLoan?.let { open -> { open(loan) } },
                )
            }
        }
    }
}

@Composable
fun LoanIdentityBadge(
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    Box(
        modifier = modifier
            .size(width = 36.dp, height = 24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(extended.liability.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MasroofIcons.savings,
            contentDescription = null,
            tint = extended.liability,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
fun LoanSummaryTile(
    loan: LoanOverview,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showNavigationIcon: Boolean = onClick != null,
) {
    val extended = MasroofThemeExtras.extendedColors
    val paymentLabel = loanPeriodPaymentLabel(loan)
    val paymentColor = resolveMetricToneColor(spendingMetricTone(loan.salaryPeriodPayment))
    val remainingAmount = loan.remainingBalance?.let { formatLocalizedMoney(it) }
        ?: stringResource(R.string.dashboard_value_unavailable)

    MasroofCard(
        modifier = modifier
            .fillMaxHeight()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LoanIdentityBadge()
                    Column {
                        Text(
                            stringResource(R.string.dashboard_loan_type_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            loan.displayLabel,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (showNavigationIcon) {
                    Icon(
                        imageVector = MasroofIcons.periodNext,
                        contentDescription = null,
                        tint = extended.account,
                    )
                }
            }

            Text(
                stringResource(R.string.dashboard_loan_remaining),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                remainingAmount,
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
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = paymentColor,
            )
        }
    }
}

@Composable
fun LoanDetailSummaryCard(
    loan: LoanOverview,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm", locale)
    val extended = MasroofThemeExtras.extendedColors
    val paymentLabel = loanPeriodPaymentLabel(loan)
    val paymentColor = resolveMetricToneColor(spendingMetricTone(loan.salaryPeriodPayment))
    val remainingAmount = loan.remainingBalance?.let { formatLocalizedMoney(it) }
        ?: stringResource(R.string.dashboard_value_unavailable)

    MasroofCard(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LoanIdentityBadge()
            Column {
                Text(
                    loan.displayLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    stringResource(R.string.dashboard_loan_type_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            stringResource(R.string.dashboard_loan_remaining),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            remainingAmount,
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
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = paymentColor,
        )

        loan.remainingBalanceAsOf?.let { asOf ->
            Text(
                stringResource(
                    R.string.dashboard_credit_card_updated,
                    formatLoanBalanceTime(asOf, zoneId, dateTimeFormatter),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
internal fun loanPeriodPaymentLabel(loan: LoanOverview): String =
    if (loan.salaryPeriodLabel != null) {
        stringResource(R.string.dashboard_loan_period_payment, loan.salaryPeriodLabel)
    } else {
        stringResource(R.string.dashboard_loan_period_payment_fallback)
    }

private fun formatLoanBalanceTime(
    instant: java.time.Instant,
    zoneId: ZoneId,
    formatter: DateTimeFormatter,
): String = formatter.format(instant.atZone(zoneId))
