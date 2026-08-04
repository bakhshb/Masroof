package com.baraa.masroof.ui.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AccountLinkRulesScreen(onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope(); var rules by remember { mutableStateOf(emptyList<com.baraa.masroof.data.db.AccountLinkRuleEntity>()) }
    var accounts by remember { mutableStateOf(emptyList<com.baraa.masroof.data.db.FinancialAccount>()) }
    LaunchedEffect(Unit) { app.accountLinkRuleRepository.observeAll().collectLatest { rules = it } }
    LaunchedEffect(Unit) { app.financialAccountRepository.observeAll().collectLatest { accounts = it } }
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("قواعد الربط المحفوظة") }, navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }) }) { padding ->
        if (rules.isEmpty()) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("لا توجد قواعد ربط محفوظة") }
        else LazyColumn(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rules, key = { it.id }) { rule ->
                val account = accounts.firstOrNull { it.id == rule.accountId }
                Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${rule.transactionType} • ${account?.displayName ?: "حساب غير متاح"}")
                    Text("${rule.expectedAccountType} • تم التأكيد ${rule.confirmationCount} مرة")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { scope.launch { app.database.accountLinkRuleDao().update(rule.copy(active = !rule.active)) } }) { Text(if (rule.active) "تعطيل" else "تفعيل") }
                        TextButton(onClick = { scope.launch { app.database.accountLinkRuleDao().delete(rule) } }) { Text("حذف") }
                    }
                } }
            }
        }
    }
}
