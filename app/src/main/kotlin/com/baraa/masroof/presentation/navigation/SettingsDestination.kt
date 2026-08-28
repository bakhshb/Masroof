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
    }

fun decodeSettingsDestination(encoded: String): SettingsDestination {
    if (encoded == "hub") return SettingsDestination.Hub
    if (encoded == "banks") return SettingsDestination.Banks
    if (encoded == "about") return SettingsDestination.About
    if (encoded == "logs") return SettingsDestination.Logs
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

fun SettingsDestination.parent(skippedBanksList: Boolean = false): SettingsDestination =
    when (this) {
        is SettingsDestination.BankAccounts -> SettingsDestination.BankHub(bankId)
        is SettingsDestination.BankCards -> SettingsDestination.BankHub(bankId)
        is SettingsDestination.BankLoans -> SettingsDestination.BankHub(bankId)
        is SettingsDestination.BankHub ->
            if (skippedBanksList) SettingsDestination.Hub else SettingsDestination.Banks
        SettingsDestination.Banks -> SettingsDestination.Hub
        SettingsDestination.Logs -> SettingsDestination.About
        SettingsDestination.About,
        SettingsDestination.Hub,
        -> SettingsDestination.Hub
    }
