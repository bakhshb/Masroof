package com.baraa.masroof.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baraa.masroof.application.dashboard.DashboardOverviewLoader
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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
    private val overviewLoader: DashboardOverviewLoader,
    private val rescanService: suspend () -> com.baraa.masroof.sms.scanner.SmsScanResult,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var activePeriod: FinancialPeriod =
        FinancialPeriodPolicy.periodContaining(LocalDate.now(clock))

    private var loadJob: Job? = null
    private var rescanJob: Job? = null

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", Locale("ar"))

    companion object {
        const val RECENT_TRANSACTION_LIMIT: Int = 5
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

    fun rescanSms() {
        if (rescanJob?.isActive == true) return
        rescanJob = viewModelScope.launch {
            _uiState.update { it.copy(rescanning = true, rescanStatus = null) }
            try {
                val result = rescanService()
                val status = when {
                    result.failure != null -> SmsRescanStatus.FAILED
                    result.parsed == 0 && result.scanned == 0 -> SmsRescanStatus.NO_MESSAGES
                    result.parsed == 0 && result.notRelevant == result.scanned -> SmsRescanStatus.NO_BANK_SMS
                    result.parsed == 0 -> SmsRescanStatus.NO_TRANSACTIONS
                    else -> SmsRescanStatus.OK
                }
                _uiState.update { it.copy(rescanning = false, rescanStatus = status) }
                refresh()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update { it.copy(rescanning = false, rescanStatus = SmsRescanStatus.FAILED) }
            }
        }
    }

    private fun load(period: FinancialPeriod) {
        val samePeriodRefresh =
            _uiState.value.period == period && _uiState.value.summary?.period == period

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { current ->
                if (samePeriodRefresh) {
                    current.copy(
                        loading = true,
                        error = null,
                        period = period,
                        periodLabel = FinancialPeriodUiFormatter.formatRange(period),
                    )
                } else {
                    current.copy(
                        loading = true,
                        error = null,
                        period = period,
                        periodLabel = FinancialPeriodUiFormatter.formatRange(period),
                        summary = null,
                        recentTransactions = emptyList(),
                        allTransactions = emptyList(),
                    )
                }
            }

            try {
                val overview = overviewLoader.loadOverview(period)
                ensureActive()
                if (period != activePeriod) {
                    return@launch
                }
                val previews = overview.transactions.map(::toPreview)
                _uiState.update {
                    it.copy(
                        loading = false,
                        period = overview.period,
                        periodLabel = FinancialPeriodUiFormatter.formatRange(overview.period),
                        summary = overview.summary,
                        recentTransactions = previews.take(RECENT_TRANSACTION_LIMIT),
                        allTransactions = previews,
                        isCurrentPeriod = overview.isCurrentPeriod,
                        error = null,
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                if (period != activePeriod) {
                    return@launch
                }
                _uiState.update { current ->
                    if (samePeriodRefresh) {
                        current.copy(
                            loading = false,
                            error = DashboardError.LOAD_FAILED,
                            period = period,
                            periodLabel = FinancialPeriodUiFormatter.formatRange(period),
                        )
                    } else {
                        current.copy(
                            loading = false,
                            error = DashboardError.LOAD_FAILED,
                            period = period,
                            periodLabel = FinancialPeriodUiFormatter.formatRange(period),
                            summary = null,
                            recentTransactions = emptyList(),
                            allTransactions = emptyList(),
                        )
                    }
                }
            }
        }
    }

    private fun toPreview(tx: FinancialTransaction): TransactionPreviewUi {
        val title = tx.merchant?.takeIf { it.isNotBlank() }
            ?: tx.counterparty?.takeIf { it.isNotBlank() }
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
