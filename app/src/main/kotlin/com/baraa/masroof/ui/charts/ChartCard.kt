package com.baraa.masroof.ui.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baraa.masroof.ui.theme.FinancialShapes
import com.baraa.masroof.ui.theme.FinancialTypography
import com.baraa.masroof.ui.theme.Spacing

@Composable
fun ChartCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    isEmpty: Boolean = false,
    emptyMessage: String = "لا توجد بيانات كافية",
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(Spacing.x4),
            verticalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            Text(title, style = FinancialTypography.sectionTitle, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isEmpty) {
                Text(
                    emptyMessage,
                    style = FinancialTypography.metadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.x6),
                )
            } else {
                content()
            }
        }
    }
}
