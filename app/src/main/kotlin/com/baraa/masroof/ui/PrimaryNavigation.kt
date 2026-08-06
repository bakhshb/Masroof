package com.baraa.masroof.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.baraa.masroof.ui.accounts.AccountLinkRulesScreen
import com.baraa.masroof.ui.ai.AiSettingsScreen
import com.baraa.masroof.ui.ai.AiSuggestionsScreen
import com.baraa.masroof.ui.categories.CategoryListScreen
import com.baraa.masroof.ui.diagnostics.DiagnosticsScreen
import com.baraa.masroof.ui.diagnostics.ReleaseNotesScreen
import com.baraa.masroof.ui.diagnostics.TestDataModeScreen
import com.baraa.masroof.ui.history.FinancialHistoryScreen
import com.baraa.masroof.ui.merchants.MerchantMemoryScreen
import com.baraa.masroof.ui.senders.SenderMappingsScreen
import com.baraa.masroof.ui.senders.ImportMessagesScreen
import com.baraa.masroof.ui.settings.AutoSmsImportSettingsScreen
import com.baraa.masroof.ui.settings.NotificationsSettingsScreen
import com.baraa.masroof.ui.transactions.TransactionOperationsScreen
import com.baraa.masroof.ui.transactions.ReviewQueueScreen
import com.baraa.masroof.ui.settings.SettingsDestination
import com.baraa.masroof.ui.settings.SettingsDestinations
import com.baraa.masroof.ui.settings.SettingsScreen

/**
 * Primary application navigation.
 *
 * Hosts:
 *  - the four-tab bottom navigation
 *  - a single NavController routing through the bottom tabs
 *  - the settings sub-graph, reachable via `nav_primary/settings`, where
 *    every Settings row is bound to a real destination.
 *
 * The bottom NavigationBar is rendered on every route. The home tab is
 * highlighted even when the user is on the import screen because the
 * import screen is logically part of the "العمليات" tab flow.
 */
@Composable
fun PrimaryNavigation(initialTab: PrimaryTab = PrimaryTab.HOME) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    Scaffold(bottomBar = {
        NavigationBar {
            PrimaryTab.values().forEach { entry ->
                val route = "primary/${entry.name}"
                val selected = when {
                    currentRoute == route -> true
                    // The import screen is logically part of the
                    // TRANSACTIONS tab; keep it highlighted.
                    (currentRoute == ImportMessagesRoute || currentRoute == ReviewQueueRoute) && entry == PrimaryTab.TRANSACTIONS -> true
                    else -> false
                }
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        android.util.Log.i(
                            "PrimaryNav",
                            "HOME_NAVIGATION_CLICKED currentRoute=$currentRoute targetRoute=$route",
                        )
                        // A child route (import/review) is highlighted as Operations
                        // but tapping Operations must still return to its root.
                        if (currentRoute == route) return@NavigationBarItem
                        navigateToPrimaryTab(navController, entry)
                    },
                    icon = { Icon(entry.icon, entry.title) },
                    label = { Text(entry.title) },
                )
            }
        }
    }) { padding ->
        NavHost(
            navController = navController,
            startDestination = "primary/${initialTab.name}",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable("primary/HOME") {
                HomeScreen(
                    onImportMessages = { navController.navigate("primary/TRANSACTIONS") },
                    onShowAllTransactions = { navController.navigate("primary/TRANSACTIONS") },
                    onOpenReview = { navController.navigate("primary/TRANSACTIONS") },
                )
            }
            composable("primary/TRANSACTIONS") {
                TransactionOperationsScreen(
                    onOpenImport = { navController.navigate(ImportMessagesRoute) { launchSingleTop = true } },
                )
            }
            composable(ImportMessagesRoute) {
                ImportMessagesScreen(
                    onClose = { navController.navigateUp() },
                    onHome = { navigateToPrimaryTab(navController, PrimaryTab.HOME) },
                    onTransactions = { navigateToPrimaryTab(navController, PrimaryTab.TRANSACTIONS) },
                    onAccounts = { navigateToPrimaryTab(navController, PrimaryTab.ACCOUNTS) },
                    onMore = { navigateToPrimaryTab(navController, PrimaryTab.MORE) },
                    onShowImportedTransactions = { navigateToPrimaryTab(navController, PrimaryTab.TRANSACTIONS) },
                    onNavigateToAccounts = { navigateToPrimaryTab(navController, PrimaryTab.ACCOUNTS) },
                    onReview = { navController.navigate(ReviewQueueRoute) { launchSingleTop = true } },
                )
            }
            composable(ReviewQueueRoute) {
                ReviewQueueScreen(
                    onBack = { navController.popBackStack(ImportMessagesRoute, inclusive = false) },
                    onHome = { navigateToPrimaryTab(navController, PrimaryTab.HOME) },
                )
            }
            composable("primary/ACCOUNTS") { com.baraa.masroof.ui.accounts.AccountListScreen(onClose = {}) }
            composable(AppRoutes.bindAccount(0L).substringBeforeLast("/") + "/{accountId}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("accountId")?.toLongOrNull() ?: 0L
                com.baraa.masroof.ui.accounts.AccountBindRoute(accountId = id) { navController.popBackStack() }
            }
            composable("primary/MORE") {
                MoreMenu(onSettings = { navController.navigate("settings/list") })
            }
            // Settings sub-graph
            composable("settings/list") {
                SettingsScreen(
                    onClose = { navController.popBackStack("primary/MORE", inclusive = false) },
                    onNavigate = { route -> navController.navigate(route) },
                )
            }
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
            composable(SettingsDestinations.accounts.route) {
                com.baraa.masroof.ui.accounts.AccountListScreen(onClose = { navController.popBackStack() })
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
            composable(SettingsDestinations.autoSmsImport.route) {
                AutoSmsImportSettingsScreen(
                    onClose = { navController.popBackStack() },
                    onRequestReceiveSms = { /* handled inside the screen via the activity launcher */ },
                )
            }
            composable(SettingsDestinations.transactionNotifications.route) {
                NotificationsSettingsScreen(onClose = { navController.popBackStack() })
            }
        }
    }
}

enum class PrimaryTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("الرئيسية", Icons.Filled.Home), TRANSACTIONS("العمليات", Icons.Filled.Inbox), ACCOUNTS("الحسابات", Icons.Filled.AccountBox), MORE("المزيد", Icons.Filled.MoreHoriz);
}

private fun SettingsDestination.routeKey(): String = route

/** Stable top-level route for the SMS import flow. */
const val ImportMessagesRoute: String = AppRoutes.IMPORT
const val ReviewQueueRoute: String = AppRoutes.REVIEW

/**
 * Switch to a primary bottom-nav tab. Pops the import / settings stack
 * first so back navigation from HOME closes the app instead of returning
 * to the import screen.
 */
private fun navigateToPrimaryTab(navController: NavHostController, tab: PrimaryTab) {
    val route = "primary/${tab.name}"
    navController.navigate(route) {
        popUpTo("primary/HOME") { inclusive = false; saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
