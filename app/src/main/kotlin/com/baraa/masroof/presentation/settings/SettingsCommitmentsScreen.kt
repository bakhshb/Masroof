package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.locale.formatLocalizedTransactionDate

@Composable
fun SettingsCommitmentsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onOpenCommitment: (ManagedCommitmentUi) -> Unit,
) {
    MasroofSecondaryScaffold(
        title = stringResource(R.string.settings_commitments_section),
        onBack = onBack,
        backContentDescription = stringResource(R.string.settings_back),
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SettingsSpacing.sectionGap),
        ) {
            if (state.activeCommitments.isEmpty() && state.disabledCommitments.isEmpty()) {
                Text(
                    stringResource(R.string.settings_commitments_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            state.activeCommitments.forEach { commitment ->
                SettingsNavRow(
                    icon = MasroofIcons.calendar,
                    title = commitment.name,
                    subtitle = buildString {
                        append(formatLocalizedMoney(commitment.amount))
                        append(" · ")
                        append(formatLocalizedTransactionDate(commitment.transactionDate))
                    },
                    onClick = { onOpenCommitment(commitment) },
                )
            }

            if (state.disabledCommitments.isNotEmpty()) {
                SettingsGroupTitle(stringResource(R.string.settings_commitments_disabled_section))
                state.disabledCommitments.forEach { commitment ->
                    SettingsNavRow(
                        icon = MasroofIcons.warning,
                        title = commitment.name,
                        subtitle = stringResource(R.string.settings_commitment_disabled_badge),
                        onClick = { onOpenCommitment(commitment) },
                    )
                }
            }
        }
    }
}
