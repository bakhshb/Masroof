package com.baraa.masroof.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Centralized Money display: digit grouping, optional negative prefix, SAR suffix. */
@Composable
fun MoneyValue(value: BigDecimal, label: String? = null, emphasize: Boolean = false) {
    val formatted = NumberFormat.getNumberInstance(Locale("ar", "SA")).apply { maximumFractionDigits = 2; minimumFractionDigits = if (value.stripTrailingZeros().scale() > 0) 2 else 0 }.format(value)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        label?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text("$formatted ريال", style = if (emphasize) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
fun FinancialSummaryCard(title: String, value: BigDecimal) = Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleSmall); MoneyValue(value)
    }
}

@Composable
fun SectionHeader(title: String) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }

@Composable
fun EmptyState(message: String) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { Text(message) } }

@Composable
fun ConfidenceBadge(label: String) { AssistChip(onClick = {}, label = { Text(label) }) }