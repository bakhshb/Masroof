package com.baraa.masroof.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.AccountIdentifierEntity
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.HistoricalFinancialService
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.ui.charts.ChartCard
import com.baraa.masroof.ui.charts.ChartMappers
import com.baraa.masroof.ui.charts.DailyTrendColumnChart
import com.baraa.masroof.ui.charts.DonutChart
import com.baraa.masroof.ui.theme.AttentionBanner
import com.baraa.masroof.ui.theme.EmptyState
import com.baraa.masroof.ui.theme.FinancialMetric
import com.baraa.masroof.ui.theme.FinancialShapes
import com.baraa.masroof.ui.theme.FinancialTypography
import com.baraa.masroof.ui.theme.HeroBalanceCard
import com.baraa.masroof.ui.theme.LoadingSkeleton
import com.baraa.masroof.ui.theme.MonthSelector
import com.baraa.masroof.ui.theme.SecondaryButton
import com.baraa.masroof.ui.theme.SectionHeader
import com.baraa.masroof.ui.theme.SemanticColors
import com.baraa.masroof.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Monthly-first home dashboard with composition donut and daily spend columns.
 */
@Composable
fun HomeScreen(
    onImportMessages: () -> Unit = {},
    onShowAllTransactions: () -> Unit = {},
    onOpenReview: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication

    val accounts by app.financialAccountRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val identifiers by app.accountIdentifierRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val transactions by app.transactionRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val setup by app.financialSetupRepository.observe().collectAsStateWithLifecycle(initialValue = null)

    var month by remember { mutableStateOf(YearMonth.now()) }

    val trackingStartDate: LocalDate? = remember(setup) {
        val s = setup ?: return@remember null
        java.time.Instant.ofEpochMilli(s.trackingStartDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    }

    val monthHistory by produceMonthHistory(app, month)
    val journals = remember(monthHistory) { monthHistory?.daily?.values?.toList().orEmpty() }

    val identifiersByAccount = remember(identifiers) { identifiers.groupBy { it.accountId } }
    val recent = remember(transactions) { transactions.sortedByDescending { it.smsTimestamp }.take(5) }

    val reviewCount = remember(transactions) { transactions.count { it.needsReview } }
    val beforeTrackingCount = remember(transactions) { transactions.count { it.exclusionReason?.contains("بداية المتابعة") == true } }

    val monthSummary = if (accounts.isNotEmpty()) monthHistory?.monthMovement() else null

    val endLiquidity = remember(journals) { journals.lastOrNull()?.endOfDayLiquidity }
    val startLiquidity = remember(journals) { journals.firstOrNull()?.startOfDayLiquidity ?: BigDecimal.ZERO }
    val endNetWorth = remember(journals) { journals.lastOrNull()?.endOfDayNetWorth }
    val endLiabilities = remember(journals) {
        journals.lastOrNull()?.accounts
            ?.filter {
                it.trackingStatus == com.baraa.masroof.ledger.HistoricalTrackingStatus.TRACKED &&
                    it.accountNature == com.baraa.masroof.transaction.AccountNature.LIABILITY &&
                    it.accountType == com.baraa.masroof.transaction.AccountType.CREDIT_CARD &&
                    it.includedInNetWorth
            }
            ?.fold(BigDecimal.ZERO) { acc, row -> acc + row.endOfDayBalance }
            ?: BigDecimal.ZERO
    }
    val monthChange = (endLiquidity ?: BigDecimal.ZERO) - startLiquidity
    val isCurrentMonth = month == YearMonth.now()

    val isDark = MaterialTheme.colorScheme.background.let {
        0.2126f * it.red + 0.7152f * it.green + 0.0722f * it.blue < 0.5f
    }
    val palette = if (isDark) ChartMappers.SeriesPalette.Dark else ChartMappers.SeriesPalette.Light
    val donutSlices = remember(monthSummary, palette) {
        monthSummary?.let { ChartMappers.monthMovementSlices(it, palette) }.orEmpty()
    }
    val expenseSeries = remember(monthHistory) {
        monthHistory?.let { ChartMappers.dailyExpenseSeries(it) }.orEmpty()
    }
    val expenseSeriesHasData = ChartMappers.hasNonZero(expenseSeries)

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
        item {
            MonthSelector(
                current = month,
                isCurrentMonth = isCurrentMonth,
                onPrev = { month = month.minusMonths(1) },
                onCurrent = { month = YearMonth.now() },
                onNext = {
                    val next = month.plusMonths(1)
                    if (!next.isAfter(YearMonth.now())) month = next
                },
            )
        }
        item {
            if (endLiquidity == null) LoadingSkeleton(Modifier.height(160.dp))
            else HeroBalanceCard(
                label = "السيولة المتاحة",
                amount = endLiquidity,
                monthChange = monthChange,
                icon = Icons.Filled.AccountBalanceWallet,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                FinancialMetric(
                    label = "صافي الثروة",
                    amount = endNetWorth ?: BigDecimal.ZERO,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.TrendingUp,
                )
                FinancialMetric(
                    label = "التزامات البطاقات",
                    amount = endLiabilities,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.CreditCard,
                )
            }
        }
        if (donutSlices.isNotEmpty()) {
            item {
                ChartCard(
                    title = "توزيع حركة الشهر",
                    subtitle = "ملخص الدخل والمصروفات لهذا الشهر",
                ) {
                    DonutChart(
                        slices = donutSlices,
                        centerLabel = "صافي التغير",
                        centerValue = monthSummary?.netCashMovement,
                    )
                }
            }
        }
        if (monthHistory != null) {
            item {
                ChartCard(
                    title = "المصروفات اليومية",
                    subtitle = "اتجاه الإنفاق خلال أيام الشهر",
                    isEmpty = !expenseSeriesHasData,
                    emptyMessage = "لا توجد مصروفات مسجّلة في هذا الشهر",
                ) {
                    DailyTrendColumnChart(
                        points = expenseSeries,
                        columnColorArgb = SemanticColors.expense().toArgb(),
                    )
                }
            }
        }
        if (reviewCount > 0 || beforeTrackingCount > 0) {
            item { SectionHeader("يحتاج انتباهك") }
            if (reviewCount > 0) item { AttentionBanner(title = "عمليات تحتاج مراجعة", description = "بعض العمليات لم تكتمل معالجتها بعد.", actionLabel = "فتح", onAction = onOpenReview) }
            if (beforeTrackingCount > 0) item { AttentionBanner(title = "عمليات قبل تاريخ بداية المتابعة", description = "هذه العمليات لم تُحتسب في أرصدتك.", actionLabel = "عرض", onAction = onOpenReview) }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.x4, bottom = Spacing.x2), horizontalArrangement = Arrangement.SpaceBetween) {
                SectionHeader("آخر العمليات")
                SecondaryButton(label = "عرض الكل", onClick = onShowAllTransactions)
            }
        }
        if (recent.isEmpty()) {
            item {
                EmptyState(
                    title = "لا توجد عمليات حتى الآن",
                    body = "استورد رسائل البنك لبدء متابعة مصروفاتك تلقائيًا.",
                    actionLabel = "استيراد الرسائل",
                    onAction = onImportMessages,
                    icon = Icons.Filled.CloudDownload,
                )
            }
        } else {
            items(recent, key = { it.id }) { tx ->
                TransactionRow(
                    presentation = tx.toPresentation(identifiers = identifiersByAccount[resolveAccountId(tx, accounts)] ?: emptyList()),
                    onClick = onOpenReview,
                )
            }
        }
        trackingStartDate?.let { date ->
            item {
                TrackingStartSummary(date)
            }
        }
    }
}

@Composable
private fun TrackingStartSummary(date: LocalDate) {
    val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("ar"))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            Text("تاريخ الرصيد الافتتاحي", style = FinancialTypography.supportingLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("الرصيد الافتتاحي في ${date.format(fmt)}", style = FinancialTypography.merchant)
            Text("هو التاريخ الذي يمثّل الرصيد الذي أدخلته للحساب. تُحتسب العمليات اللاحقة له للوصول إلى رصيد اليوم.", style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun produceMonthHistory(
    app: MasroofApplication,
    month: YearMonth,
): androidx.compose.runtime.State<com.baraa.masroof.ledger.MonthlyFinancialHistory?> {
    val state = remember { mutableStateOf<com.baraa.masroof.ledger.MonthlyFinancialHistory?>(null) }
    val accounts by app.financialAccountRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val journalVersion by app.database.journalDao().observePosted().collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(accounts, month, journalVersion.size) {
        if (accounts.isEmpty()) {
            state.value = null
        } else {
            runCatching {
                val journals = withContext(Dispatchers.IO) { app.database.journalDao().getPostedThrough(month.atEndOfMonth()) }
                state.value = HistoricalFinancialService.calculateMonth(month, accounts, journals)
            }.onFailure {
                state.value = null
            }
        }
    }
    return state
}

private fun resolveAccountId(tx: TransactionEntity, accounts: List<FinancialAccount>): Long? {
    if (tx.sourceAccountId != null) return tx.sourceAccountId
    if (tx.destinationAccountId != null) return tx.destinationAccountId
    return null
}

private fun TransactionEntity.toPresentation(identifiers: List<AccountIdentifierEntity>): TransactionPresentation {
    val institutionDisplayName = identifiers.firstOrNull()?.displayLabel ?: "مرسل مالي غير معروف"
    return TransactionPresentation(
        transactionId = id,
        amount = amount ?: BigDecimal.ZERO,
        amountLabel = amount?.let { "${it.toPlainString()} ${currency.name}" } ?: "—",
        isExpense = financialTreatment.name in listOf("EXPENSE", "BANK_FEE"),
        merchantOrLabel = merchantOrBeneficiary?.takeIf { it.isNotBlank() } ?: TransactionPresentationFactory.friendlyTransactionType(transactionType),
        friendlyType = TransactionPresentationFactory.friendlyTransactionType(transactionType),
        institutionDisplayName = institutionDisplayName,
        institutionSource = com.baraa.masroof.ledger.InstitutionIdentificationSource.ACCOUNT_SENDER_ALIAS,
        accountOrInstrumentLabel = accountOrCardLastFourDigits?.let { "•••• ${it.takeLast(4)}" } ?: "غير مرتبط بحساب",
        channelLabel = when (transactionType) {
            TransactionType.ONLINE_PURCHASE -> "عبر الإنترنت"
            TransactionType.PURCHASE -> "نقاط البيع"
            else -> null
        },
        currency = currency.name,
        dateLabel = transactionDate?.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("ar"))) ?: "—",
        requiresReview = needsReview,
        needsAttention = exclusionReason != null && needsReview,
        exclusionReason = exclusionReason,
        isBeforeTrackingStart = exclusionReason?.contains("بداية المتابعة") == true,
    )
}
