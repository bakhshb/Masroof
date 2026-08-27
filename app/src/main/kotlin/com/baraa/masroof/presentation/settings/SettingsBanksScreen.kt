package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBanksScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onOpenBank: (SettingsBankSummaryUi) -> Unit,
) {
    MasroofSecondaryScaffold(
        title = stringResource(R.string.settings_banks_section),
        onBack = onBack,
        backContentDescription = stringResource(R.string.settings_back),
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.settings_banks_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.bankSummaries.isEmpty()) {
                Text(
                    stringResource(R.string.settings_banks_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.bankSummaries.forEach { summary ->
                    SettingsNavRow(
                        icon = MasroofIcons.moneyMovement,
                        title = settingsBankLabel(summary.bank),
                        subtitle = bankSummarySubtitle(summary),
                        onClick = { onOpenBank(summary) },
                    )
                }
            }
        }
    }
}

@Composable
private fun bankSummarySubtitle(summary: SettingsBankSummaryUi): String {
    val parts = buildList {
        if (summary.accountCount > 0) {
            add(stringResource(R.string.settings_bank_summary_accounts, summary.accountCount))
        }
        if (summary.cardCount > 0) {
            add(stringResource(R.string.settings_bank_summary_cards, summary.cardCount))
        }
        if (summary.loanCount > 0) {
            add(stringResource(R.string.settings_bank_summary_loans, summary.loanCount))
        }
    }
    val counts = parts.joinToString(stringResource(R.string.settings_bank_summary_separator))
    return if (summary.unregisteredCount > 0) {
        stringResource(R.string.settings_bank_summary_with_unregistered, counts, summary.unregisteredCount)
    } else {
        counts.ifEmpty { stringResource(R.string.settings_banks_empty) }
    }
}
