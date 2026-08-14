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
import com.baraa.masroof.presentation.common.SummaryMiniCard

@Composable
fun CurrentAccountSection(
    summary: CurrentAccountSummary,
    accountBadge: String?,
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
                                stringResource(R.string.dashboard_current_account_net_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.dashboard_current_account_net_subtitle),
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
                            R.string.dashboard_current_account_net_formula,
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
                        label = stringResource(R.string.dashboard_income),
                        amount = summary.income,
                        positive = true,
                    )
                    FlowRow(
                        label = stringResource(R.string.dashboard_external_in_short),
                        amount = summary.externalTransfersIn,
                        positive = true,
                    )

                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.dashboard_current_account_outflows),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        label = stringResource(R.string.dashboard_card_payments),
                        amount = summary.creditCardPayments,
                        positive = false,
                    )
                    if (summary.billPayments.amount.signum() > 0) {
                        FlowRow(
                            label = stringResource(R.string.dashboard_bill_payments),
                            amount = summary.billPayments,
                            positive = false,
                        )
                    }
                    FlowRow(
                        label = stringResource(R.string.dashboard_external_out_short),
                        amount = summary.externalTransfersOut,
                        positive = false,
                    )
                    FlowRow(
                        label = stringResource(R.string.dashboard_cash_withdrawals),
                        amount = summary.cashWithdrawals,
                        positive = false,
                    )
                    FlowRow(
                        label = stringResource(R.string.dashboard_pos_purchases),
                        amount = summary.posPurchases + summary.fees,
                        positive = false,
                    )
                }
            }
        }
    }
}

@Composable
fun SpendingSplitSection(
    spendingSplit: SpendingSplitSummary,
    currentAccount: CurrentAccountSummary,
    followedCardsSpending: SignedMoneyAmount? = null,
    unknownCardCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(R.string.dashboard_spending_split_title),
            icon = MasroofIcons.netSpending,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryMiniCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.dashboard_spending_from_account),
                value = formatLocalizedMoney(spendingSplit.fromCurrentAccount),
                icon = MasroofIcons.externalOut,
            )
            SummaryMiniCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.dashboard_spending_on_card),
                value = formatLocalizedMoney(spendingSplit.onCreditCard),
                icon = MasroofIcons.cardPayment,
            )
        }

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
                        formatLocalizedMoney(spendingSplit.totalNet),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
                Text(
                    stringResource(R.string.dashboard_spending_split_account_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val excludedTransfers = currentAccount.externalTransfersOut
                val excludedCash = currentAccount.cashWithdrawals
                val excludedCardPay = currentAccount.creditCardPayments
                if (
                    excludedTransfers.amount.signum() > 0 ||
                    excludedCash.amount.signum() > 0 ||
                    excludedCardPay.amount.signum() > 0
                ) {
                    Text(
                        stringResource(
                            R.string.dashboard_spending_split_excluded,
                            formatLocalizedMoney(excludedTransfers),
                            formatLocalizedMoney(excludedCash),
                            formatLocalizedMoney(excludedCardPay),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.dashboard_spending_split_card_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                followedCardsSpending?.let { followed ->
                    val otherCardsDelta = spendingSplit.onCreditCard.amount
                        .subtract(followed.amount)
                        .setScale(Money.SCALE, java.math.RoundingMode.HALF_EVEN)
                    if (otherCardsDelta.signum() > 0) {
                        Text(
                            stringResource(
                                R.string.dashboard_spending_split_other_cards,
                                formatLocalizedMoney(SignedMoneyAmount(otherCardsDelta, spendingSplit.currency)),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
private fun FlowRow(
    label: String,
    amount: Money,
    positive: Boolean,
) {
    if (amount.amount.signum() == 0) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (if (positive) "↑ " else "↓ ") + label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            formatLocalizedMoney(amount),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (positive) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}
