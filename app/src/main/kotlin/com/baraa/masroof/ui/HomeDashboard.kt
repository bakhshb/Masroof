package com.baraa.masroof.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.ledger.AccountBalanceService
import com.baraa.masroof.ledger.HistoricalFinancialService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HomeDashboard() {
    val app = LocalContext.current.applicationContext as MasroofApplication
    var accounts by remember { mutableStateOf(emptyList<com.baraa.masroof.data.db.FinancialAccount>()) }
    var transactions by remember { mutableStateOf(emptyList<com.baraa.masroof.data.db.TransactionEntity>()) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { app.financialAccountRepository.observeAll().collectLatest { accounts = it } }
    LaunchedEffect(Unit) { app.transactionRepository.observeAll().collectLatest { transactions = it; loading = false } }
    val monthResult = remember(accounts, transactions, month) {
        runCatching {
            kotlinx.coroutines.runBlocking { withContext(Dispatchers.Default) {
                val journals = app.database.journalDao().getPostedThrough(month.atEndOfMonth())
                HistoricalFinancialService.calculateMonth(month, accounts, journals)
            } }
        }.getOrNull()
    }
    val today = month.atDay(LocalDate.now().dayOfMonth.coerceAtMost(month.lengthOfMonth()))
    val day = monthResult?.daily?.get(today)
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("الرئيسية") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale("ar"))), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { if (month > YearMonth.now()) month = month.minusMonths(1) }) { Text("الشهر التالي") }
                        OutlinedButton(onClick = { month = YearMonth.now() }) { Text("الشهر الحالي") }
                        OutlinedButton(onClick = { month = month.minusMonths(1) }) { Text("الشهر السابق") }
                    }
                }
            }
            item { MoneyValue(day?.endOfDayLiquidity ?: java.math.BigDecimal.ZERO, label = "السيولة المتاحة", emphasize = true) }
            item { MoneyValue(day?.endOfDayNetWorth ?: java.math.BigDecimal.ZERO, label = "صافي الثروة") }
            item { SectionHeader("ملخص الشهر") }
            item { FinancialSummaryCard("الدخل", day?.movement?.income ?: java.math.BigDecimal.ZERO) }
            item { FinancialSummaryCard("المصروفات", day?.movement?.expenses ?: java.math.BigDecimal.ZERO) }
            item { FinancialSummaryCard("الاستردادات", day?.movement?.refunds ?: java.math.BigDecimal.ZERO) }
            item { FinancialSummaryCard("الرسوم البنكية", day?.movement?.bankFees ?: java.math.BigDecimal.ZERO) }
            item { FinancialSummaryCard("الاستثمارات", day?.movement?.investments ?: java.math.BigDecimal.ZERO) }
            item { FinancialSummaryCard("صافي التغير", (day?.movement?.netCashMovement ?: java.math.BigDecimal.ZERO)) }
            item { FinancialSummaryCard("إجمالي الالتزامات على البطاقات", day?.endOfDayLiabilities ?: java.math.BigDecimal.ZERO) }
            if (transactions.any { it.postingStatus == com.baraa.masroof.ledger.TransactionPostingStatus.NEEDS_REVIEW }) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(12.dp)) { Text("يحتاج انتباهك", style = MaterialTheme.typography.titleMedium); Text("توجد عمليات تحتاج مراجعة في قائمة العمليات") }
                }
            }
            item { SectionHeader("آخر العمليات") }
            items(transactions.sortedByDescending { it.smsTimestamp }.take(5), key = { it.id }) { tx ->
                Card { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text(tx.merchantOrBeneficiary?.takeIf { it.isNotBlank() } ?: tx.transactionType.name, style = MaterialTheme.typography.titleSmall); Text(tx.transactionDate?.toString().orEmpty(), style = MaterialTheme.typography.labelSmall) }
                    Text("${tx.amount?.toPlainString().orEmpty()} ${tx.currency.name}", style = MaterialTheme.typography.titleMedium)
                } }
            }
        }
    }
}

@Composable fun MoreMenu(onSettings: () -> Unit, onHistory: () -> Unit, onRules: () -> Unit) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("السجل المالي" to onHistory, "قواعد الربط المحفوظة" to onRules, "إعدادات التطبيق" to onSettings).forEach { (label, action) ->
            Card(onClick = action) { Row(Modifier.fillMaxWidth().padding(12.dp)) { Text(label) } }
        }
    }
}