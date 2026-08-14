package com.baraa.masroof.presentation.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.application.locale.AppLocaleRepository
import androidx.lifecycle.viewModelScope
import com.baraa.masroof.application.dashboard.DashboardOverviewLoader
import com.baraa.masroof.application.transaction.ReclassificationResult
import com.baraa.masroof.application.transaction.TransactionReclassificationService
import com.baraa.masroof.domain.ids.FinancialContainerIdParser
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import com.baraa.masroof.domain.repository.CardRegistryRepository
import com.baraa.masroof.sms.scanner.SmsScanFailure
import com.baraa.masroof.sms.scanner.SmsScanResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.baraa.masroof.presentation.locale.AppLocaleContext
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class DashboardViewModel(
    private val overviewLoader: DashboardOverviewLoader,
    private val cardRegistryRepository: CardRegistryRepository,
    private val rescanService: suspend () -> SmsScanResult,
    private val reclassificationService: TransactionReclassificationService,
    private val permissionStateProvider: () -> Boolean,
    private val appContext: Context,
    private val appLocaleRepository: AppLocaleRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var activePeriod: FinancialPeriod =
        FinancialPeriodPolicy.periodContaining(LocalDate.now(clock))

    private var loadJob: Job? = null
    private var rescanJob: Job? = null

    private val languageTag: String
        get() = appLocaleRepository.getLanguageTag()

    private val dateFormatter: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("d MMM", AppLocale.displayLocale(languageTag))

    private fun localizedContext(): Context =
        AppLocaleContext.wrap(appContext, languageTag)

    companion object {
        const val RECENT_TRANSACTION_LIMIT: Int = 5
    }

    fun refresh() {
        syncSmsPermissionState()
        load(activePeriod, preserveSelectionId = _uiState.value.selectedTransactionId)
    }

    /**
     * Pull-to-refresh: re-import inbox SMS when permission is granted, then reload the dashboard.
     */
    fun refreshWithSmsImport() {
        syncSmsPermissionState()
        if (!permissionStateProvider()) {
            refresh()
            return
        }
        if (rescanJob?.isActive == true) return
        rescanJob = viewModelScope.launch {
            _uiState.update { it.copy(rescanning = true, rescanStatus = null) }
            try {
                val result = rescanService()
                _uiState.update {
                    it.copy(
                        rescanning = false,
                        rescanStatus = mapRescanStatus(result),
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(rescanning = false, rescanStatus = SmsRescanStatus.FAILED)
                }
            }
            refresh()
        }
    }

    fun onAppResumed() {
        val granted = permissionStateProvider()
        val wasGranted = _uiState.value.smsPermissionGranted
        _uiState.update { it.copy(smsPermissionGranted = granted) }
        if (granted && !wasGranted) {
            rescanSms()
        } else {
            refresh()
        }
    }

    fun clearRescanStatus() {
        _uiState.update { it.copy(rescanStatus = null) }
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
        syncSmsPermissionState()
        if (!permissionStateProvider()) {
            _uiState.update { it.copy(rescanStatus = SmsRescanStatus.PERMISSION_DENIED) }
            return
        }
        if (rescanJob?.isActive == true) return
        rescanJob = viewModelScope.launch {
            _uiState.update { it.copy(rescanning = true, rescanStatus = null) }
            try {
                val result = rescanService()
                _uiState.update {
                    it.copy(
                        rescanning = false,
                        rescanStatus = mapRescanStatus(result),
                    )
                }
                refresh()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update { it.copy(rescanning = false, rescanStatus = SmsRescanStatus.FAILED) }
            }
        }
    }

    private fun syncSmsPermissionState() {
        _uiState.update { it.copy(smsPermissionGranted = permissionStateProvider()) }
    }

    private fun mapRescanStatus(result: SmsScanResult): SmsRescanStatus =
        when (result.failure) {
            SmsScanFailure.PermissionDenied -> SmsRescanStatus.PERMISSION_DENIED
            is SmsScanFailure.ProviderError -> SmsRescanStatus.FAILED
            null -> when {
                result.parsed == 0 && result.scanned == 0 -> SmsRescanStatus.NO_MESSAGES
                result.parsed == 0 && result.notRelevant == result.scanned -> SmsRescanStatus.NO_BANK_SMS
                result.parsed == 0 -> SmsRescanStatus.NO_TRANSACTIONS
                else -> SmsRescanStatus.OK
            }
        }

    fun openTransactionDetail(transactionId: String) {
        _uiState.update {
            it.copy(
                selectedTransactionId = transactionId,
                reclassifySuccess = false,
                reclassifyError = null,
            )
        }
    }

    fun closeTransactionDetail() {
        _uiState.update {
            it.copy(
                selectedTransactionId = null,
                reclassifying = false,
                reclassifySuccess = false,
                reclassifyError = null,
            )
        }
    }

    fun reclassifySelectedTransaction(newType: FinancialTransactionType) {
        val transactionId = _uiState.value.selectedTransactionId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(reclassifying = true, reclassifySuccess = false, reclassifyError = null) }
            when (val result = reclassificationService.reclassify(transactionId, newType)) {
                is ReclassificationResult.Success -> {
                    refreshPreservingSelection()
                    _uiState.update {
                        it.copy(
                            reclassifying = false,
                            reclassifySuccess = true,
                            reclassifyError = null,
                        )
                    }
                }
                is ReclassificationResult.Rejected -> {
                    _uiState.update {
                        it.copy(
                            reclassifying = false,
                            reclassifyError = result.reason,
                        )
                    }
                }
            }
        }
    }

    private fun refreshPreservingSelection() {
        refresh()
    }

    private fun periodPresentation(period: FinancialPeriod): Pair<String, String?> {
        val adjustment = FinancialPeriodPolicy.salaryCycleStartAdjustment(period.startDate)
        val context = localizedContext()
        return FinancialPeriodUiFormatter.formatSalaryPeriodTitle(context, period) to
            FinancialPeriodUiFormatter.formatAdjustmentHint(context, adjustment)
    }

    private fun load(period: FinancialPeriod, preserveSelectionId: String? = null) {
        val samePeriodRefresh =
            _uiState.value.period == period && _uiState.value.summary?.period == period
        val (periodLabel, periodAdjustmentHint) = periodPresentation(period)

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { current ->
                if (samePeriodRefresh) {
                    current.copy(
                        loading = true,
                        error = null,
                        period = period,
                        periodLabel = periodLabel,
                        periodAdjustmentHint = periodAdjustmentHint,
                    )
                } else {
                    current.copy(
                        loading = true,
                        error = null,
                        period = period,
                        periodLabel = periodLabel,
                        periodAdjustmentHint = periodAdjustmentHint,
                        summary = null,
                        creditCards = null,
                        recentTransactions = emptyList(),
                        allTransactions = emptyList(),
                    )
                }
            }

            try {
                val overview = overviewLoader.loadOverview(period)
                val unknownCards = loadUnknownCards()
                val ownedCards = loadOwnedCards()
                ensureActive()
                if (period != activePeriod) {
                    return@launch
                }
                val previews = overview.transactions.map(::toPreview)
                val (loadedLabel, loadedHint) = periodPresentation(overview.period)
                _uiState.update {
                    it.copy(
                        loading = false,
                        period = overview.period,
                        periodLabel = loadedLabel,
                        periodAdjustmentHint = loadedHint,
                        summary = overview.summary,
                        creditCards = overview.creditCards,
                        recentTransactions = previews.take(RECENT_TRANSACTION_LIMIT),
                        allTransactions = previews,
                        isCurrentPeriod = overview.isCurrentPeriod,
                        error = null,
                        selectedTransactionId = preserveSelectionId,
                        unknownCards = unknownCards,
                        ownedCards = ownedCards,
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
                            periodLabel = periodLabel,
                            periodAdjustmentHint = periodAdjustmentHint,
                        )
                    } else {
                        current.copy(
                            loading = false,
                            error = DashboardError.LOAD_FAILED,
                            period = period,
                            periodLabel = periodLabel,
                            periodAdjustmentHint = periodAdjustmentHint,
                            summary = null,
                            creditCards = null,
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
        val searchText = listOfNotNull(
            tx.merchant?.trim()?.takeIf { it.isNotEmpty() },
            tx.counterparty?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" ").lowercase(Locale.getDefault())
        return TransactionPreviewUi(
            id = tx.id,
            title = title,
            amount = tx.amount,
            localDate = localDate,
            amountLabel = MoneyUiFormatter.format(tx.amount, languageTag),
            dateLabel = dateFormatter.format(localDate),
            type = tx.type,
            typeLabelResHint = tx.type,
            direction = TransactionTypePresentation.direction(tx.type),
            cardLast4 = FinancialContainerIdParser.cardLast4FromContainers(
                sourceContainerId = tx.sourceContainerId,
                destinationContainerId = tx.destinationContainerId,
            ),
            searchText = searchText,
        )
    }

    private suspend fun loadUnknownCards(): List<UnknownCardCandidateUi> =
        cardRegistryRepository.listAll()
            .filter { it.bank != Bank.UNKNOWN && it.ownership == OwnershipStatus.UNKNOWN }
            .map { UnknownCardCandidateUi(bank = it.bank, last4 = it.last4) }
            .sortedBy { it.last4 }

    private suspend fun loadOwnedCards(): List<OwnedCardUi> =
        cardRegistryRepository.listAll()
            .filter { it.bank != Bank.UNKNOWN && it.ownership == OwnershipStatus.OWNED }
            .map { OwnedCardUi(bank = it.bank, last4 = it.last4) }
            .sortedBy { it.last4 }
}
