package com.baraa.masroof.presentation.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CreditCardDashboardRow
import com.baraa.masroof.domain.ids.FinancialContainerIdParser
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.presentation.common.formatCardLast4

object DashboardRegistryLabels {
    fun customName(displayName: String?): String? =
        displayName?.trim()?.takeIf { it.isNotEmpty() }

    fun accountLast4(maskedNumber: String): String {
        val trimmed = maskedNumber.trim()
        val last4 = if (trimmed.length <= 4) trimmed else trimmed.takeLast(4)
        return formatCardLast4(last4)
    }

    fun resolveAccountLabel(
        displayName: String?,
        maskedNumber: String,
        last4Template: (String) -> String,
    ): String = customName(displayName) ?: last4Template(accountLast4(maskedNumber))

    fun resolveCardLabel(
        displayName: String?,
        last4: String,
        last4Template: (String) -> String,
    ): String = customName(displayName) ?: last4Template(formatCardLast4(last4))
}

@Composable
fun OwnedAccountUi.displayLabel(): String {
    val custom = DashboardRegistryLabels.customName(displayName)
    if (custom != null) return custom
    return stringResource(
        R.string.dashboard_account_item,
        DashboardRegistryLabels.accountLast4(maskedNumber),
    )
}

@Composable
fun OwnedCardUi.displayLabel(): String {
    val custom = DashboardRegistryLabels.customName(displayName)
    if (custom != null) return custom
    return stringResource(R.string.dashboard_credit_card_last4, formatCardLast4(last4))
}

@Composable
fun CreditCardDashboardRow.displayLabel(ownedCards: List<OwnedCardUi>): String {
    val match = ownedCards.find { it.bank == bank && it.last4 == last4 }
    val custom = DashboardRegistryLabels.customName(match?.displayName)
    if (custom != null) return custom
    return stringResource(R.string.dashboard_credit_card_last4, formatCardLast4(last4))
}

@Composable
fun accountDisplayLabel(
    accounts: List<OwnedAccountUi>,
    bank: Bank,
    maskedNumber: String,
): String {
    val match = accounts.find { it.bank == bank && it.maskedNumber == maskedNumber }
    val custom = DashboardRegistryLabels.customName(match?.displayName)
    if (custom != null) return custom
    return stringResource(
        R.string.dashboard_account_item,
        DashboardRegistryLabels.accountLast4(maskedNumber),
    )
}

@Composable
fun accountDisplayLabel(
    accounts: List<OwnedAccountUi>,
    containerId: String,
): String? {
    val bankId = containerId.removePrefix("account:").substringBefore(':').trim().takeIf { it.isNotEmpty() }
        ?: return null
    val masked = FinancialContainerIdParser.accountMaskedNumber(containerId) ?: return null
    return accountDisplayLabel(accounts, Bank(bankId), masked)
}

@Composable
fun cardDisplayLabel(
    cards: List<OwnedCardUi>,
    bank: Bank,
    last4: String,
): String {
    val match = cards.find { it.bank == bank && it.last4 == last4 }
    val custom = DashboardRegistryLabels.customName(match?.displayName)
    if (custom != null) return custom
    return stringResource(R.string.dashboard_credit_card_last4, formatCardLast4(last4))
}

@Composable
fun cardDisplayLabel(
    cards: List<OwnedCardUi>,
    last4: String,
): String {
    val match = cards.find { it.last4 == last4 }
    val custom = DashboardRegistryLabels.customName(match?.displayName)
    if (custom != null) return custom
    return stringResource(R.string.dashboard_credit_card_last4, formatCardLast4(last4))
}

@Composable
fun cardDisplayLabelFromTransaction(
    row: TransactionPreviewUi,
    cards: List<OwnedCardUi>,
): String? {
    val last4 = row.cardLast4 ?: return null
    val containerId = listOfNotNull(row.sourceContainerId, row.destinationContainerId)
        .firstOrNull { it.startsWith("card:") }
    val bank = containerId?.let { FinancialContainerIdParser.cardBankId(it)?.let(::Bank) }
    return if (bank != null) {
        cardDisplayLabel(cards, bank, last4)
    } else {
        cardDisplayLabel(cards, last4)
    }
}
