package com.baraa.masroof.presentation.navigation

sealed interface SettingsDestination {
    data object Hub : SettingsDestination

    data object Banks : SettingsDestination

    data class BankHub(
        val bankId: String,
    ) : SettingsDestination

    data class BankAccounts(
        val bankId: String,
    ) : SettingsDestination

    data class BankCards(
        val bankId: String,
    ) : SettingsDestination

    data class BankLoans(
        val bankId: String,
    ) : SettingsDestination

    data object About : SettingsDestination

    data object Logs : SettingsDestination

    data object DesignCatalog : SettingsDestination
}

fun SettingsDestination.encode(): String =
    when (this) {
        SettingsDestination.Hub -> "hub"
        SettingsDestination.Banks -> "banks"
        is SettingsDestination.BankHub -> "bank:$bankId"
        is SettingsDestination.BankAccounts -> "bank:$bankId:accounts"
        is SettingsDestination.BankCards -> "bank:$bankId:cards"
        is SettingsDestination.BankLoans -> "bank:$bankId:loans"
        SettingsDestination.About -> "about"
        SettingsDestination.Logs -> "logs"
        SettingsDestination.DesignCatalog -> "design_catalog"
    }

fun decodeSettingsDestination(encoded: String): SettingsDestination {
    if (encoded == "hub") return SettingsDestination.Hub
    if (encoded == "banks") return SettingsDestination.Banks
    if (encoded == "about") return SettingsDestination.About
    if (encoded == "logs") return SettingsDestination.Logs
    if (encoded == "design_catalog") return SettingsDestination.DesignCatalog
    if (encoded.startsWith("bank:")) {
        val parts = encoded.removePrefix("bank:").split(":")
        val bankId = parts.firstOrNull().orEmpty()
        return when (parts.getOrNull(1)) {
            "accounts" -> SettingsDestination.BankAccounts(bankId)
            "cards" -> SettingsDestination.BankCards(bankId)
            "loans" -> SettingsDestination.BankLoans(bankId)
            else -> SettingsDestination.BankHub(bankId)
        }
    }
    return SettingsDestination.Hub
}

/** Starts Settings on [destination] only — back from here leaves Settings. */
fun replaceSettingsStack(destination: SettingsDestination): List<String> =
    listOf(destination.encode())

fun pushSettingsDestination(stack: List<String>, next: SettingsDestination): List<String> =
    stack + next.encode()

/** `null` means the back press should leave Settings and return to the caller. */
fun popSettingsStack(stack: List<String>): List<String>? =
    if (stack.size <= 1) null else stack.dropLast(1)

fun replaceSettingsTop(stack: List<String>, next: SettingsDestination): List<String> {
    val remaining = if (stack.isEmpty()) emptyList() else stack.dropLast(1)
    return remaining + next.encode()
}
