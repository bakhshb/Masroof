package com.baraa.masroof.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baraa.masroof.application.dashboard.DashboardService
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class DashboardViewModel(
    private val dashboardService: DashboardService,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var activePeriod: FinancialPeriod =
        FinancialPeriodPolicy.periodContaining(LocalDate.now(clock))

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", Locale("ar"))

    init {
        refresh()
    }

    fun refresh() {
        load(activePeriod)
    }

    fun goToPreviousPeriod() {
        activePeriod = FinancialPeriodPolicy.previous(activePeriod)
        load(activePeriod)
    }

    fun goToNextPeriod() {
        activePeriod = FinancialPeriodPolicy.next(activePeriod)
        load(activePeriod)
    }

    fun goToCurrentPeriod() {
        activePeriod = FinancialPeriodPolicy.periodContaining(LocalDate.now(clock))
        load(activePeriod)
    }

    private fun load(period: FinancialPeriod) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = true,
                    error = null,
                    period = period,
                    periodLabel = FinancialPeriodUiFormatter.formatRange(period),
                )
            }
            try {
                val overview = dashboardService.loadOverview(period)
                activePeriod = overview.period
                _uiState.update {
                    it.copy(
                        loading = false,
                        period = overview.period,
                        periodLabel = FinancialPeriodUiFormatter.formatRange(overview.period),
                        summary = overview.summary,
                        recentTransactions = overview.recentTransactions.map(::toPreview),
                        isCurrentPeriod = overview.isCurrentPeriod,
                        error = null,
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = DashboardError.LOAD_FAILED,
                    )
                }
            }
        }
    }

    private fun toPreview(tx: FinancialTransaction): TransactionPreviewUi {
        val title = tx.merchant?.takeIf { it.isNotBlank() }
            ?: tx.counterparty?.takeIf { it.isNotBlank() }
            ?: tx.type.name
        val localDate = tx.occurredAt.atZone(zoneId).toLocalDate()
        return TransactionPreviewUi(
            id = tx.id,
            title = title,
            amountLabel = MoneyUiFormatter.format(tx.amount),
            dateLabel = dateFormatter.format(localDate),
            type = tx.type,
            typeLabelResHint = tx.type,
            direction = TransactionTypePresentation.direction(tx.type),
        )
    }
}
