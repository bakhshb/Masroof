package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBankScreen(
    bank: Bank,
    summary: SettingsBankSummaryUi,
    onBack: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCards: () -> Unit,
    onOpenLoans: () -> Unit,
) {
    val accountsSubtitle = when {
        summary.accountCount == 0 -> stringResource(R.string.settings_accounts_empty)
        summary.unregisteredAccountCount > 0 -> {
            val followed = (summary.accountCount - summary.unregisteredAccountCount).coerceAtLeast(0)
            stringResource(
                R.string.settings_hub_accounts_subtitle,
                followed,
                summary.unregisteredAccountCount,
            )
        }
        else -> stringResource(
            R.string.settings_hub_accounts_subtitle_followed_only,
            summary.accountCount,
        )
    }
    val cardsSubtitle = when {
        summary.cardCount == 0 -> stringResource(R.string.settings_cards_empty)
        summary.unregisteredCardCount > 0 -> {
            val followed = (summary.cardCount - summary.unregisteredCardCount).coerceAtLeast(0)
            stringResource(
                R.string.settings_hub_cards_subtitle,
                followed,
                summary.unregisteredCardCount,
            )
        }
        else -> stringResource(
            R.string.settings_hub_cards_subtitle_followed_only,
            summary.cardCount,
        )
    }
    val loansSubtitle = if (summary.loanCount > 0) {
        stringResource(R.string.settings_bank_summary_loans, summary.loanCount)
    } else {
        stringResource(R.string.settings_loans_empty)
    }

    MasroofSecondaryScaffold(
        title = settingsBankLabel(bank),
        onBack = onBack,
        backContentDescription = stringResource(R.string.settings_back),
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsNavRow(
                icon = MasroofIcons.externalIn,
                title = stringResource(R.string.settings_bank_accounts_title),
                subtitle = accountsSubtitle,
                onClick = onOpenAccounts,
                enabled = summary.accountCount > 0,
            )

            SettingsNavRow(
                icon = MasroofIcons.cardPayment,
                title = stringResource(R.string.settings_bank_cards_title),
                subtitle = cardsSubtitle,
                onClick = onOpenCards,
                enabled = summary.cardCount > 0,
            )

            SettingsNavRow(
                icon = MasroofIcons.moneyMovement,
                title = stringResource(R.string.settings_bank_loans_title),
                subtitle = loansSubtitle,
                onClick = onOpenLoans,
                enabled = summary.loanCount > 0,
            )
        }
    }
}
