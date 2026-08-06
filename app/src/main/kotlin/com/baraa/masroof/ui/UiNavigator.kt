package com.baraa.masroof.ui

/** Testable app-level navigation intent. Compose owns the NavController adapter. */
sealed interface NavigationCommand {
    data object OpenHome : NavigationCommand
    data object OpenOperations : NavigationCommand
    data object OpenImport : NavigationCommand
    data class OpenImportResults(val importSessionId: Long) : NavigationCommand
    data class OpenReviewQueue(val importSessionId: Long? = null, val reviewFilter: String = "actionable") : NavigationCommand
    data class OpenInstitutionMapping(val senderKey: String? = null) : NavigationCommand
    data class OpenAccountChooser(val transactionId: Long) : NavigationCommand
    data class BindAccountFromSms(val accountId: Long) : NavigationCommand
    data object BackToOperations : NavigationCommand
}

object AppRoutes {
    const val HOME = "primary/HOME"
    const val OPERATIONS = "primary/TRANSACTIONS"
    const val IMPORT = "route/import_messages"
    const val REVIEW = "operations/review"
    fun importResults(sessionId: Long) = "operations/import-results/$sessionId"
    fun review(sessionId: Long?) = "$REVIEW?sessionId=${sessionId ?: "current"}"
    fun bindAccount(accountId: Long) = "operations/account-bind/$accountId"
}

/** Pure resolver keeps navigation behavior testable without Compose or a device. */
fun NavigationCommand.destinationRoute(): String = when (this) {
    NavigationCommand.OpenHome -> AppRoutes.HOME
    NavigationCommand.OpenOperations, NavigationCommand.BackToOperations -> AppRoutes.OPERATIONS
    NavigationCommand.OpenImport -> AppRoutes.IMPORT
    is NavigationCommand.OpenImportResults -> AppRoutes.importResults(importSessionId)
    is NavigationCommand.OpenReviewQueue -> AppRoutes.review(importSessionId)
    is NavigationCommand.OpenInstitutionMapping -> "settings/sender_mappings"
    is NavigationCommand.OpenAccountChooser -> "operations/account-chooser/$transactionId"
    is NavigationCommand.BindAccountFromSms -> AppRoutes.bindAccount(accountId)
}
