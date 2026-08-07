package com.baraa.masroof.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Standard top app bar used on every screen. Default action is back; pass
 * `actions` for context-specific buttons. Always exposes a 48dp back
 * target.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasroofTopAppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    onTransactions: (() -> Unit)? = null,
    onAccounts: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = financialTextStyle(FinancialTextStyle.SectionTitle)) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(Spacing.touch)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                }
            }
        },
        actions = {
            Row {
                onHome?.let { IconButton(onClick = it) { Icon(Icons.Filled.Home, contentDescription = "الرئيسية") } }
                onTransactions?.let { IconButton(onClick = it) { Icon(Icons.Filled.Inbox, contentDescription = "العمليات") } }
                onAccounts?.let { IconButton(onClick = it) { Icon(Icons.Filled.AccountBox, contentDescription = "الحسابات") } }
                onMore?.let { IconButton(onClick = it) { Icon(Icons.Filled.MoreHoriz, contentDescription = "المزيد") } }
                actions()
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

/** Display a BigDecimal with Arabic locale digit grouping and SAR suffix. */
@Composable
fun MoneyValue(
    value: BigDecimal,
    label: String? = null,
    emphasize: Boolean = false,
    isExpense: Boolean? = null,
    modifier: Modifier = Modifier,
) {
    val style = if (emphasize) FinancialTypography.heroValue else FinancialTypography.financialTotal
    val color = when {
        emphasize -> MaterialTheme.colorScheme.onSurface
        isExpense == true && value.signum() < 0 -> SemanticColors.expense()
        isExpense == false && value.signum() > 0 -> SemanticColors.positive()
        else -> MaterialTheme.colorScheme.onSurface
    }
    val formatted = remember(value) {
        NumberFormat.getNumberInstance(Locale("ar", "SA"))
            .apply {
                maximumFractionDigits = 2
                minimumFractionDigits = if (value.stripTrailingZeros().scale() > 0) 2 else 0
            }
            .format(value.abs())
    }
    val sign = when {
        value.signum() < 0 -> "−"
        emphasize && isExpense == true -> ""
        else -> ""
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
        if (label != null) {
            Text(label, style = financialTextStyle(FinancialTextStyle.SupportingLabel), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = "$sign$formatted ريال",
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun HeroBalanceCard(
    label: String,
    amount: BigDecimal,
    monthChange: BigDecimal? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = FinancialShapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(Spacing.x6)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(label, style = financialTextStyle(FinancialTextStyle.SupportingLabel), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(Spacing.x2))
            val formatted = remember(amount) {
                NumberFormat.getNumberInstance(Locale("ar", "SA"))
                    .apply { maximumFractionDigits = 2; minimumFractionDigits = 0 }
                    .format(amount)
            }
            Text("$formatted ريال", style = FinancialTypography.heroValue, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (monthChange != null) {
                Spacer(Modifier.height(Spacing.x2))
                MonthChangeChip(monthChange)
            }
        }
    }
}

@Composable
private fun MonthChangeChip(change: BigDecimal) {
    val negative = change.signum() < 0
    val formatted = remember(change) {
        NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 2 }.format(change.abs())
    }
    val signed = if (negative) "−$formatted" else "+$formatted"
    Surface(
        shape = FinancialShapes.pill,
        color = if (negative) SemanticColors.expenseContainer() else SemanticColors.positiveContainer(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val chipColor = if (negative) SemanticColors.expense() else SemanticColors.positive()
            Text("$signed ريال", style = FinancialTypography.badge, color = chipColor)
            Text("هذا الشهر", style = FinancialTypography.badge, color = chipColor)
        }
    }
}

@Composable
fun FinancialMetric(
    label: String,
    amount: BigDecimal,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier,
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(label, style = financialTextStyle(FinancialTextStyle.SupportingLabel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MoneyValue(amount, emphasize = false)
        }
    }
}

@Composable
fun MonthlySummaryRow(label: String, amount: BigDecimal, isExpense: Boolean? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.x1), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = financialTextStyle(FinancialTextStyle.Merchant), color = MaterialTheme.colorScheme.onSurface)
        MoneyValue(amount, isExpense = isExpense)
    }
}

@Composable
fun InstitutionBadge(name: String, color: Color = MaterialTheme.colorScheme.primaryContainer, onColor: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Surface(shape = FinancialShapes.small, color = color) {
        Text(name, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = FinancialTypography.badge, color = onColor)
    }
}

@Composable
fun AccountBadge(label: String) {
    Surface(shape = FinancialShapes.pill, color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = FinancialTypography.badge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ChannelBadge(label: String) {
    Surface(shape = FinancialShapes.small, color = SemanticColors.brandContainer()) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = FinancialTypography.badge, color = SemanticColors.onBrandContainer())
    }
}

@Composable
fun ReviewBadge(label: String = "يحتاج مراجعة") {
    Surface(shape = FinancialShapes.small, color = SemanticColors.warningContainer()) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = FinancialTypography.badge, color = SemanticColors.warning())
    }
}

@Composable
fun StatusBadge(label: String, color: Color, onColor: Color) {
    Surface(shape = FinancialShapes.small, color = color) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = FinancialTypography.badge, color = onColor)
    }
}

@Composable
fun AttentionBanner(
    title: String,
    description: String? = null,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onAction),
        shape = FinancialShapes.medium,
        color = SemanticColors.warningContainer(),
    ) {
        Row(Modifier.padding(Spacing.x4), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = SemanticColors.warning(),
                modifier = Modifier.padding(end = Spacing.x3),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = FinancialTypography.merchant, color = SemanticColors.warning())
                if (!description.isNullOrBlank()) Text(description, style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.x2))
            TextButton(onClick = onAction) {
                Text(actionLabel, color = SemanticColors.warning())
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = SemanticColors.warning(),
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = Spacing.x4, bottom = Spacing.x2), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = financialTextStyle(FinancialTextStyle.SectionTitle), color = MaterialTheme.colorScheme.onBackground)
        action?.invoke()
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = Icons.Filled.Inbox,
) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.x8),
        verticalArrangement = Arrangement.spacedBy(Spacing.x3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = FinancialShapes.pill, color = MaterialTheme.colorScheme.surfaceVariant) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp).size(28.dp),
                )
            } else {
                Text("مَ", modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = FinancialTypography.merchant, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(title, style = FinancialTypography.merchant, color = MaterialTheme.colorScheme.onSurface)
        Text(body, style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionLabel != null && onAction != null) PrimaryButton(actionLabel, onAction)
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.x6),
        verticalArrangement = Arrangement.spacedBy(Spacing.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = FinancialTypography.merchant, color = MaterialTheme.colorScheme.error)
        if (onRetry != null) SecondaryButton("إعادة المحاولة", onRetry)
    }
}

@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth().height(120.dp), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) { Box(Modifier.fillMaxSize()) }
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = Spacing.touch),
        enabled = enabled,
        shape = FinancialShapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
    ) { Text(label, style = FinancialTypography.button) }
}

@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Spacing.touch),
        enabled = enabled,
        shape = FinancialShapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) { Text(label, style = FinancialTypography.button, color = MaterialTheme.colorScheme.onSurface) }
}

/** Irreversible action — filled error color. */
@Composable
fun DestructiveButton(label: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = Spacing.touch),
        enabled = enabled,
        shape = FinancialShapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) { Text(label, style = FinancialTypography.button) }
}

@Composable
fun DestructiveTextButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) { Text(label) }
}

data class FilterChipModel(val id: String, val label: String, val selected: Boolean = false, val removable: Boolean = true)

@Composable
fun FilterChipRow(
    chips: List<FilterChipModel>,
    onChipClick: (String) -> Unit,
    onChipRemove: ((String) -> Unit)? = null,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
        contentPadding = PaddingValues(horizontal = Spacing.x4),
    ) {
        items(chips) { chip ->
            val displayLabel = if (chip.selected && onChipRemove != null && chip.removable) "${chip.label} ×" else chip.label
            AssistChip(
                onClick = {
                    if (chip.selected && onChipRemove != null && chip.removable) onChipRemove(chip.id) else onChipClick(chip.id)
                },
                label = { Text(displayLabel) },
            )
        }
    }
}

@Composable
fun MonthSelector(
    current: java.time.YearMonth,
    isCurrentMonth: Boolean,
    onPrev: () -> Unit,
    onCurrent: () -> Unit,
    onNext: () -> Unit,
) {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale("ar"))
    Row(
        Modifier.fillMaxWidth().padding(Spacing.x4),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(Spacing.touch)) {
            Icon(Icons.Default.ChevronRight, contentDescription = "الشهر السابق")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(current.format(formatter), style = FinancialTypography.merchant, color = MaterialTheme.colorScheme.onBackground)
            if (!isCurrentMonth) TextButton(onClick = onCurrent) { Text("العودة للشهر الحالي") }
        }
        IconButton(onClick = onNext, enabled = !isCurrentMonth, modifier = Modifier.size(Spacing.touch)) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "الشهر التالي")
        }
    }
}

@Composable
fun ImportSummaryCard(rangeLabel: String, allowedInstitutionCount: Int, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(Spacing.x4)) {
            Text("نطاق الاستيراد", style = FinancialTypography.supportingLabel, color = SemanticColors.secondaryAccent())
            Spacer(Modifier.height(Spacing.x1))
            Text(rangeLabel, style = FinancialTypography.merchant, color = SemanticColors.secondaryAccent())
        }
    }
}

@Composable
fun InstitutionAmountRow(institution: String, ready: Int, needsReview: Int, unparsed: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.x2), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        InstitutionBadge(institution)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            if (ready > 0) StatusBadge("$ready جاهز", SemanticColors.positiveContainer(), SemanticColors.positive())
            if (needsReview > 0) StatusBadge("$needsReview مراجعة", SemanticColors.warningContainer(), SemanticColors.warning())
            if (unparsed > 0) StatusBadge("$unparsed تعذر", SemanticColors.expenseContainer(), SemanticColors.expense())
        }
    }
}

// (size modifier provided by foundation layout)
