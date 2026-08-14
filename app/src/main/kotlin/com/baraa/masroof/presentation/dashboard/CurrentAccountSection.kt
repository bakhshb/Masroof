package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CurrentAccountSummary
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.application.dashboard.SpendingSplitSummary
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.SectionHeader

@Composable
fun CurrentAccountSection(
    summary: CurrentAccountSummary,
    accountBadge: String?,
    ownedAccountCount: Int = 1,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(R.string.dashboard_current_account_section),
            icon = MasroofIcons.externalIn,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
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
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        accountBadge?.let { badge ->
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    badge,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val net = summary.netMovement
                    Text(
                        formatLocalizedMoney(net),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = when {
                                net.amount.signum() > 0 -> MaterialTheme.colorScheme.tertiary
                                net.amount.signum() < 0 -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        ),
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

                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.dashboard_current_account_inflows),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        label = stringResource(R.string.dashboard_salary),
                        amount = summary.salary,
                        positive = true,
                    )
                    FlowRow(
                        label = stringResource(R.string.dashboard_external_in_short),
                        amount = summary.externalTransfersIn,
                        positive = true,
                    )
                    if (summary.otherIncome.amount.signum() > 0) {
                        FlowRow(
                            label = stringResource(R.string.dashboard_other_income),
                            amount = summary.otherIncome,
                            positive = true,
                        )
                    }
                    TotalRow(
                        label = stringResource(R.string.dashboard_total_inflow),
                        amount = summary.totalInflow,
                    )

                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.dashboard_money_movement),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutflowRow(
                        label = stringResource(R.string.dashboard_external_out_short),
                        amount = summary.externalTransfersOut,
                    )
                    OutflowRow(
                        label = stringResource(R.string.dashboard_credit_card_payment),
                        amount = summary.creditCardPayments,
                    )
                    OutflowRow(
                        label = stringResource(R.string.dashboard_cash_withdrawals),
                        amount = summary.cashWithdrawals,
                    )
                    OutflowRow(
                        label = stringResource(R.string.dashboard_bill_payments),
                        amount = summary.billPayments,
                    )
                    OutflowRow(
                        label = stringResource(R.string.dashboard_pos_purchases_short),
                        amount = summary.posPurchases + summary.fees,
                    )
                    TotalRow(
                        label = stringResource(R.string.dashboard_total_spent),
                        amount = summary.totalOutflow,
                        amountColor = MaterialTheme.colorScheme.error,
                    )

                    if (
                        summary.selfTransfersIn.amount.signum() > 0 ||
                        summary.selfTransfersOut.amount.signum() > 0
                    ) {
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.dashboard_self_transfers),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            label = stringResource(R.string.dashboard_self_transfer_in),
                            amount = summary.selfTransfersIn,
                            neutral = true,
                        )
                        FlowRow(
                            label = stringResource(R.string.dashboard_self_transfer_out),
                            amount = summary.selfTransfersOut,
                            neutral = true,
                        )
                        Text(
                            stringResource(R.string.dashboard_self_transfers_neutral_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpendingSplitSection(
    spendingSplit: SpendingSplitSummary,
    unknownCardCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(R.string.dashboard_spending_split_title),
            icon = MasroofIcons.netSpending,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.dashboard_spending_total),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        formatLocalizedMoney(spendingSplit.totalSpending),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        ),
                    )
                }
                Text(
                    stringResource(R.string.dashboard_spending_total_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.dashboard_spending_breakdown_formula),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (spendingSplit.creditCardPurchases.amount.signum() > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.dashboard_spending_on_card),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            formatLocalizedMoney(spendingSplit.creditCardPurchases),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        stringResource(R.string.dashboard_spending_excludes_card_purchases),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (unknownCardCount > 0) {
                    Text(
                        stringResource(R.string.dashboard_spending_split_unknown_cards, unknownCardCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun OutflowRow(
    label: String,
    amount: Money,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "↓ $label",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            formatLocalizedMoney(amount),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun FlowRow(
    label: String,
    amount: Money,
    positive: Boolean = false,
    neutral: Boolean = false,
) {
    if (amount.amount.signum() == 0) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val prefix = when {
            neutral -> "↔ "
            positive -> "↑ "
            else -> "↓ "
        }
        Text(
            prefix + label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            formatLocalizedMoney(amount),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = when {
                neutral -> MaterialTheme.colorScheme.onSurfaceVariant
                positive -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
            },
        )
    }
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
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            formatLocalizedMoney(amount),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = amountColor,
        )
    }
}
