package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.formatCardLast4
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.locale.formatLocalizedTransactionDate
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

@Composable
fun DashboardRecentTransactionRow(
    row: TransactionPreviewUi,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val extended = MasroofThemeExtras.extendedColors
    val title = row.title ?: transactionTypeLabel(row.type)
    val isIncoming = row.direction == TransactionDirectionUi.INCOME ||
        row.direction == TransactionDirectionUi.INWARD ||
        row.direction == TransactionDirectionUi.TRANSFER_IN
    val badgeLabel = if (isIncoming) {
        stringResource(R.string.dashboard_tx_badge_income)
    } else {
        stringResource(R.string.dashboard_tx_badge_expense)
    }
    val badgeColors = if (isIncoming) {
        extended.inflowSoft to extended.inflow
    } else {
        extended.outflowSoft to extended.outflow
    }
    val amountColor = if (isIncoming) extended.inflow else extended.outflow

    MasroofCard(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = MasroofIcons.transactionType(row.type),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(transactionTypeLabel(row.type))
                        row.cardLast4?.let {
                            append(" · ")
                            append(stringResource(R.string.dashboard_credit_card_last4, formatCardLast4(it)))
                        }
                        append(" · ")
                        append(formatLocalizedTransactionDate(row.localDate))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = badgeColors.first,
                ) {
                    Text(
                        badgeLabel,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = badgeColors.second,
                    )
                }
                Text(
                    formatLocalizedMoney(row.amount),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = amountColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
