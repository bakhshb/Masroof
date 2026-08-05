package com.baraa.masroof.ui.settings

/**
 * Single registry that describes every Settings destination, its route
 * and whether it is fully implemented.
 *
 * Routes are stable string identifiers consumed by the
 * [com.baraa.masroof.ui.settings.SettingsNavHost]. When
 * [implemented] is `false` the row is disabled, dimmed, and shows a
 * "قريباً" badge instead of navigating.
 */
data class SettingsDestination(
    val route: String,
    val title: String,
    val group: SettingsGroup,
    val implemented: Boolean = true,
    val isToggle: Boolean = false,
)

enum class SettingsGroup(val header: String) {
    Categories("التصنيفات"),
    AccountsAndLinking("الحسابات والربط"),
    Diagnostics("الدعم والتشخيص"),
    Messages("الرسائل الجديدة"),
}

object SettingsDestinations {
    val all: List<SettingsDestination>

    // Categories group
    val categoryManagement = SettingsDestination("settings/categories", "إدارة التصنيفات", SettingsGroup.Categories)
    val merchantMemory = SettingsDestination("settings/merchant_memory", "التجار المحفوظون", SettingsGroup.Categories)
    val aiCategorization = SettingsDestination("settings/ai", "التصنيف الذكي", SettingsGroup.Categories)
    val aiSuggestions = SettingsDestination("settings/ai_suggestions", "اقتراحات التصنيف", SettingsGroup.Categories)
    val aiBatch = SettingsDestination("settings/ai_batch", "تصنيف العمليات غير المصنفة", SettingsGroup.Categories, implemented = false)

    // Accounts and linking group
    val accounts = SettingsDestination("settings/accounts", "الحسابات", SettingsGroup.AccountsAndLinking)
    val linkTransactions = SettingsDestination("settings/link_transactions", "ربط العمليات بالحسابات", SettingsGroup.AccountsAndLinking)
    val financialHistory = SettingsDestination("settings/financial_history", "السجل المالي", SettingsGroup.AccountsAndLinking)
    val accountLinkRules = SettingsDestination("settings/account_link_rules", "قواعد الربط المحفوظة", SettingsGroup.AccountsAndLinking)
    val senderMappings = SettingsDestination("settings/sender_mappings", "مرسلو الرسائل والمؤسسات", SettingsGroup.AccountsAndLinking)

    // Diagnostics group
    val diagnostics = SettingsDestination("settings/diagnostics", "تشخيص التطبيق", SettingsGroup.Diagnostics)
    val testData = SettingsDestination("settings/test_data", "رسائل تجريبية", SettingsGroup.Diagnostics)
    val releaseNotes = SettingsDestination("settings/release_notes", "ملاحظات الإصدار", SettingsGroup.Diagnostics)

    // Messages group
    val autoSmsImport = SettingsDestination("settings/auto_sms_import", "استيراد رسائل البنك تلقائياً", SettingsGroup.Messages)
    val transactionNotifications = SettingsDestination("settings/transaction_notifications", "إشعار عند تسجيل عملية جديدة", SettingsGroup.Messages)

    init {
        all = listOf(
            // Categories
            categoryManagement, merchantMemory, aiCategorization, aiSuggestions, aiBatch,
            // Accounts and linking
            accounts, linkTransactions, financialHistory, accountLinkRules, senderMappings,
            // Diagnostics
            diagnostics, testData, releaseNotes,
            // Messages
            autoSmsImport, transactionNotifications,
        )
    }

    fun byRoute(route: String): SettingsDestination? = all.firstOrNull { it.route == route }
}

data class SettingsClickLog(
    val destinationTitle: String,
    val route: String,
    val routeRegistered: Boolean,
    val implemented: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
)
