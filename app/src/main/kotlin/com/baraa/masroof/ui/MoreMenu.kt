package com.baraa.masroof.ui

import androidx.compose.foundation.layout.Arrangement
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
 * "المزيد" tab landing page. The previous version listed half-implemented
 * entries; we now show only what the user can actually open. Linking-rule,
 * history, and sender mapping screens moved under the Settings tab via
 * the new settings registry, so they are no longer advertised here.
 */
@Composable fun MoreMenu(onSettings: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("الإعدادات والأدوات", style = com.baraa.masroof.ui.theme.FinancialTypography.sectionTitle, modifier = Modifier.padding(bottom = 8.dp))
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
