package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.LoanType
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.presentation.common.AccountOwnershipInlinePrompt
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBankLoansScreen(
    bank: Bank,
    state: SettingsUiState,
    onBack: () -> Unit,
    onConfirmOwned: (ManagedLoanUi) -> Unit,
    onMarkExternal: (ManagedLoanUi) -> Unit,
    onRequestStopTracking: (ManagedLoanUi) -> Unit,
    onResumeTracking: (ManagedLoanUi) -> Unit,
    onDismissStopConfirm: () -> Unit,
    onConfirmStopTracking: () -> Unit,
) {
    val bankLoans = state.loans.filter { it.bank == bank }
    val unregisteredLoans = bankLoans.filter { it.ownership == OwnershipStatus.UNKNOWN }
    val followedLoans = bankLoans.filter { it.ownership == OwnershipStatus.OWNED }
    val stoppedLoans = bankLoans.filter { it.ownership == OwnershipStatus.EXTERNAL }

    SettingsLoanStopConfirmDialog(
        target = state.stopConfirmLoanTarget,
        updating = state.updating,
        onDismiss = onDismissStopConfirm,
        onConfirm = onConfirmStopTracking,
    )

    MasroofSecondaryScaffold(
        title = stringResource(R.string.settings_bank_loans_title),
        onBack = onBack,
        backContentDescription = stringResource(R.string.settings_back),
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SettingsSpacing.sectionGap),
        ) {
            SettingsScreenHeader(
                bank = bank,
                hint = stringResource(R.string.settings_loans_hint),
            )

            if (bankLoans.isEmpty()) {
                Text(
                    stringResource(R.string.settings_loans_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (unregisteredLoans.isNotEmpty()) {
                SettingsGroupTitle(stringResource(R.string.settings_loans_unregistered))
                unregisteredLoans.forEach { loan ->
                    SettingsRegistryItemCard(
                        icon = MasroofIcons.moneyMovement,
                        bank = loan.bank,
                        title = loanDisplayLabel(loan),
                        showBankLabel = false,
                        footer = {
                            AccountOwnershipInlinePrompt(
                                enabled = !state.updating,
                                titleRes = R.string.settings_loans_unregistered,
                                onConfirmOwned = { onConfirmOwned(loan) },
                                onMarkExternal = { onMarkExternal(loan) },
                            )
                        },
                    )
                }
            }

            if (followedLoans.isNotEmpty()) {
                SettingsGroupTitle(stringResource(R.string.settings_loans_followed))
                followedLoans.forEach { loan ->
                    SettingsRegistryItemCard(
                        icon = MasroofIcons.moneyMovement,
                        bank = loan.bank,
                        title = loanDisplayLabel(loan),
                        showBankLabel = false,
                        endAction = {
                            SettingsStopTrackingButton(
                                onClick = { onRequestStopTracking(loan) },
                                enabled = !state.updating,
                                contentDescription = stringResource(R.string.settings_stop_loan_tracking),
                            )
                        },
                    )
                }
            }

            if (stoppedLoans.isNotEmpty()) {
                SettingsGroupTitle(stringResource(R.string.settings_loans_stopped))
                stoppedLoans.forEach { loan ->
                    SettingsRegistryItemCard(
                        icon = MasroofIcons.moneyMovement,
                        bank = loan.bank,
                        title = loanDisplayLabel(loan),
                        showBankLabel = false,
                        endAction = {
                            SettingsResumeTrackingButton(
                                onClick = { onResumeTracking(loan) },
                                enabled = !state.updating,
                            )
                        },
                    )
                }
            }

            state.error?.let {
                Text(
                    stringResource(R.string.settings_update_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SettingsLoanStopConfirmDialog(
    target: ManagedLoanUi?,
    updating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    target ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = MasroofIcons.warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringResource(R.string.settings_stop_loan_confirm_title)) },
        text = {
            Text(
                stringResource(
                    R.string.settings_stop_loan_confirm_body,
                    loanDisplayLabel(target),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !updating) {
                Text(stringResource(R.string.settings_stop_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
private fun loanDisplayLabel(loan: ManagedLoanUi): String {
    val custom = loan.displayName?.trim()?.takeIf { it.isNotEmpty() }
    return custom ?: loanTypeLabel(loan.loanType)
}

@Composable
private fun loanTypeLabel(loanType: LoanType): String =
    when (loanType) {
        LoanType.PERSONAL -> stringResource(R.string.settings_loan_type_personal)
        LoanType.AUTO -> stringResource(R.string.settings_loan_type_auto)
        LoanType.MORTGAGE -> stringResource(R.string.settings_loan_type_mortgage)
    }
