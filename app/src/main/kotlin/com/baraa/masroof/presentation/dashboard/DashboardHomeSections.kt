package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.presentation.common.MasroofSectionHeader

import com.baraa.masroof.presentation.common.MasroofAmountRole

import com.baraa.masroof.presentation.common.MasroofAmountText

import com.baraa.masroof.presentation.common.MasroofTextStyles

import com.baraa.masroof.presentation.theme.MasroofSpacing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.AccountsSummary
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.application.dashboard.cashPosition
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofCompactCard
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun DashboardHeroCard(
    accountsFleet: AccountsSummary,
    period: FinancialPeriod?,
    isCurrentPeriod: Boolean,
    today: LocalDate,
    size: com.baraa.masroof.application.dashboard.DashboardSectionSize = com.baraa.masroof.application.dashboard.DashboardSectionSize.MEDIUM,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    val remaining = accountsFleet.totalRemaining
        ?: SignedMoneyAmount.zero(accountsFleet.currency ?: Currency.SAR)
    val totalInflow = accountsFleet.totalInflow ?: Money.zero(remaining.currency)
    val totalOutflow = accountsFleet.totalOutflow ?: Money.zero(remaining.currency)
    val remainingColor = when {
        remaining.amount.signum() > 0 -> extended.inflow
        remaining.amount.signum() < 0 -> extended.outflow
        else -> MaterialTheme.colorScheme.primary
    }

    MasroofCard(modifier = modifier) {
        Text(
            stringResource(R.string.dashboard_hero_remaining_title),
            style = MasroofTextStyles.hint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MasroofAmountText(
            amount = formatLocalizedMoney(remaining),
            role = MasroofAmountRole.Hero,
            color = remainingColor,
            heroScale = heroAmountStyleScale(size),
            modifier = Modifier.padding(top = 6.dp),
        )

        period?.let { financialPeriod ->
            val (progress, daysLabel) = periodProgress(financialPeriod, today, isCurrentPeriod)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = MasroofIcons.calendar,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            daysLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(
                    R.string.dashboard_hero_footer_income,
                    formatLocalizedMoney(totalInflow),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(
                    R.string.dashboard_hero_footer_expense,
                    formatLocalizedMoney(totalOutflow),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun periodProgress(
    period: FinancialPeriod,
    today: LocalDate,
    isCurrentPeriod: Boolean,
): Pair<Float, String> {
    val totalDays = ChronoUnit.DAYS.between(period.startDate, period.endDateExclusive).toInt()
    if (totalDays <= 0) return 0f to ""

    if (!isCurrentPeriod) {
        return 1f to stringResource(R.string.dashboard_period_days_total, totalDays)
    }

    val elapsed = ChronoUnit.DAYS.between(period.startDate, today)
        .coerceIn(0, totalDays.toLong())
        .toInt()
    val remainingDays = (totalDays - elapsed).coerceAtLeast(0)
    val progress = elapsed.toFloat() / totalDays.toFloat()
    return progress to stringResource(R.string.dashboard_days_remaining, remainingDays, totalDays)
}

@Composable
fun DashboardQuickSummaryRow(
    accountsFleet: AccountsSummary,
    onOpenExpenseDetails: () -> Unit,
    onOpenIncomeDetails: () -> Unit,
    showExpense: Boolean = true,
    showIncome: Boolean = true,
    size: com.baraa.masroof.application.dashboard.DashboardSectionSize = com.baraa.masroof.application.dashboard.DashboardSectionSize.MEDIUM,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    val remaining = accountsFleet.totalRemaining
        ?: SignedMoneyAmount.zero(accountsFleet.currency ?: Currency.SAR)
    val totalInflow = accountsFleet.totalInflow ?: Money.zero(remaining.currency)
    val totalOutflow = accountsFleet.totalOutflow ?: Money.zero(remaining.currency)

    val cardPadding = quickCardPadding(size)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.cardInnerGap),
    ) {
        MasroofCompactCard(
            label = stringResource(R.string.dashboard_quick_net_remaining),
            value = formatLocalizedMoney(remaining),
            valueColor = when {
                remaining.amount.signum() > 0 -> extended.inflow
                remaining.amount.signum() < 0 -> extended.outflow
                else -> MaterialTheme.colorScheme.primary
            },
            icon = MasroofIcons.appLogo,
            iconBackground = MaterialTheme.colorScheme.surfaceVariant,
            iconTint = MaterialTheme.colorScheme.primary,
            contentPadding = cardPadding,
            modifier = Modifier.weight(1f),
        )
        if (showExpense) {
            MasroofCompactCard(
                label = stringResource(R.string.dashboard_quick_total_expense),
                value = formatLocalizedMoney(totalOutflow),
                valueColor = extended.outflow,
                icon = MasroofIcons.netSpending,
                iconBackground = extended.outflowSoft,
                iconTint = extended.outflow,
                clickable = true,
                onClick = onOpenExpenseDetails,
                contentPadding = cardPadding,
                modifier = Modifier.weight(1f),
            )
        }
        if (showIncome) {
            MasroofCompactCard(
                label = stringResource(R.string.dashboard_quick_total_income),
                value = formatLocalizedMoney(totalInflow),
                valueColor = extended.inflow,
                icon = MasroofIcons.income,
                iconBackground = extended.inflowSoft,
                iconTint = extended.inflow,
                clickable = true,
                onClick = onOpenIncomeDetails,
                contentPadding = cardPadding,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun DashboardAccountsSection(
    accounts: List<OwnedAccountUi>,
    modifier: Modifier = Modifier,
    onViewAll: (() -> Unit)? = null,
) {
    if (accounts.isEmpty()) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap)) {
        MasroofSectionHeader(
            title = stringResource(R.string.dashboard_accounts_summary_title),
            icon = MasroofIcons.moneyMovement,
            onViewAll = onViewAll,
            viewAllLabel = stringResource(R.string.dashboard_view_all),
        )
        MasroofCard {
            accounts.forEachIndexed { index, account ->
                if (index > 0) {
                    Spacer(Modifier.height(MasroofSpacing.listItemGap))
                }
                DashboardAccountRow(account = account, index = index)
            }
        }
    }
}

@Composable
private fun DashboardAccountRow(
    account: OwnedAccountUi,
    index: Int,
) {
    val extended = MasroofThemeExtras.extendedColors
    val summary = account.periodSummary
    val movement = summary?.cashPosition()
    val periodInflow = movement?.inflow
    val periodOutflow = movement?.outflow
    val remaining = movement?.remaining
    val iconOptions = listOf(
        Triple(MasroofIcons.moneyMovement, extended.accountSoft, extended.account),
        Triple(MasroofIcons.savings, extended.inflowSoft, extended.inflow),
        Triple(MasroofIcons.cardPayment, extended.cardSoft, extended.card),
    )
    val (icon, iconBg, iconTint) = iconOptions[index % iconOptions.size]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(MasroofSpacing.entityIconSize)
                .clip(RoundedCornerShape(MasroofSpacing.entityIconRadius))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconTint,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                account.displayLabel(),
                style = MasroofTextStyles.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (periodInflow != null && periodOutflow != null) {
                Text(
                    stringResource(
                        R.string.dashboard_account_period_in_out,
                        formatLocalizedMoney(periodInflow),
                        formatLocalizedMoney(periodOutflow),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                stringResource(R.string.dashboard_account_remaining_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MasroofAmountText(
                amount = remaining?.let { formatLocalizedMoney(it) }
                    ?: stringResource(R.string.dashboard_value_unavailable),
                role = MasroofAmountRole.Card,
                color = remaining?.let { value ->
                    when {
                        value.amount.signum() > 0 -> extended.inflow
                        value.amount.signum() < 0 -> extended.outflow
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                } ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
