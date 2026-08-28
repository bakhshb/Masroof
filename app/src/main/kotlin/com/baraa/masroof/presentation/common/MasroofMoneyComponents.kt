package com.baraa.masroof.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.presentation.theme.MasroofIconSizes
import com.baraa.masroof.presentation.theme.MasroofRowShape
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

@Composable
fun MasroofMoneyRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    style: MasroofMoneyRowStyle = MasroofMoneyRowStyle.Neutral,
    leadingIcon: ImageVector? = null,
) {
    val extended = MasroofThemeExtras.extendedColors
    val (background, borderColor) = when (style) {
        MasroofMoneyRowStyle.Inflow -> extended.inflowSoft to extended.inflowRowBorder
        MasroofMoneyRowStyle.Outflow -> extended.outflowSoft to extended.outflowRowBorder
        MasroofMoneyRowStyle.Highlight -> extended.highlight to extended.highlightBorder
        MasroofMoneyRowStyle.Neutral -> extended.miniBackground to extended.cardBorder
    }
    val valueColor = when (style) {
        MasroofMoneyRowStyle.Inflow -> extended.inflow
        MasroofMoneyRowStyle.Outflow -> extended.outflow
        MasroofMoneyRowStyle.Highlight -> extended.outflow
        MasroofMoneyRowStyle.Neutral -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MasroofRowShape)
            .background(background)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(1.dp, borderColor, MasroofRowShape)
                } else {
                    Modifier
                },
            )
            .padding(
                horizontal = MasroofSpacing.rowHorizontalPadding,
                vertical = MasroofSpacing.rowVerticalPadding,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(MasroofIconSizes.moneyRowLeading),
                    tint = valueColor,
                )
                Spacer(Modifier.width(MasroofSpacing.sectionHeaderGap))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(MasroofSpacing.sectionHeaderGap))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor,
        )
    }
}

@Composable
fun MasroofHintBox(
    text: String,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MasroofRowShape,
        color = extended.hintBackground,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Text(
            text,
            modifier = Modifier.padding(
                horizontal = MasroofSpacing.rowHorizontalPadding,
                vertical = MasroofSpacing.rowVerticalPadding,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
