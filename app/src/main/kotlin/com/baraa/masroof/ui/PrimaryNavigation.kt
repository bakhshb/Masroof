package com.baraa.masroof.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import com.baraa.masroof.ui.senders.SenderDetailsScreen
import com.baraa.masroof.ui.senders.TemplateEditorScreen
import com.baraa.masroof.ui.settings.AutoSmsImportSettingsScreen
import com.baraa.masroof.ui.settings.NotificationsSettingsScreen
import com.baraa.masroof.ui.transactions.TransactionOperationsScreen
import com.baraa.masroof.ui.transactions.TransactionDetailScreen
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
                    (
                        currentRoute == ImportMessagesRoute ||
                            currentRoute == ReviewQueueRoute ||
                            currentRoute == TransactionDetailRoute
                        ) && entry == PrimaryTab.TRANSACTIONS -> true
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
                    icon = {
                        Icon(
                            if (selected) entry.selectedIcon else entry.unselectedIcon,
                            entry.title,
                        )
                    },
                    label = { Text(entry.title) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.secondary,
                        selectedTextColor = MaterialTheme.colorScheme.secondary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
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
                    onImportMessages = { navController.navigate(ImportMessagesRoute) { launchSingleTop = true } },
                    onShowAllTransactions = { navController.navigate("primary/TRANSACTIONS") },
                    onOpenReview = { navController.navigate(ReviewQueueRoute) { launchSingleTop = true } },
                    onBankMessages = {
                        navController.navigate(SettingsDestinations.bankMessages.route) { launchSingleTop = true }
                    },
                    onTransactionClick = { id -> navController.navigate(transactionDetailRoute(id)) },
                )
            }
            composable("primary/TRANSACTIONS") {
                TransactionOperationsScreen(
                    onOpenImport = { navController.navigate(ImportMessagesRoute) { launchSingleTop = true } },
                    onOpenReview = { navController.navigate(ReviewQueueRoute) { launchSingleTop = true } },
                    onTransactionClick = { id -> navController.navigate(transactionDetailRoute(id)) },
                )
            }
            composable(
                route = TransactionDetailRoute,
                arguments = listOf(navArgument("transactionId") { type = NavType.LongType }),
            ) { backStackEntry ->
                TransactionDetailScreen(
                    transactionId = backStackEntry.arguments?.getLong("transactionId") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onOpenReview = {
                        navController.navigate(ReviewQueueRoute) { launchSingleTop = true }
                    },
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
                    onBankMessages = {
                        navController.navigate(SettingsDestinations.bankMessages.route) { launchSingleTop = true }
                    },
                )
            }
            composable(ReviewQueueRoute) {
                ReviewQueueScreen(
                    onBack = {
                        if (!navController.popBackStack()) {
                            navigateToPrimaryTab(navController, PrimaryTab.TRANSACTIONS)
                        }
                    },
                    onHome = { navigateToPrimaryTab(navController, PrimaryTab.HOME) },
                    onImport = { navController.navigate(ImportMessagesRoute) { launchSingleTop = true } },
                    onBankMessages = {
                        navController.navigate(SettingsDestinations.bankMessages.route) { launchSingleTop = true }
                    },
                )
            }
            composable("primary/ACCOUNTS") {
                com.baraa.masroof.ui.accounts.AccountListScreen(
                    onClose = null,
                    onOpenImport = { navController.navigate(ImportMessagesRoute) { launchSingleTop = true } },
                )
            }
            composable(AppRoutes.bindAccount(0L).substringBeforeLast("/") + "/{accountId}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("accountId")?.toLongOrNull() ?: 0L
                com.baraa.masroof.ui.accounts.AccountBindRoute(
                    accountId = id,
                    onBack = { navController.popBackStack() },
                    onImportNow = {
                        navController.popBackStack()
                        navController.navigate(ImportMessagesRoute) { launchSingleTop = true }
                    },
                )
            }
            composable("primary/MORE") {
                MoreMenu(
                    onSettings = { navController.navigate("settings/list") },
                    onCategories = { navController.navigate(SettingsDestinations.categoryManagement.route) },
                    onAccounts = { navController.navigate(SettingsDestinations.accounts.route) },
                    onBankMessages = { navController.navigate(SettingsDestinations.bankMessages.route) },
                    onLinkRules = { navController.navigate(SettingsDestinations.accountLinkRules.route) },
                    onFinancialHistory = { navController.navigate(SettingsDestinations.financialHistory.route) },
                    onPrivacyAndAi = { navController.navigate(SettingsDestinations.aiCategorization.route) },
                    onDiagnostics = { navController.navigate(SettingsDestinations.diagnostics.route) },
                )
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
                com.baraa.masroof.ui.accounts.AccountListScreen(
                    onClose = { navController.popBackStack() },
                    onOpenImport = { navController.navigate(ImportMessagesRoute) { launchSingleTop = true } },
                )
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
            composable(SettingsDestinations.bankMessages.route) {
                com.baraa.masroof.ui.senders.BankMessagesScreen(
                    onBack = { navController.popBackStack() },
                    onSenderClick = {
                        navController.navigate(SettingsDestinations.bankMessagesSender(it))
                    },
                    onReturnToImport = {
                        if (!navController.popBackStack(ImportMessagesRoute, inclusive = false)) {
                            navController.navigate(ImportMessagesRoute) { launchSingleTop = true }
                        }
                    },
                )
            }
            composable(
                SettingsDestinations.bankMessagesSenderRoute,
                arguments = listOf(navArgument("senderProfileId") { type = NavType.LongType }),
            ) { entry ->
                SenderDetailsScreen(
                    senderProfileId = entry.arguments?.getLong("senderProfileId") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onTemplateClick = {
                        navController.navigate(SettingsDestinations.bankMessagesTemplate(it))
                    },
                    onOpenDraftEditor = {
                        navController.navigate(SettingsDestinations.bankMessagesDraft())
                    },
                    onReturnToImport = {
                        if (!navController.popBackStack(ImportMessagesRoute, inclusive = false)) {
                            navController.navigate(ImportMessagesRoute) { launchSingleTop = true }
                        }
                    },
                )
            }
            composable(
                SettingsDestinations.bankMessagesTemplateRoute,
                arguments = listOf(navArgument("patternId") { type = NavType.LongType }),
            ) { entry ->
                TemplateEditorScreen(
                    patternId = entry.arguments?.getLong("patternId") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onReturnToImport = {
                        if (!navController.popBackStack(ImportMessagesRoute, inclusive = false)) {
                            navController.navigate(ImportMessagesRoute) { launchSingleTop = true }
                        }
                    },
                )
            }
            composable(SettingsDestinations.bankMessagesDraftRoute) {
                TemplateEditorScreen(
                    patternId = 0L,
                    draft = remember { com.baraa.masroof.ui.senders.PatternDraftHolder.consume() },
                    onBack = { navController.popBackStack() },
                    onReturnToImport = {
                        if (!navController.popBackStack(ImportMessagesRoute, inclusive = false)) {
                            navController.navigate(ImportMessagesRoute) { launchSingleTop = true }
                        }
                    },
                )
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

enum class PrimaryTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME("الرئيسية", Icons.Filled.Home, Icons.Outlined.Home),
    TRANSACTIONS("العمليات", Icons.Filled.Inbox, Icons.Outlined.Inbox),
    ACCOUNTS("الحسابات", Icons.Filled.AccountBox, Icons.Outlined.AccountBox),
    MORE("المزيد", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz);

    /** Backward-compatible alias used by older call sites / tests. */
    val icon: ImageVector get() = selectedIcon
}

private fun SettingsDestination.routeKey(): String = route

/** Stable top-level route for the SMS import flow. */
const val ImportMessagesRoute: String = AppRoutes.IMPORT
const val ReviewQueueRoute: String = AppRoutes.REVIEW
const val TransactionDetailRoute: String = "operations/transaction/{transactionId}"
fun transactionDetailRoute(transactionId: Long): String = "operations/transaction/$transactionId"

/**
 * Switch to a primary bottom-nav tab. Pops the import / settings stack
 * first so back navigation from HOME closes the app instead of returning
 * to the import screen.
 *
 * HOME is special-cased: navigate()+launchSingleTop+restoreState is a
 * no-op when HOME is already under Import/Review, so we pop back to it.
 */
private fun navigateToPrimaryTab(navController: NavHostController, tab: PrimaryTab) {
    val route = "primary/${tab.name}"
    // Leave nested screens (import / review) so post-import CTAs always work.
    navController.popBackStack(ImportMessagesRoute, inclusive = true)
    navController.popBackStack(ReviewQueueRoute, inclusive = true)
    if (tab == PrimaryTab.HOME) {
        if (!navController.popBackStack("primary/HOME", inclusive = false)) {
            navController.navigate(route) { launchSingleTop = true }
        }
        return
    }
    navController.navigate(route) {
        popUpTo("primary/HOME") { inclusive = false; saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
