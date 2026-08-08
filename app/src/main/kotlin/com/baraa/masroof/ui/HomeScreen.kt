package com.baraa.masroof.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.baraa.masroof.ledger.TransactionPostingStatus
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.FinancialTreatment
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
import com.baraa.masroof.ui.theme.MoneyValue
import com.baraa.masroof.ui.theme.MonthSelector
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.SecondaryButton
import com.baraa.masroof.ui.theme.SectionHeader
import com.baraa.masroof.ui.theme.SemanticColors
import com.baraa.masroof.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Monthly-first home dashboard with composition donut and daily spend columns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onImportMessages: () -> Unit = {},
    onShowAllTransactions: () -> Unit = {},
    onOpenReview: () -> Unit = {},
    onBankMessages: () -> Unit = {},
    onTransactionClick: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication

    val accounts by app.financialAccountRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val identifiers by app.accountIdentifierRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val transactions by app.transactionRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val setup by app.financialSetupRepository.observe().collectAsStateWithLifecycle(initialValue = null)
    val unknownPatterns by app.messagePatternRepository.observeUnknown().collectAsStateWithLifecycle(initialValue = emptyList())

    val patternCtaPrefs = remember {
        context.getSharedPreferences("masroof_home_cta_prefs", android.content.Context.MODE_PRIVATE)
    }
    var patternsCtaDismissed by remember {
        mutableStateOf(patternCtaPrefs.getBoolean("patterns_setup_cta_dismissed", false))
    }
    var hasApprovedPatterns by remember { mutableStateOf(true) }
    LaunchedEffect(accounts) {
        hasApprovedPatterns = withContext(Dispatchers.IO) {
            app.messagePatternRepository.senderProfileIdsWithApprovedPatterns().isNotEmpty()
        }
    }
    val ownedAccounts = remember(accounts) {
        accounts.filter { it.isOwnedByUser && it.isActive && it.systemAccountKey == null }
    }
    val showPatternsSetupCta = ownedAccounts.isNotEmpty() && !hasApprovedPatterns && !patternsCtaDismissed

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

    val trackingStartDate: LocalDate? = remember(setup) {
        val s = setup ?: return@remember null
        java.time.Instant.ofEpochMilli(s.trackingStartDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    }

    val monthHistory by produceMonthHistory(app, month)
    val journals = remember(monthHistory) { monthHistory?.daily?.values?.toList().orEmpty() }

    val identifiersByAccount = remember(identifiers) { identifiers.groupBy { it.accountId } }
    val recent = remember(transactions) {
        transactions.sortedWith(
            compareByDescending<com.baraa.masroof.data.db.TransactionEntity> {
                com.baraa.masroof.ui.transactions.TransactionSearchEngine.effectiveFinancialTime(it)
            }.thenByDescending { it.id },
        ).take(5)
    }

    val reviewCount = remember(transactions) { transactions.count { it.needsReview } }
    val beforeTrackingCount = remember(transactions) { transactions.count { it.exclusionReason?.contains("بداية المتابعة") == true } }

    val monthSummary = if (accounts.isNotEmpty()) monthHistory?.monthMovement() else null

    val endLiquidity = remember(journals) { journals.lastOrNull()?.endOfDayLiquidity }
    val startLiquidity = remember(journals) { journals.firstOrNull()?.startOfDayLiquidity ?: BigDecimal.ZERO }
    val endNetWorth = remember(journals) { journals.lastOrNull()?.endOfDayNetWorth }
    val journalVersion by app.database.journalDao().observePosted().collectAsStateWithLifecycle(initialValue = emptyList())
    var creditCards by remember { mutableStateOf<List<CreditCardHomeSummary>>(emptyList()) }
    LaunchedEffect(accounts, journalVersion.size) {
        creditCards = withContext(Dispatchers.IO) {
            val cards = accounts.filter {
                it.isActive &&
                    it.isOwnedByUser &&
                    it.systemAccountKey == null &&
                    it.accountType == AccountType.CREDIT_CARD
            }
            if (cards.isEmpty()) return@withContext emptyList()
            val posted = app.database.journalDao().getAllForRecalculation()
            cards.map { card ->
                val outstanding = com.baraa.masroof.ledger.AccountBalanceService.balance(
                    account = card,
                    journals = posted,
                    asOfDate = LocalDate.now(),
                )
                val limit = card.creditLimit
                val available = if (limit != null && limit.signum() > 0) {
                    limit.subtract(outstanding).coerceAtLeast(BigDecimal.ZERO)
                } else {
                    null
                }
                CreditCardHomeSummary(
                    accountId = card.id,
                    name = card.displayName,
                    outstanding = outstanding,
                    creditLimit = limit,
                    availableCredit = available,
                )
            }
        }
    }
    val creditCardLiabilities = remember(creditCards) {
        creditCards.fold(BigDecimal.ZERO) { acc, card -> acc + card.outstanding }
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
    val spendDays = remember(expenseSeries, month) {
        expenseSeries.filter { it.value.signum() > 0 }.map { month.atDay(it.dayOfMonth) }
    }
    val dayTransactions = remember(selectedDay, transactions) {
        selectedDay?.let { day -> spendingTransactionsForDay(transactions, day) }.orEmpty()
    }
    val dayTotal = remember(dayTransactions) {
        dayTransactions.fold(BigDecimal.ZERO) { acc, tx -> acc + (tx.amount ?: BigDecimal.ZERO).abs() }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
        if (showPatternsSetupCta) {
            item {
                AttentionBanner(
                    title = "أنشئ نمطاً قبل الاستيراد",
                    description = "لا يمكن استيراد رسائل البنوك قبل حفظ نمط معتمد من «رسائل البنوك».",
                    actionLabel = "رسائل البنوك",
                    onAction = onBankMessages,
                )
            }
            item {
                SecondaryButton(
                    label = "إخفاء",
                    onClick = {
                        patternsCtaDismissed = true
                        patternCtaPrefs.edit().putBoolean("patterns_setup_cta_dismissed", true).apply()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else if (unknownPatterns.isNotEmpty()) {
            item {
                AttentionBanner(
                    title = "تم العثور على نوع رسالة جديد",
                    description = "راجع الأنماط غير المعروفة في «رسائل البنوك» قبل الاستيراد التالي.",
                    actionLabel = "مراجعة",
                    onAction = onBankMessages,
                )
            }
        }
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
                    amount = creditCardLiabilities,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.CreditCard,
                )
            }
        }
        if (creditCards.isNotEmpty()) {
            item {
                CreditCardsLimitSection(cards = creditCards)
            }
        }
        if (accounts.isNotEmpty() && donutSlices.isEmpty() && !expenseSeriesHasData) {
            item {
                ImportGuidanceCard(
                    hasReviewItems = reviewCount > 0,
                    onImport = onImportMessages,
                    onReview = onOpenReview,
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
                    subtitle = "اختر يومًا لعرض تفاصيل ما صرفت فيه",
                    isEmpty = !expenseSeriesHasData,
                    emptyMessage = "لا توجد مصروفات مسجّلة في هذا الشهر",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
                        DailyTrendColumnChart(
                            points = expenseSeries,
                            columnColorArgb = SemanticColors.expense().toArgb(),
                        )
                        if (spendDays.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
                            ) {
                                spendDays.forEach { day ->
                                    val selected = selectedDay == day
                                    FilterChip(
                                        selected = selected,
                                        onClick = { selectedDay = day },
                                        label = {
                                            Text("${day.dayOfMonth}")
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (reviewCount > 0 || beforeTrackingCount > 0) {
            item { SectionHeader("يحتاج انتباهك") }
            if (reviewCount > 0) item {
                AttentionBanner(
                    title = "عمليات تحتاج مراجعة ($reviewCount)",
                    description = "لن يتغيّر الرصيد ولن تظهر المخططات حتى تؤكد ربط هذه العمليات بالحسابات.",
                    actionLabel = "فتح",
                    onAction = onOpenReview,
                )
            }
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
                    onClick = { onTransactionClick(tx.id) },
                )
            }
        }
        trackingStartDate?.let { date ->
            item {
                TrackingStartSummary(date)
            }
        }
    }

    val sheetDay = selectedDay
    if (sheetDay != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val dayFmt = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ar")) }
        ModalBottomSheet(
            onDismissRequest = { selectedDay = null },
            sheetState = sheetState,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.x4)
                    .padding(bottom = Spacing.x6),
                verticalArrangement = Arrangement.spacedBy(Spacing.x3),
            ) {
                Text(
                    "مصروفات ${sheetDay.format(dayFmt)}",
                    style = FinancialTypography.sectionTitle,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("الإجمالي", style = FinancialTypography.supportingLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyValue(dayTotal, isExpense = true)
                }
                if (dayTransactions.isEmpty()) {
                    Text(
                        "لا توجد عمليات مصروف مسجّلة في هذا اليوم.",
                        style = FinancialTypography.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.x2),
                    ) {
                        items(dayTransactions, key = { it.id }) { tx ->
                            TransactionRow(
                                presentation = tx.toPresentation(
                                    identifiers = identifiersByAccount[resolveAccountId(tx, accounts)] ?: emptyList(),
                                ),
                                onClick = {
                                    selectedDay = null
                                    onTransactionClick(tx.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditCardsLimitSection(cards: List<CreditCardHomeSummary>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        SectionHeader("البطاقات الائتمانية")
        cards.forEach { card ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = FinancialShapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    Modifier.padding(Spacing.x4),
                    verticalArrangement = Arrangement.spacedBy(Spacing.x2),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(card.name, style = FinancialTypography.merchant)
                        Text("فيزا / ائتمان", style = FinancialTypography.badge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
                    ) {
                        LimitMetric(label = "المستحق", amount = card.outstanding, modifier = Modifier.weight(1f), expenseTint = true)
                        LimitMetric(
                            label = "الحد الائتماني",
                            amount = card.creditLimit,
                            modifier = Modifier.weight(1f),
                            missingHint = "غير محدّد",
                        )
                        LimitMetric(
                            label = "المتاح",
                            amount = card.availableCredit,
                            modifier = Modifier.weight(1f),
                            missingHint = "—",
                        )
                    }
                    if (card.creditLimit == null || card.creditLimit.signum() <= 0) {
                        Text(
                            "حد البطاقة غير محفوظ بعد. سيُحدَّث تلقائيًا من رسالة «تغيير حد الرصيد»، أو أدخله من الحسابات.",
                            style = FinancialTypography.metadata,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LimitMetric(
    label: String,
    amount: BigDecimal?,
    modifier: Modifier = Modifier,
    expenseTint: Boolean = false,
    missingHint: String = "—",
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
        Text(label, style = FinancialTypography.supportingLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (amount != null) {
            MoneyValue(amount, isExpense = if (expenseTint) true else null, emphasize = false)
        } else {
            Text(missingHint, style = FinancialTypography.merchant, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ImportGuidanceCard(
    hasReviewItems: Boolean,
    onImport: () -> Unit,
    onReview: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            Text("لم تُستورد عمليات بعد", style = FinancialTypography.sectionTitle, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                "الرصيد الظاهر هو رصيدك الافتتاحي فقط. ليتطابق مع البنك تقريباً:",
                style = FinancialTypography.metadata,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text("1. أنشئ أنماطاً معتمدة من «رسائل البنوك».", style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text("2. أضف معرف الحساب (آخر 4 أرقام) يدوياً.", style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text("3. استورد الرسائل المطابقة للأنماط فقط.", style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSecondaryContainer)
            PrimaryButton(label = "استيراد الرسائل", onClick = onImport, modifier = Modifier.fillMaxWidth())
            if (hasReviewItems) {
                SecondaryButton(label = "فتح قائمة المراجعة", onClick = onReview, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TrackingStartSummary(date: LocalDate) {
    val fmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ar"))
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

private data class CreditCardHomeSummary(
    val accountId: Long,
    val name: String,
    val outstanding: BigDecimal,
    val creditLimit: BigDecimal?,
    val availableCredit: BigDecimal?,
)

/** Spending rows for a calendar day — matches the daily expense chart buckets. */
internal fun spendingTransactionsForDay(
    transactions: List<TransactionEntity>,
    day: LocalDate,
): List<TransactionEntity> {
    val spendTreatments = setOf(
        FinancialTreatment.EXPENSE,
        FinancialTreatment.BANK_FEE,
        FinancialTreatment.CASH_WITHDRAWAL,
    )
    return transactions
        .filter { tx ->
            tx.transactionDate == day &&
                tx.financialTreatment in spendTreatments &&
                tx.postingStatus != TransactionPostingStatus.VOIDED &&
                !tx.needsReview
        }
        .sortedByDescending { it.smsTimestamp }
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
        isExpense = financialTreatment.name in listOf("EXPENSE", "BANK_FEE", "CASH_WITHDRAWAL"),
        merchantOrLabel = merchantOrBeneficiary?.takeIf { it.isNotBlank() } ?: TransactionPresentationFactory.friendlyTransactionType(transactionType),
        friendlyType = TransactionPresentationFactory.friendlyTransactionType(transactionType),
        institutionDisplayName = institutionDisplayName,
        institutionSource = com.baraa.masroof.ledger.InstitutionIdentificationSource.ACCOUNT_SENDER_PROFILE,
        accountOrInstrumentLabel = accountOrCardLastFourDigits?.let { "•••• ${it.takeLast(4)}" } ?: "غير مرتبط بحساب",
        channelLabel = when (transactionType) {
            TransactionType.ONLINE_PURCHASE -> "عبر الإنترنت"
            TransactionType.PURCHASE -> "نقاط البيع"
            else -> null
        },
        currency = currency.name,
        dateLabel = transactionDate?.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ar"))) ?: "—",
        requiresReview = needsReview,
        needsAttention = exclusionReason != null && needsReview,
        exclusionReason = exclusionReason,
        isBeforeTrackingStart = exclusionReason?.contains("بداية المتابعة") == true,
    )
}
