package com.baraa.masroof.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baraa.masroof.ledger.HistoricalCompletenessStatus
import com.baraa.masroof.ui.charts.ChartCard
import com.baraa.masroof.ui.charts.ChartMappers
import com.baraa.masroof.ui.charts.DailyTrendColumnChart
import com.baraa.masroof.ui.theme.FinancialMetric
import com.baraa.masroof.ui.theme.MonthlySummaryRow
import com.baraa.masroof.ui.theme.SemanticColors
import com.baraa.masroof.ui.theme.Spacing
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialHistoryScreen(onClose: () -> Unit, viewModel: FinancialHistoryViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = remember { java.time.LocalDate.now() }
    val summary = state.history?.daily?.get(state.selectedDate)
    val liquiditySeries = remember(state.history) {
        state.history?.let { ChartMappers.dailyLiquiditySeries(it) }.orEmpty()
    }
    val liquidityHasData = ChartMappers.hasNonZero(liquiditySeries)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("السجل المالي") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && state.history == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("جارٍ حساب الأرصدة")
                    }
                }
            }
            state.error -> {
                Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("تعذر حساب السجل المالي")
                    Button(onClick = viewModel::retry) { Text("إعادة المحاولة") }
                }
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { viewModel.load(state.month.minusMonths(1)) }) { Text("الشهر السابق") }
                            Text(state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("ar"))), style = MaterialTheme.typography.titleMedium)
                            OutlinedButton(
                                enabled = state.month < java.time.YearMonth.now(),
                                onClick = { viewModel.load(state.month.plusMonths(1)) },
                            ) { Text("الشهر التالي") }
                        }
                        TextButton(onClick = { viewModel.load(java.time.YearMonth.now(), today) }) { Text("اليوم") }
                    }
                    if (state.history != null) {
                        item {
                            ChartCard(
                                title = "السيولة خلال الشهر",
                                subtitle = "رصيد السيولة في نهاية كل يوم",
                                isEmpty = !liquidityHasData,
                                emptyMessage = "لا توجد بيانات سيولة لهذا الشهر",
                            ) {
                                DailyTrendColumnChart(
                                    points = liquiditySeries,
                                    columnColorArgb = SemanticColors.secondaryAccent().toArgb(),
                                )
                            }
                        }
                    }
                    item { Text("اختر اليوم", style = MaterialTheme.typography.titleMedium) }
                    items(state.history?.daily?.values?.toList().orEmpty(), key = { it.selectedDate }) { day ->
                        val disabled = day.selectedDate > today ||
                            day.completeness.statuses.contains(HistoricalCompletenessStatus.BEFORE_TRACKING_START)
                        Card(
                            onClick = { if (!disabled) viewModel.select(day.selectedDate) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (day.selectedDate == state.selectedDate) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(day.selectedDate.dayOfMonth.toString())
                                Text("السيولة ${money(day.endOfDayLiquidity)}")
                                Text("صافي الثروة ${money(day.endOfDayNetWorth)}")
                            }
                        }
                    }
                    summary?.let { s ->
                        item {
                            Text(
                                s.selectedDate.format(DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", Locale("ar"))),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Row {
                                FilterChip(selected = !state.endOfDay, onClick = { viewModel.setEndOfDay(false) }, label = { Text("بداية اليوم") })
                                Spacer(Modifier.width(8.dp))
                                FilterChip(selected = state.endOfDay, onClick = { viewModel.setEndOfDay(true) }, label = { Text("نهاية اليوم") })
                            }
                            val end = state.endOfDay
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2), modifier = Modifier.padding(top = Spacing.x2)) {
                                FinancialMetric(
                                    label = "صافي الثروة",
                                    amount = if (end) s.endOfDayNetWorth else s.startOfDayNetWorth,
                                    icon = Icons.Filled.TrendingUp,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                FinancialMetric(
                                    label = "السيولة المتاحة",
                                    amount = if (end) s.endOfDayLiquidity else s.startOfDayLiquidity,
                                    icon = Icons.Filled.AccountBalanceWallet,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                FinancialMetric(
                                    label = "إجمالي الأصول",
                                    amount = if (end) s.endOfDayAssets else s.startOfDayAssets,
                                    icon = Icons.Filled.Savings,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                FinancialMetric(
                                    label = "إجمالي الالتزامات",
                                    amount = if (end) s.endOfDayLiabilities else s.startOfDayLiabilities,
                                    icon = Icons.Filled.CreditCard,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        item {
                            Text("حركات اليوم", style = MaterialTheme.typography.titleMedium)
                            Card {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (s.movement.income.signum() > 0) MonthlySummaryRow("الدخل", s.movement.income, isExpense = false)
                                    if (s.movement.expenses.signum() > 0) MonthlySummaryRow("المصروفات", s.movement.expenses, isExpense = true)
                                    if (s.movement.refunds.signum() > 0) MonthlySummaryRow("الاستردادات", s.movement.refunds, isExpense = false)
                                    if (s.movement.bankFees.signum() > 0) MonthlySummaryRow("الرسوم البنكية", s.movement.bankFees, isExpense = true)
                                    if (s.movement.internalTransfers.signum() > 0) MonthlySummaryRow("التحويلات الداخلية", s.movement.internalTransfers)
                                    if (s.movement.creditCardPayments.signum() > 0) MonthlySummaryRow("سداد البطاقات", s.movement.creditCardPayments, isExpense = true)
                                    if (listOf(
                                            s.movement.income, s.movement.expenses, s.movement.refunds,
                                            s.movement.bankFees, s.movement.internalTransfers, s.movement.creditCardPayments,
                                        ).all { it.signum() == 0 }
                                    ) {
                                        Text("لا توجد حركات في هذا اليوم", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        item { Text("أرصدة الحسابات", style = MaterialTheme.typography.titleMedium) }
                        items(s.accounts, key = { it.accountId }) { a ->
                            Card {
                                Column(Modifier.padding(12.dp)) {
                                    Text(a.accountDisplayLabel, fontWeight = FontWeight.SemiBold)
                                    Text("${money(if (state.endOfDay) a.endOfDayBalance else a.startOfDayBalance)} ${a.currency}")
                                    Text(if (!a.isActive) "حساب غير نشط" else a.accountType.name)
                                    if (a.trackingStatus.name == "NOT_STARTED") {
                                        Text("لا تتوفر بيانات قبل تاريخ بدء متابعة الحساب")
                                    }
                                }
                            }
                        }
                        item {
                            if (s.unpostedTransactionCount > 0) {
                                Text("توجد عمليات غير معتمدة في هذا اليوم ولا تظهر ضمن الأرصدة (${s.unpostedTransactionCount})")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun money(value: BigDecimal): String =
    NumberFormat.getNumberInstance(Locale("ar", "SA")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = if (value.stripTrailingZeros().scale() > 0) 2 else 0
    }.format(value) + " ريال"
