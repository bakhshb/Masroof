package com.baraa.masroof.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.baraa.masroof.ui.theme.AccountBadge
import com.baraa.masroof.ui.theme.FinancialShapes
import com.baraa.masroof.ui.theme.InstitutionBadge
import com.baraa.masroof.ui.theme.ReviewBadge
import com.baraa.masroof.ui.theme.SemanticColors
import com.baraa.masroof.ui.theme.Spacing
import com.baraa.masroof.ui.theme.StatusBadge
import java.text.NumberFormat
import java.util.Locale

/**
 * Single-row transaction card. Never shows raw SMS or parser internals;
 * always shows institution and account or payment instrument. Total amount
 * displayed in red for confirmed expenses, green for income.
 */
@Composable
fun TransactionRow(
    presentation: TransactionPresentation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sign = when (presentation.isExpense) {
        true -> "−"
        false -> "+"
        null -> "·"
    }
    val formatted = remember(presentation.amount) {
        NumberFormat.getNumberInstance(Locale("ar", "SA")).apply { maximumFractionDigits = 2; minimumFractionDigits = 0 }.format(presentation.amount.abs())
    }
    val amountColor = when {
        presentation.isExpense == true -> SemanticColors.expense()
        presentation.isExpense == false -> SemanticColors.positive()
        else -> MaterialTheme.colorScheme.onSurface
    }
    val leadingIcon = transactionLeadingIcon(presentation)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick,
    ) {
        Column(Modifier.padding(Spacing.x4)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.x3)) {
                    Surface(
                        shape = FinancialShapes.pill,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = leadingIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column {
                        Text(presentation.merchantOrLabel, style = com.baraa.masroof.ui.theme.FinancialTypography.merchant)
                        Spacer(Modifier.height(2.dp))
                        Text(presentation.friendlyType, style = com.baraa.masroof.ui.theme.FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("$sign$formatted ${presentation.currency}", style = com.baraa.masroof.ui.theme.FinancialTypography.financialTotal, color = amountColor)
            }
            Spacer(Modifier.height(Spacing.x2))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2), verticalAlignment = Alignment.CenterVertically) {
                InstitutionBadge(name = presentation.institutionDisplayName)
                AccountBadge(label = presentation.accountOrInstrumentLabel)
                presentation.channelLabel?.let {
                    InstitutionBadge(
                        name = it,
                        color = SemanticColors.brandContainer(),
                        onColor = SemanticColors.onBrandContainer(),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.x1))
            Text(presentation.dateLabel, style = com.baraa.masroof.ui.theme.FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (presentation.isBeforeTrackingStart) {
                Spacer(Modifier.height(Spacing.x2))
                StatusBadge(
                    label = "العملية تسبق تاريخ بداية المتابعة",
                    color = SemanticColors.warningContainer(),
                    onColor = SemanticColors.warning(),
                )
            }
            if (presentation.requiresReview) {
                Spacer(Modifier.height(Spacing.x2))
                ReviewBadge()
            }
        }
    }
}

@Composable
private fun transactionLeadingIcon(presentation: TransactionPresentation): ImageVector {
    if (presentation.requiresReview) return Icons.Filled.Warning
    return when (presentation.isExpense) {
        true -> if (presentation.friendlyType.contains("رسوم") || presentation.friendlyType.contains("Fee", ignoreCase = true)) {
            Icons.Filled.Receipt
        } else {
            Icons.Filled.ShoppingCart
        }
        false -> Icons.Filled.Savings
        null -> Icons.Filled.SwapHoriz
    }
}
