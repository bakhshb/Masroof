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
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.LoanType
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBankLoansScreen(
    bank: Bank,
    state: SettingsUiState,
    onBack: () -> Unit,
) {
    val bankLoans = state.loans.filter { it.bank == bank }

    MasroofSecondaryScaffold(
        title = stringResource(R.string.settings_bank_loans_title),
        onBack = onBack,
        backContentDescription = stringResource(R.string.settings_back),
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                settingsBankLabel(bank),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.settings_loans_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (bankLoans.isEmpty()) {
                Text(
                    stringResource(R.string.settings_loans_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                bankLoans.forEach { loan ->
                    SettingsRegistryItemCard(
                        icon = MasroofIcons.moneyMovement,
                        bank = loan.bank,
                        title = loanDisplayLabel(loan),
                        showBankLabel = false,
                    )
                }
            }
        }
    }
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
