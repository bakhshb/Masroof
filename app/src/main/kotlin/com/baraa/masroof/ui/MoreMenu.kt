package com.baraa.masroof.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.baraa.masroof.ui.theme.FinancialTypography
import com.baraa.masroof.ui.theme.Spacing

/**
 * "المزيد" tab — curated product hub. Deep tools live under Settings;
 * diagnostics stay secondary.
 */
@Composable
fun MoreMenu(
    onSettings: () -> Unit,
    onCategories: () -> Unit = onSettings,
    onAccounts: () -> Unit = onSettings,
    onBankMessages: () -> Unit = onSettings,
    onLinkRules: () -> Unit = onSettings,
    onFinancialHistory: () -> Unit = onSettings,
    onPrivacyAndAi: () -> Unit = onSettings,
    onDiagnostics: () -> Unit = onSettings,
) {
    Column(
        Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "المزيد",
            style = FinancialTypography.sectionTitle,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        MenuCard(Icons.Filled.Style, "التصنيفات", "إدارة تصنيفات المصروفات والدخل", onCategories)
        MenuCard(Icons.Filled.AccountBox, "الحسابات", "أرصدة الحسابات والمعرفات", onAccounts)
        MenuCard(Icons.Filled.Sms, "رسائل البنوك", "تعليم المرسلين وأنماط الرسائل", onBankMessages)
        MenuCard(Icons.Filled.Link, "قواعد الربط", "القواعد المحفوظة لربط العمليات", onLinkRules)
        MenuCard(Icons.Filled.History, "التاريخ المالي", "أرصدة يومية وصافي الثروة", onFinancialHistory)
        MenuCard(Icons.Filled.PrivacyTip, "الخصوصية والذكاء الاصطناعي", "إعدادات محلية واختيارية", onPrivacyAndAi)
        MenuCard(Icons.Filled.Settings, "إعدادات التطبيق", "المظهر والإشعارات والاستيراد التلقائي", onSettings)
        MenuCard(Icons.Filled.Info, "التشخيص والإصدار", "أدوات المطوّر ومعلومات الإصدار", onDiagnostics)
    }
}

@Composable
private fun MenuCard(
    icon: ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(Spacing.x3))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = FinancialTypography.merchant)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
