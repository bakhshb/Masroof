package com.baraa.masroof.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.AccountIdentifierEntity
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.FinancialInstitutionResolver
import com.baraa.masroof.ledger.HistoricalFinancialService
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.ui.theme.AttentionBanner
import com.baraa.masroof.ui.theme.EmptyState
import com.baraa.masroof.ui.theme.FinancialMetric
import com.baraa.masroof.ui.theme.FinancialShapes
import com.baraa.masroof.ui.theme.FinancialTypography
import com.baraa.masroof.ui.theme.HeroBalanceCard
import com.baraa.masroof.ui.theme.LoadingSkeleton
import com.baraa.masroof.ui.theme.MonthlySummaryRow
import com.baraa.masroof.ui.theme.MonthSelector
import com.baraa.masroof.ui.theme.SectionHeader
import com.baraa.masroof.ui.theme.SecondaryButton
import com.baraa.masroof.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Monthly-first home dashboard.
 *
 * Visible at all times:
 *  - Month selector (current month selected by default; future month disabled)
 *  - Hero card with liquidity for the selected month
 *  - Two secondary metrics (net worth + card liabilities)
 *  - Conditional monthly summary (skips zero-valued rows)
 *  - Conditional attention section (only when there is something to attend to)
 *  - Last five transactions row by row
 */
@Composable
fun HomeScreen(
    onImportMessages: () -> Unit = {},
    onShowAllTransactions: () -> Unit = {},
    onOpenReview: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    var accounts by remember { mutableStateOf(emptyList<FinancialAccount>()) }
    var identifiers by remember { mutableStateOf(emptyMap<Long, List<AccountIdentifierEntity>>()) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var endLiquidity by remember { mutableStateOf<BigDecimal?>(null) }
    var startLiquidity by remember { mutableStateOf(BigDecimal.ZERO) }
    var endNetWorth by remember { mutableStateOf<BigDecimal?>(null) }
    var endLiabilities by remember { mutableStateOf(BigDecimal.ZERO) }
    var movement by remember { mutableStateOf<com.baraa.masroof.ledger.DailyFinancialMovement?>(null) }
    var loading by remember { mutableStateOf(true) }
    var recent by remember { mutableStateOf<List<TransactionEntity>>(emptyList()) }
    var reviewCount by remember { mutableStateOf(0) }
    var beforeTrackingCount by remember { mutableStateOf(0) }
    var trackingStartLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { app.financialAccountRepository.observeAll().collect { accounts = it } }
    LaunchedEffect(Unit) {
        app.transactionRepository.observeAll().collect { list ->
            recent = list.sortedByDescending { it.smsTimestamp }.take(5)
            reviewCount = list.count { it.needsReview }
            beforeTrackingCount = list.count { it.exclusionReason?.contains("بداية المتابعة") == true }
        }
    }
    LaunchedEffect(Unit) {
        val setup = runCatching { app.financialSetupRepository.load() }.getOrNull()
        if (setup != null) {
            val start = java.time.Instant.ofEpochMilli(setup.trackingStartDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            trackingStartLabel = "بداية المتابعة: $start"
        }
    }
    LaunchedEffect(accounts) {
        if (accounts.isNotEmpty()) {
            app.accountIdentifierRepository.observeAll().collect { allIdentifiers ->
                identifiers = allIdentifiers.groupBy { it.accountId }
            }
        }
    }
    LaunchedEffect(accounts, month) {
        if (accounts.isNotEmpty()) {
            val journals = runCatching { withContext(Dispatchers.IO) { app.database.journalDao().getPostedThrough(month.atEndOfMonth()) } }.getOrDefault(emptyList())
            val history = HistoricalFinancialService.calculateMonth(month, accounts, journals)
            val todaySummary = history.daily.values.lastOrNull()
            endLiquidity = todaySummary?.endOfDayLiquidity
            startLiquidity = todaySummary?.startOfDayLiquidity ?: BigDecimal.ZERO
            endNetWorth = todaySummary?.endOfDayNetWorth
            endLiabilities = todaySummary?.endOfDayLiabilities ?: BigDecimal.ZERO
            movement = todaySummary?.movement
            loading = false
        }
    }

    val monthChange = (endLiquidity ?: BigDecimal.ZERO) - startLiquidity
    val isCurrentMonth = month == YearMonth.now()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
        item { MonthSelector(current = month, isCurrentMonth = isCurrentMonth, onPrev = { month = month.minusMonths(1) }, onCurrent = { month = YearMonth.now() }, onNext = { /* future months disabled per spec */ }) }
        item {
            if (endLiquidity == null) LoadingSkeleton(Modifier.height(160.dp))
            else HeroBalanceCard(label = "السيولة المتاحة", amount = endLiquidity ?: BigDecimal.ZERO, monthChange = monthChange)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                FinancialMetric(label = "صافي الثروة", amount = endNetWorth ?: BigDecimal.ZERO, modifier = Modifier.weight(1f))
                FinancialMetric(label = "إجمالي التزامات البطاقات", amount = endLiabilities, modifier = Modifier.weight(1f))
            }
        }
        val m = movement
        if (m != null && listOf(m.income, m.expenses, m.refunds, m.bankFees, m.investments).any { row -> row.signum() != 0 }) {
            item { SectionHeader("ملخص الشهر") }
            item {
                Surface(Modifier.fillMaxWidth(), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(Spacing.x4)) {
                        if (m.income.signum() > 0) MonthlySummaryRow("الدخل", m.income, isExpense = false)
                        if (m.expenses.signum() > 0) MonthlySummaryRow("المصروفات", m.expenses, isExpense = true)
                        if (m.refunds.signum() > 0) MonthlySummaryRow("الاستردادات", m.refunds, isExpense = false)
                        if (m.bankFees.signum() > 0) MonthlySummaryRow("الرسوم البنكية", m.bankFees, isExpense = true)
                        if (m.investments.signum() > 0) MonthlySummaryRow("الاستثمارات", m.investments, isExpense = true)
                        MonthlySummaryRow("صافي التغير", m.netCashMovement, isExpense = m.netCashMovement.signum() < 0)
                    }
                }
            }
        }
        if (reviewCount > 0 || beforeTrackingCount > 0) {
            item { SectionHeader("يحتاج انتباهك") }
            if (reviewCount > 0) item { AttentionBanner(title = "عمليات تحتاج مراجعة", description = "بعض العمليات لم تكتمل معالجتها بعد.", actionLabel = "فتح", onAction = onOpenReview) }
            if (beforeTrackingCount > 0) item { AttentionBanner(title = "عمليات قبل تاريخ بداية المتابعة", description = "هذه العمليات لم تُحتسب في أرصدتك.", actionLabel = "عرض", onAction = onOpenReview) }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.x4, bottom = Spacing.x2), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
                )
            }
        } else {
            items(recent, key = { it.id }) { tx ->
                TransactionRow(
                    presentation = tx.toPresentation(identifiers = identifiers[resolveAccountId(tx, accounts)] ?: emptyList()),
                    onClick = onOpenReview,
                )
            }
        }
        trackingStartLabel?.let { item { Text(it, modifier = Modifier.padding(top = Spacing.x4), style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}

private fun resolveAccountId(tx: TransactionEntity, accounts: List<FinancialAccount>): Long? {
    if (tx.sourceAccountId != null) return tx.sourceAccountId
    if (tx.destinationAccountId != null) return tx.destinationAccountId
    val lastFour = tx.accountOrCardLastFourDigits?.takeLast(4) ?: return null
    return accounts.firstOrNull { it.lastFourDigits?.takeLast(4) == lastFour }?.id
}

private fun TransactionEntity.toPresentation(identifiers: List<AccountIdentifierEntity>): TransactionPresentation {
    val account = identifiers.firstOrNull() // first visible safe identifier
    val institutionDisplayName = account?.displayLabel ?: "مرسل مالي غير معروف"
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
