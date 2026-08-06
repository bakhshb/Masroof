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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.AccountIdentifierEntity
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
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
import com.baraa.masroof.ui.theme.SecondaryButton
import com.baraa.masroof.ui.theme.SectionHeader
import com.baraa.masroof.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Monthly-first home dashboard.
 *
 * Reactive sources (each is a Room-backed Flow):
 *  - financialAccountRepository.observeAll()
 *  - accountIdentifierRepository.observeAll()
 *  - transactionRepository.observeAll()
 *  - financialSetupRepository.observe()  → controls `trackingStartDate` display
 *  - journalDao.observePosted()         → triggers balance recompute on import
 *
 * Note: NO `remember { mutableStateOf(BigDecimal.ZERO) }` cached values for
 * money. Each visible number is derived live from Room via
 * [derivedStateOf]; when a posting lands, recomposition is automatic.
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

    // The dashboard listens for new posted journals. When at least one
    // posted journal exists that affects the selected month, we
    // recompute balances against the live journal list.
    val journals by produceJournalsForMonth(app, month)

    val identifiersByAccount = remember(identifiers) { identifiers.groupBy { it.accountId } }
    val recent = remember(transactions) { transactions.sortedByDescending { it.smsTimestamp }.take(5) }

    val reviewCount = remember(transactions) { transactions.count { it.needsReview } }
    val beforeTrackingCount = remember(transactions) { transactions.count { it.exclusionReason?.contains("بداية المتابعة") == true } }

    val todaySummary = if (accounts.isNotEmpty()) {
        val daily = journals.lastOrNull()
        com.baraa.masroof.ledger.DailyFinancialMovement(
            income = daily?.movement?.income ?: BigDecimal.ZERO,
            expenses = daily?.movement?.expenses ?: BigDecimal.ZERO,
            refunds = daily?.movement?.refunds ?: BigDecimal.ZERO,
            bankFees = daily?.movement?.bankFees ?: BigDecimal.ZERO,
            investments = daily?.movement?.investments ?: BigDecimal.ZERO,
            netCashMovement = daily?.movement?.netCashMovement ?: BigDecimal.ZERO,
        )
    } else null

    val endLiquidity = remember(journals) { journals.lastOrNull()?.endOfDayLiquidity }
    val startLiquidity = remember(journals) { journals.lastOrNull()?.startOfDayLiquidity ?: BigDecimal.ZERO }
    val endNetWorth = remember(journals) { journals.lastOrNull()?.endOfDayNetWorth }
    val endLiabilities = remember(journals) { journals.lastOrNull()?.endOfDayLiabilities ?: BigDecimal.ZERO }
    val monthChange = (endLiquidity ?: BigDecimal.ZERO) - startLiquidity
    val isCurrentMonth = month == YearMonth.now()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
        item { MonthSelector(current = month, isCurrentMonth = isCurrentMonth, onPrev = { month = month.minusMonths(1) }, onCurrent = { month = YearMonth.now() }, onNext = { }) }
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
        val m = todaySummary
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
private fun produceJournalsForMonth(app: MasroofApplication, month: YearMonth): androidx.compose.runtime.State<List<com.baraa.masroof.ledger.HistoricalFinancialSummary>> {
    val state = remember { mutableStateOf(emptyList<com.baraa.masroof.ledger.HistoricalFinancialSummary>()) }
    val accounts by app.financialAccountRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    // Observe posted-journal Flow so new imports trigger an immediate
    // balance refresh without depending on account-table changes.
    val journalVersion by app.database.journalDao().observePosted().collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(accounts, month, journalVersion.size) {
        if (accounts.isEmpty()) {
            state.value = emptyList()
        } else {
            runCatching {
                val journals = withContext(Dispatchers.IO) { app.database.journalDao().getPostedThrough(month.atEndOfMonth()) }
                state.value = HistoricalFinancialService.calculateMonth(month, accounts, journals).daily.values.toList()
            }.onFailure {
                state.value = emptyList()
            }
        }
    }
    return state
}

private fun resolveAccountId(tx: TransactionEntity, accounts: List<FinancialAccount>): Long? {
    if (tx.sourceAccountId != null) return tx.sourceAccountId
    if (tx.destinationAccountId != null) return tx.destinationAccountId
    // Presentation follows the persisted account link only. Legacy
    // FinancialAccount.lastFourDigits is migration compatibility data, never
    // account-matching evidence.
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
