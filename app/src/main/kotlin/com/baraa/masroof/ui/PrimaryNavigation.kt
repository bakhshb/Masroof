package com.baraa.masroof.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.baraa.masroof.ui.accounts.AccountListScreen
import com.baraa.masroof.ui.accounts.AccountLinkRulesScreen
import com.baraa.masroof.ui.history.FinancialHistoryScreen
import com.baraa.masroof.ui.transactions.TransactionListScreen
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PrimaryNavigation(initialTab: PrimaryTab = PrimaryTab.HOME) {
    var tab by rememberSaveable(initialTab.ordinal) { mutableStateOf(initialTab.ordinal) }
    var showSettings by remember { mutableStateOf(false) }
    var showFinancialHistory by remember { mutableStateOf(false) }
    var showAccountLinkRules by remember { mutableStateOf(false) }
    Scaffold(bottomBar = {
        NavigationBar {
            PrimaryTab.values().forEach { entry ->
                NavigationBarItem(selected = tab == entry.ordinal, onClick = { tab = entry.ordinal }, icon = { Icon(entry.icon, entry.title) }, label = { Text(entry.title) })
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (PrimaryTab.values()[tab]) {
                PrimaryTab.HOME -> HomeDashboard()
                PrimaryTab.TRANSACTIONS -> TransactionListScreen()
                PrimaryTab.ACCOUNTS -> AccountListScreen(onClose = {})
                PrimaryTab.MORE -> MoreMenu(onSettings = { showSettings = true }, onHistory = { showFinancialHistory = true }, onRules = { showAccountLinkRules = true })
            }
        }
    }
    if (showSettings) com.baraa.masroof.ui.settings.SettingsScreen(onClose = { showSettings = false }, onCategories = {}, onMerchants = {}, onAccounts = {}, onAi = {}, onAiSuggestions = {}, onAiBatch = {})
    if (showFinancialHistory) FinancialHistoryScreen(onClose = { showFinancialHistory = false })
    if (showAccountLinkRules) AccountLinkRulesScreen(onClose = { showAccountLinkRules = false })
}

enum class PrimaryTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("الرئيسية", Icons.Filled.Home), TRANSACTIONS("العمليات", Icons.Filled.Inbox), ACCOUNTS("الحسابات", Icons.Filled.AccountBox), MORE("المزيد", Icons.Filled.MoreHoriz);
}