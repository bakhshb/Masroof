package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CurrentAccountSummary
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.presentation.common.MasroofBadge
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofCardAccent
import com.baraa.masroof.presentation.common.MasroofMoneyRow
import com.baraa.masroof.presentation.common.MasroofMoneyRowStyle
import com.baraa.masroof.presentation.common.SectionHeader
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

@Composable
fun CurrentAccountSection(
    summary: CurrentAccountSummary,
    accountBadge: String?,
    ownedAccountCount: Int = 1,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(R.string.dashboard_current_account_section),
            icon = com.baraa.masroof.presentation.common.MasroofIcons.externalIn,
        )

        MasroofCard(accent = MasroofCardAccent.Account) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.dashboard_remaining_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(
                            if (ownedAccountCount > 1) {
                                R.string.dashboard_current_account_net_subtitle_multi
                            } else {
                                R.string.dashboard_current_account_net_subtitle
                            },
                        ),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
                accountBadge?.let { badge ->
                    MasroofBadge(text = badge, accent = MasroofCardAccent.Account)
                }
            }

            val net = summary.netMovement
            Text(
                formatLocalizedMoney(net),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = when {
                        net.amount.signum() > 0 -> extended.inflow
                        net.amount.signum() < 0 -> extended.outflow
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                ),
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                stringResource(
                    R.string.dashboard_remaining_formula,
                    formatLocalizedMoney(summary.totalInflow),
                    formatLocalizedMoney(summary.totalOutflow),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.dashboard_current_account_inflows),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                FlowRow(
                    label = stringResource(R.string.dashboard_salary),
                    amount = summary.salary,
                    direction = TransactionDirectionUi.INCOME,
                )
                FlowRow(
                    label = stringResource(R.string.dashboard_external_in_short),
                    amount = summary.externalTransfersIn,
                    direction = TransactionDirectionUi.TRANSFER_IN,
                )
                if (summary.otherIncome.amount.signum() > 0) {
                    FlowRow(
                        label = stringResource(R.string.dashboard_other_income),
                        amount = summary.otherIncome,
                        direction = TransactionDirectionUi.INCOME,
                    )
                }
            }
            TotalRow(
                label = stringResource(R.string.dashboard_total_inflow),
                amount = summary.totalInflow,
            )

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.dashboard_money_movement),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                DirectionMoneyRow(
                    label = stringResource(R.string.dashboard_external_out_short),
                    amount = summary.externalTransfersOut,
                    direction = TransactionDirectionUi.OUTWARD,
                )
                DirectionMoneyRow(
                    label = stringResource(R.string.dashboard_credit_card_payment),
                    amount = summary.creditCardPayments,
                    direction = TransactionDirectionUi.OUTWARD,
                )
                DirectionMoneyRow(
                    label = stringResource(R.string.dashboard_cash_withdrawals),
                    amount = summary.cashWithdrawals,
                    direction = TransactionDirectionUi.OUTWARD,
                )
                DirectionMoneyRow(
                    label = stringResource(R.string.dashboard_bill_payments),
                    amount = summary.billPayments,
                    direction = TransactionDirectionUi.OUTWARD,
                )
                DirectionMoneyRow(
                    label = stringResource(R.string.dashboard_pos_purchases_short),
                    amount = summary.posPurchases,
                    direction = TransactionDirectionUi.OUTWARD,
                )
                DirectionMoneyRow(
                    label = stringResource(R.string.dashboard_fees_short),
                    amount = summary.fees,
                    direction = TransactionDirectionUi.OUTWARD,
                )
            }
            TotalRow(
                label = stringResource(R.string.dashboard_total_spent),
                amount = summary.totalOutflow,
                amountColor = extended.outflow,
            )

            if (
                summary.selfTransfersIn.amount.signum() > 0 ||
                summary.selfTransfersOut.amount.signum() > 0
            ) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    stringResource(R.string.dashboard_self_transfers),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    FlowRow(
                        label = stringResource(R.string.dashboard_self_transfer_in),
                        amount = summary.selfTransfersIn,
                        direction = TransactionDirectionUi.NEUTRAL,
                    )
                    FlowRow(
                        label = stringResource(R.string.dashboard_self_transfer_out),
                        amount = summary.selfTransfersOut,
                        direction = TransactionDirectionUi.NEUTRAL,
                    )
                }
                Text(
                    stringResource(R.string.dashboard_self_transfers_neutral_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DirectionMoneyRow(
    label: String,
    amount: Money,
    direction: TransactionDirectionUi,
) {
    if (amount.amount.signum() == 0) return
    MasroofMoneyRow(
        label = label,
        value = formatLocalizedMoney(amount),
        style = directionMoneyRowStyle(direction),
        leadingIcon = TransactionDirectionPresentation.icon(direction),
    )
}

@Composable
private fun FlowRow(
    label: String,
    amount: Money,
    direction: TransactionDirectionUi,
) {
    DirectionMoneyRow(label = label, amount = amount, direction = direction)
}

private fun directionMoneyRowStyle(direction: TransactionDirectionUi): MasroofMoneyRowStyle =
    when (direction) {
        TransactionDirectionUi.INCOME,
        TransactionDirectionUi.INWARD,
        TransactionDirectionUi.TRANSFER_IN,
        -> MasroofMoneyRowStyle.Inflow
        TransactionDirectionUi.OUTWARD -> MasroofMoneyRowStyle.Outflow
        TransactionDirectionUi.NEUTRAL -> MasroofMoneyRowStyle.Neutral
    }

@Composable
private fun TotalRow(
    label: String,
    amount: Money,
    amountColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            formatLocalizedMoney(amount),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = amountColor,
        )
    }
}
