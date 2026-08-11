package com.baraa.masroof.presentation.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baraa.masroof.application.review.ReviewDetailLoader
import com.baraa.masroof.application.review.ReviewWorkflowResult
import com.baraa.masroof.application.review.ReviewWorkflowService
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ReviewKind
import com.baraa.masroof.domain.model.ReviewStatus
import com.baraa.masroof.presentation.dashboard.MoneyUiFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReviewViewModel(
    private val reviewWorkflowService: ReviewWorkflowService,
    private val detailLoader: ReviewDetailLoader,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale("ar"))

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val summaries = detailLoader.loadSummaries()
                val items = summaries.map(::toListItem)
                _uiState.update {
                    it.copy(
                        loading = false,
                        items = items,
                        error = null,
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false, error = ReviewError.LOAD_FAILED) }
            }
        }
    }

    fun openDetail(reviewId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, message = null) }
            try {
                val detail = detailLoader.loadDetail(reviewId) ?: run {
                    _uiState.update { it.copy(loading = false, error = ReviewError.LOAD_FAILED) }
                    return@launch
                }
                val pairCandidates = if (detail.review.kind == ReviewKind.PENDING_MATCH) {
                    detailLoader.loadPairCandidates(reviewId).map(::toListItem)
                } else {
                    emptyList()
                }
                _uiState.update {
                    it.copy(
                        loading = false,
                        selectedDetail = toDetailUi(detail, pairCandidates),
                        error = null,
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false, error = ReviewError.LOAD_FAILED) }
            }
        }
    }

    fun closeDetail() {
        _uiState.update { it.copy(selectedDetail = null, message = null, error = null, actionErrorDetail = null) }
    }

    fun resolveAsFinancialType(type: FinancialTransactionType) {
        val reviewId = _uiState.value.selectedDetail?.id ?: return
        runAction {
            reviewWorkflowService.resolveAsFinancialType(reviewId, type)
        }
    }

    fun resolveAsExternalTransfer() {
        val reviewId = _uiState.value.selectedDetail?.id ?: return
        runAction {
            reviewWorkflowService.resolveTransferAsExternal(reviewId)
        }
    }

    fun resolveAsNonFinancial() {
        val reviewId = _uiState.value.selectedDetail?.id ?: return
        runAction {
            reviewWorkflowService.resolveAsNonFinancial(reviewId)
        }
    }

    fun resolveSelfTransferPair(partnerReviewId: String) {
        val currentId = _uiState.value.selectedDetail?.id ?: return
        viewModelScope.launch {
            val detail = detailLoader.loadDetail(currentId) ?: return@launch
            val (outgoingId, incomingId) = when (detail.messageFamily) {
                MessageFamily.TRANSFER_OUT -> currentId to partnerReviewId
                MessageFamily.TRANSFER_IN -> partnerReviewId to currentId
                else -> return@launch
            }
            runAction {
                reviewWorkflowService.resolveSelfTransferPair(outgoingId, incomingId)
            }
        }
    }

    private fun runAction(action: suspend () -> ReviewWorkflowResult) {
        viewModelScope.launch {
            _uiState.update { it.copy(resolving = true, error = null, message = null, actionErrorDetail = null) }
            try {
                when (val result = action()) {
                    is ReviewWorkflowResult.Success -> {
                        val resolved = result.review.status == ReviewStatus.RESOLVED
                        refreshAfterAction(
                            message = if (resolved) ReviewMessage.RESOLVED else ReviewMessage.STILL_NEEDS_REVIEW,
                            closeDetail = resolved,
                        )
                    }
                    is ReviewWorkflowResult.Rejected -> {
                        _uiState.update {
                            it.copy(
                                resolving = false,
                                error = ReviewError.ACTION_FAILED,
                                actionErrorDetail = result.reason,
                            )
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(resolving = false, error = ReviewError.ACTION_FAILED, actionErrorDetail = null)
                }
            }
        }
    }

    private suspend fun refreshAfterAction(message: ReviewMessage, closeDetail: Boolean) {
        val summaries = detailLoader.loadSummaries()
        _uiState.update {
            it.copy(
                loading = false,
                resolving = false,
                items = summaries.map(::toListItem),
                selectedDetail = if (closeDetail) null else it.selectedDetail,
                message = message,
                error = null,
            )
        }
    }

    private fun toListItem(summary: ReviewDetailLoader.ReviewSummary): ReviewListItemUi {
        val review = summary.review
        val dateLabel = summary.receivedAt?.atZone(zoneId)?.toLocalDate()?.let(dateFormatter::format)
            ?: "—"
        return ReviewListItemUi(
            id = review.id,
            kind = review.kind,
            kindLabelRes = review.kind.toUiLabelRes(),
            title = summary.title ?: "—",
            amountLabel = summary.amount?.let(MoneyUiFormatter::format),
            dateLabel = dateLabel,
            reasonLabel = review.reasons.firstOrNull().orEmpty(),
        )
    }

    private fun toDetailUi(
        detail: ReviewDetailLoader.ReviewDetail,
        pairCandidates: List<ReviewListItemUi>,
    ): ReviewDetailUi {
        val review = detail.review
        val family = detail.messageFamily
        val isTransferOut = family == MessageFamily.TRANSFER_OUT
        val isTransferIn = family == MessageFamily.TRANSFER_IN
        val pendingMatch = review.kind == ReviewKind.PENDING_MATCH
        val dismissNonFinancial = shouldOfferNonFinancialDismiss(
            messageFamily = family,
            reasons = review.reasons,
            body = detail.body.orEmpty(),
        )
        val dateLabel = detail.receivedAt?.atZone(zoneId)?.toLocalDate()?.let(dateFormatter::format)
            ?: "—"
        return ReviewDetailUi(
            id = review.id,
            kind = review.kind,
            kindLabelRes = review.kind.toUiLabelRes(),
            sender = detail.sender,
            body = detail.body.orEmpty(),
            dateLabel = dateLabel,
            messageFamilyLabel = family?.name,
            messageFamily = family,
            amountLabel = detail.amount?.let(MoneyUiFormatter::format),
            merchant = detail.merchant,
            counterparty = detail.counterparty,
            reasonLabels = review.reasons,
            pairCandidates = pairCandidates,
            showExternalTransferAction = pendingMatch && isTransferOut,
            showIncomingIncomeAction = pendingMatch && isTransferIn,
            showFinancialTypeActions = review.kind == ReviewKind.NEEDS_REVIEW && !dismissNonFinancial,
            showDismissNonFinancialAction = dismissNonFinancial,
        )
    }
}
