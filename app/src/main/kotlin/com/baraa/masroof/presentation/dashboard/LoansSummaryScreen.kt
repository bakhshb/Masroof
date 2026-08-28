package com.baraa.masroof.presentation.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.LoanOverview
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofCardAccent
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSectionTitle
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

@Composable
fun LoansSummaryRoute(
    viewModel: DashboardViewModel,
    initialSelectedLoanKey: String? = null,
    onInitialSelectionConsumed: () -> Unit = {},
    onBack: () -> Unit,
    onManageLoans: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenAllTransactions: (TransactionListFilterState) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var selectedLoanKey by rememberSaveable { mutableStateOf<String?>(null) }
    val followedLoans = state.followedLoansOverview()

    androidx.compose.runtime.LaunchedEffect(initialSelectedLoanKey) {
        if (initialSelectedLoanKey != null) {
            selectedLoanKey = initialSelectedLoanKey
            onInitialSelectionConsumed()
        }
    }

    val selectedLoan = selectedLoanKey?.let { key ->
        followedLoans?.loans?.find { LoanOwnershipKey.of(it) == key }
    }

    BackHandler {
        when {
            selectedLoan != null -> selectedLoanKey = null
            else -> onBack()
        }
    }

    when {
        selectedLoan != null -> {
            LoanDetailScreen(
                loan = selectedLoan,
                state = state,
                onBack = { selectedLoanKey = null },
                onOpenTransaction = onOpenTransaction,
                onViewAllTransactions = {
                    val loanContainerId = FinancialContainerIdFactory.loanId(
                        selectedLoan.bank,
                        selectedLoan.loanType,
                    )
                    val loanTransactionIds = state.allTransactions
                        .filter { tx ->
                            tx.type == FinancialTransactionType.LOAN_REPAYMENT &&
                                tx.destinationContainerId == loanContainerId
                        }
                        .map { it.id }
                        .toSet()
                    onOpenAllTransactions(
                        TransactionListFilterState(transactionIds = loanTransactionIds),
                    )
                },
            )
        }

        else -> {
            LoansSummaryScreen(
                state = state,
                onBack = onBack,
                onManageLoans = onManageLoans,
                onOpenLoan = { loan -> selectedLoanKey = LoanOwnershipKey.of(loan) },
            )
        }
    }
}

@Composable
fun LoansSummaryScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onManageLoans: () -> Unit,
    onOpenLoan: (LoanOverview) -> Unit,
) {
    val followedLoans = state.followedLoansOverview()

    DashboardSummaryScaffold(
        title = stringResource(R.string.dashboard_loans_summary_screen_title),
        state = state,
        onBack = onBack,
    ) { contentModifier ->
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (followedLoans == null || !followedLoans.hasContent) {
                Text(
                    stringResource(R.string.dashboard_loans_summary_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DashboardSummaryHeroCard(spec = loansSummaryHeroSpec(overview = followedLoans))
                LoansSummaryHeader(
                    loanCount = followedLoans.loans.size,
                    onManageLoans = onManageLoans,
                )
                followedLoans.loans.forEach { loan ->
                    LoanCompactListRow(
                        loan = loan,
                        onClick = { onOpenLoan(loan) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoansSummaryHeader(
    loanCount: Int,
    onManageLoans: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MasroofSectionTitle(
            title = stringResource(R.string.dashboard_loans_count_label, loanCount),
        )
        TextButton(onClick = onManageLoans) {
            Text(stringResource(R.string.dashboard_manage_loans))
        }
    }
}

@Composable
fun LoanCompactListRow(
    loan: LoanOverview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    val paymentLabel = if (loan.salaryPeriodLabel != null) {
        stringResource(R.string.dashboard_loan_period_payment, loan.salaryPeriodLabel)
    } else {
        stringResource(R.string.dashboard_loan_period_payment_fallback)
    }

    MasroofCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        accent = MasroofCardAccent.Credit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(extended.liability.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MasroofIcons.savings,
                    contentDescription = null,
                    tint = extended.liability,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    loan.displayLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.dashboard_loan_remaining),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    loan.remainingBalance?.let { formatLocalizedMoney(it) }
                        ?: stringResource(R.string.dashboard_value_unavailable),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = extended.liability,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    paymentLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatLocalizedMoney(loan.salaryPeriodPayment),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = MasroofIcons.periodNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(20.dp),
                )
            }
        }
    }
}
