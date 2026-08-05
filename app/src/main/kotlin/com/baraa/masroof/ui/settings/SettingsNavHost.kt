package com.baraa.masroof.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.baraa.masroof.ui.theme.MasroofTopAppBar
import com.baraa.masroof.ui.theme.FinancialTypography
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.baraa.masroof.ui.accounts.AccountLinkRulesScreen
import com.baraa.masroof.ui.ai.AiBatchDialog
import com.baraa.masroof.ui.ai.AiSettingsScreen
import com.baraa.masroof.ui.ai.AiSuggestionsScreen
import com.baraa.masroof.ui.categories.CategoryListScreen
import com.baraa.masroof.ui.diagnostics.DiagnosticsScreen
import com.baraa.masroof.ui.diagnostics.ReleaseNotesScreen
import com.baraa.masroof.ui.diagnostics.TestDataModeScreen
import com.baraa.masroof.ui.history.FinancialHistoryScreen
import com.baraa.masroof.ui.merchants.MerchantMemoryScreen
import com.baraa.masroof.ui.senders.SenderMappingsScreen
import com.baraa.masroof.ui.accounts.AccountListScreen

/**
 * Compose `NavHost` that registers every destination from
 * [SettingsDestinations]. Each route is bound to a real screen via
 * `composable(route) { … }`. The whole screen is reachable from the
 * Settings list and supports back navigation through the standard
 * `navController.popBackStack()`.
 */
@Composable
fun SettingsNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = SettingsDestinations.categoryManagement.route,
    ) {
        composable(SettingsDestinations.categoryManagement.route) {
            CategoryListScreen(onClose = { navController.popBackStack() })
        }
        composable(SettingsDestinations.merchantMemory.route) {
            MerchantMemoryScreen(onClose = { navController.popBackStack() })
        }
        composable(SettingsDestinations.aiCategorization.route) {
            AiSettingsScreen(onClose = { navController.popBackStack() })
        }
        composable(SettingsDestinations.aiSuggestions.route) {
            AiSuggestionsScreen(onClose = { navController.popBackStack() }, minimumConfidence = 70)
        }
        composable(SettingsDestinations.aiBatch.route) {
            PlaceholderDestination(title = "تصنيف العمليات غير المصنفة", onClose = { navController.popBackStack() })
        }
        composable(SettingsDestinations.accounts.route) {
            AccountListScreen(onClose = { navController.popBackStack() })
        }
        composable(SettingsDestinations.linkTransactions.route) {
            AccountLinkRulesScreen(onClose = { navController.popBackStack() })
        }
        composable(SettingsDestinations.financialHistory.route) {
            FinancialHistoryScreen(onClose = { navController.popBackStack() })
        }
        composable(SettingsDestinations.accountLinkRules.route) {
            AccountLinkRulesScreen(onClose = { navController.popBackStack() })
        }
        composable(SettingsDestinations.senderMappings.route) {
            SenderMappingsScreen(onClose = { navController.popBackStack() })
        }
        composable(SettingsDestinations.diagnostics.route) {
            DiagnosticsScreen(onClose = { navController.popBackStack() })
        }
        composable(SettingsDestinations.testData.route) {
            TestDataModeScreen(onClose = { navController.popBackStack() })
        }
        composable(SettingsDestinations.releaseNotes.route) {
            ReleaseNotesScreen(versionName = "0.1.0-test", onClose = { navController.popBackStack() })
        }
    }
}

@Composable
private fun PlaceholderDestination(title: String, onClose: () -> Unit) {
    androidx.compose.material3.Scaffold(topBar = { MasroofTopAppBar(title = title, onBack = onClose) }) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).padding(24.dp).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text("هذه الميزة قيد التطوير", style = FinancialTypography.merchant, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
