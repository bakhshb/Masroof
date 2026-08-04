package com.baraa.masroof.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baraa.masroof.ledger.HistoricalCompletenessStatus
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FinancialHistoryScreen(onClose: () -> Unit, viewModel: FinancialHistoryViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle(); val today = remember { java.time.LocalDate.now() }
    val summary = state.history?.daily?.get(state.selectedDate)
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("السجل المالي") }, navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }) }) { padding ->
        if (state.loading && state.history == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) { Column { CircularProgressIndicator(); Text("جارٍ حساب الأرصدة") } }
        else if (state.error) Column(Modifier.padding(padding).padding(24.dp)) { Text("تعذر حساب السجل المالي"); Button(onClick = viewModel::retry) { Text("إعادة المحاولة") } }
        else LazyColumn(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { OutlinedButton(onClick = { viewModel.load(state.month.minusMonths(1)) }) { Text("الشهر السابق") }; Text(state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("ar"))), style = MaterialTheme.typography.titleMedium); OutlinedButton(enabled = state.month < java.time.YearMonth.now(), onClick = { viewModel.load(state.month.plusMonths(1)) }) { Text("الشهر التالي") } }; TextButton(onClick = { viewModel.load(java.time.YearMonth.now(), today) }) { Text("اليوم") } }
            item { Text("اختر اليوم", style = MaterialTheme.typography.titleMedium) }
            items(state.history?.daily?.values?.toList().orEmpty(), key = { it.selectedDate }) { day ->
                val disabled = day.selectedDate > today || day.completeness.statuses.contains(HistoricalCompletenessStatus.BEFORE_TRACKING_START)
                Card(onClick = { if (!disabled) viewModel.select(day.selectedDate) }, colors = CardDefaults.cardColors(containerColor = if (day.selectedDate == state.selectedDate) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(day.selectedDate.dayOfMonth.toString()); Text("السيولة ${money(day.endOfDayLiquidity)}"); Text("صافي الثروة ${money(day.endOfDayNetWorth)}") } }
            }
            summary?.let { s -> item { Text(s.selectedDate.format(DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", Locale("ar"))), style = MaterialTheme.typography.titleMedium); Row { FilterChip(selected = !state.endOfDay, onClick = { viewModel.setEndOfDay(false) }, label = { Text("بداية اليوم") }); Spacer(Modifier.width(8.dp)); FilterChip(selected = state.endOfDay, onClick = { viewModel.setEndOfDay(true) }, label = { Text("نهاية اليوم") }) }; val end = state.endOfDay; SummaryCard("صافي الثروة", if(end) s.endOfDayNetWorth else s.startOfDayNetWorth); SummaryCard("السيولة المتاحة", if(end) s.endOfDayLiquidity else s.startOfDayLiquidity); SummaryCard("إجمالي الأصول", if(end) s.endOfDayAssets else s.startOfDayAssets); SummaryCard("إجمالي الالتزامات", if(end) s.endOfDayLiabilities else s.startOfDayLiabilities) }
                item { Text("حركات اليوم", style = MaterialTheme.typography.titleMedium); Text("الدخل ${money(s.movement.income)} • المصروفات ${money(s.movement.expenses)} • الاستردادات ${money(s.movement.refunds)}\nالرسوم البنكية ${money(s.movement.bankFees)} • التحويلات الداخلية ${money(s.movement.internalTransfers)} • سداد البطاقات ${money(s.movement.creditCardPayments)}") }
                item { Text("أرصدة الحسابات", style = MaterialTheme.typography.titleMedium) }
                items(s.accounts, key = { it.accountId }) { a -> Card { Column(Modifier.padding(12.dp)) { Text(a.accountDisplayLabel, fontWeight = FontWeight.SemiBold); Text("${money(if (state.endOfDay) a.endOfDayBalance else a.startOfDayBalance)} ${a.currency}"); Text(if (!a.isActive) "حساب غير نشط" else a.accountType.name); if (a.trackingStatus.name == "NOT_STARTED") Text("لا تتوفر بيانات قبل تاريخ بدء متابعة الحساب") } } }
                item { if (s.unpostedTransactionCount > 0) Text("توجد عمليات غير معتمدة في هذا اليوم ولا تظهر ضمن الأرصدة (${s.unpostedTransactionCount})") }
            }
        }
    }
}
@Composable private fun SummaryCard(label: String, value: java.math.BigDecimal) = Card { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(money(value), fontWeight = FontWeight.Bold) } }
private fun money(value: java.math.BigDecimal): String = NumberFormat.getNumberInstance(Locale("ar", "SA")).apply { maximumFractionDigits = 2; minimumFractionDigits = if (value.stripTrailingZeros().scale() > 0) 2 else 0 }.format(value) + " ريال"
