package com.baraa.masroof.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * "المزيد" tab landing page. Routing decisions are owned by the parent;
 * this composable only renders the entries.
 */
@Composable fun MoreMenu(
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onRules: () -> Unit,
    onSenderMappings: () -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        Text("الإعدادات والأدوات", style = com.baraa.masroof.ui.theme.FinancialTypography.sectionTitle, modifier = Modifier.padding(bottom = 8.dp))
        MenuCard("السجل المالي", onClick = onHistory)
        MenuCard("مرسلو الرسائل والمؤسسات", onClick = onSenderMappings)
        MenuCard("قواعد الربط المحفوظة", onClick = onRules)
        MenuCard("إعدادات التطبيق", onClick = onSettings)
    }
}

@Composable
private fun MenuCard(label: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(label, style = com.baraa.masroof.ui.theme.FinancialTypography.merchant)
            Spacer(modifier = Modifier.weight(1f))
            Text("›", style = com.baraa.masroof.ui.theme.FinancialTypography.merchant, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
