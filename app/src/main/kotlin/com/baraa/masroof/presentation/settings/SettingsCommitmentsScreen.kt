package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
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
import com.baraa.masroof.presentation.theme.MasroofSpacing

@Composable
fun SettingsCommitmentsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onOpenCommitment: (ManagedCommitmentUi) -> Unit,
    onListTabChange: (CommitmentsListTab) -> Unit = {},
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MasroofSpacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap),
            ) {
                FilterChip(
                    selected = state.commitmentsListTab == CommitmentsListTab.ACTIVE,
                    onClick = { onListTabChange(CommitmentsListTab.ACTIVE) },
                    label = { Text(stringResource(R.string.settings_commitments_tab_active)) },
                )
                FilterChip(
                    selected = state.commitmentsListTab == CommitmentsListTab.HISTORY,
                    onClick = { onListTabChange(CommitmentsListTab.HISTORY) },
                    label = { Text(stringResource(R.string.settings_commitments_tab_history)) },
                )
            }

            val commitments = when (state.commitmentsListTab) {
                CommitmentsListTab.ACTIVE -> state.activeCommitments
                CommitmentsListTab.HISTORY -> state.disabledCommitments
            }

            if (commitments.isEmpty()) {
                Text(
                    when (state.commitmentsListTab) {
                        CommitmentsListTab.ACTIVE -> stringResource(R.string.settings_commitments_active_empty)
                        CommitmentsListTab.HISTORY -> stringResource(R.string.settings_commitments_history_empty)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MasroofSpacing.screenHorizontal),
                )
                return@Column
            }

            commitments.forEach { commitment ->
                SettingsNavRow(
                    icon = if (commitment.active) MasroofIcons.calendar else MasroofIcons.warning,
                    title = commitment.name,
                    subtitle = commitmentSubtitle(commitment),
                    onClick = { onOpenCommitment(commitment) },
                )
            }
        }
    }
}

@Composable
private fun commitmentSubtitle(commitment: ManagedCommitmentUi): String {
    if (!commitment.active && commitment.pauseIntervals.isNotEmpty()) {
        val latest = commitment.pauseIntervals.last()
        val pausedLabel = formatLocalizedTransactionDate(latest.pausedAt)
        val resumedLabel = latest.resumedAt?.let { formatLocalizedTransactionDate(it) }
        return if (resumedLabel != null) {
            stringResource(
                R.string.settings_commitment_pause_interval_resumed,
                pausedLabel,
                resumedLabel,
            )
        } else {
            stringResource(R.string.settings_commitment_pause_interval_open, pausedLabel)
        }
    }
    return buildString {
        append(formatLocalizedMoney(commitment.amount))
        append(" · ")
        append(formatLocalizedTransactionDate(commitment.transactionDate))
    }
}
