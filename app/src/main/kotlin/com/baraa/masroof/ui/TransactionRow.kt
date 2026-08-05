package com.baraa.masroof.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.baraa.masroof.ui.theme.AccountBadge
import com.baraa.masroof.ui.theme.FinancialPalette
import com.baraa.masroof.ui.theme.FinancialShapes
import com.baraa.masroof.ui.theme.InstitutionBadge
import com.baraa.masroof.ui.theme.ReviewBadge
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
        presentation.isExpense == true && presentation.amount.signum() < 0 -> FinancialPalette.Expense
        presentation.isExpense == false && presentation.amount.signum() > 0 -> FinancialPalette.Positive
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick,
    ) {
        Column(Modifier.padding(Spacing.x4)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(presentation.merchantOrLabel, style = com.baraa.masroof.ui.theme.FinancialTypography.merchant)
                    Spacer(Modifier.height(2.dp))
                    Text(presentation.friendlyType, style = com.baraa.masroof.ui.theme.FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("$sign$formatted ${presentation.currency}", style = com.baraa.masroof.ui.theme.FinancialTypography.financialTotal, color = amountColor)
            }
            Spacer(Modifier.height(Spacing.x2))
            // Institution + Account/Instrument line — NEVER optional.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2), verticalAlignment = Alignment.CenterVertically) {
                InstitutionBadge(name = presentation.institutionDisplayName)
                AccountBadge(label = presentation.accountOrInstrumentLabel)
                presentation.channelLabel?.let { InstitutionBadge(name = it, color = FinancialPalette.NavyContainer, onColor = FinancialPalette.NavyPrimary) }
            }
            Spacer(Modifier.height(Spacing.x1))
            Text(presentation.dateLabel, style = com.baraa.masroof.ui.theme.FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (presentation.isBeforeTrackingStart) {
                Spacer(Modifier.height(Spacing.x2))
                StatusBadge(label = "العملية تسبق تاريخ بداية المتابعة", color = FinancialPalette.WarningContainer, onColor = FinancialPalette.Warning)
            }
            if (presentation.requiresReview) {
                Spacer(Modifier.height(Spacing.x2))
                ReviewBadge()
            }
        }
    }
}

// remember() is provided by androidx.compose.runtime.
