package com.baraa.masroof.ai

import com.baraa.masroof.data.db.MerchantMemory
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.CategoryRepository
import com.baraa.masroof.data.repository.MerchantMemoryRepository
import com.baraa.masroof.data.repository.TransactionRepository
import com.baraa.masroof.transaction.FinancialTreatment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the Arabic batch action: "تصنيف العمليات غير المصنفة".
 *
 * Behaviour:
 *  - Filters unclassified transactions (no categoryId set, not
 *    user-confirmed) AND eligible (expense / bank fee; not declined/
 *    pending/refunded/transferred/etc.).
 *  - Skips transactions already covered by a user-confirmed memory.
 *  - Checks the AI cache first; on hit the suggestion is reused without
 *    a remote call.
 *  - For uncached merchants, calls the provider sequentially.
 *  - Records every suggestion in the [AiSuggestionRepository] so the
 *    user can review it from "اقتراحات التصنيف الذكي".
 *  - The transaction itself is NOT modified to `userConfirmed=true` by
 *    the batch — it remains in `needsReview` until the user accepts
 *    the suggestion.
 *  - Cancellable mid-batch via [cancel]; already-completed items
 *    remain available.
 *  - A single failure does NOT stop the batch.
 */
class AiBatchCategorizationService(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val merchantMemoryRepository: MerchantMemoryRepository,
    private val aiService: AiCategorizationService,
    private val suggestionRepository: AiSuggestionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    private val _state = MutableStateFlow<BatchState>(BatchState.Idle)
    val state: StateFlow<BatchState> = _state.asStateFlow()

    /** True iff a batch is currently in progress. UI can use this to
     *  disable the "start" button. */
    fun isActive(): Boolean = currentJob?.isActive == true

    suspend fun plan(): BatchPlan {
        val txs = transactionRepository.getAllNewestFirst()
        val categories = categoryRepository.getAll()
        val memories = merchantMemoryRepository.getAll()
        val eligible = txs.filter { isEligible(it, memories) }
        var cached = 0
        var remote = 0
        for (t in eligible) {
            val merchant = t.merchantOrBeneficiary
            if (merchant.isNullOrBlank()) {
                remote++
                continue
            }
            val hit = aiService.lookupCache(merchant) { id -> categories.any { it.id == id && it.enabled } }
            if (hit != null) cached++ else remote++
        }
        return BatchPlan(
            eligible = eligible.size,
            cached = cached,
            remote = remote,
            providerName = "OpenAI-compatible",
        )
    }

    fun start() {
        if (currentJob?.isActive == true) return
        currentJob = scope.launch {
            try {
                runBatch()
            } catch (ce: kotlinx.coroutines.CancellationException) {
                _state.value = BatchState.Idle
                throw ce
            } catch (t: Throwable) {
                _state.value = BatchState.Error(t.message ?: "unknown")
            }
        }
    }

    fun cancel() {
        currentJob?.cancel()
        // Mark the current state as canceled (without resetting the
        // progress counters if they were set by runBatch already).
        _state.value = when (val s = _state.value) {
            is BatchState.Running -> s.copy(canceled = true)
            else -> BatchState.Running(processed = 0, total = 0, merchant = null, canceled = true)
        }
    }

    private suspend fun runBatch() = coroutineScope {
        val txs = transactionRepository.getAllNewestFirst()
        val categories = categoryRepository.getAll()
        val memories = merchantMemoryRepository.getAll()
        val eligible = txs.filter { isEligible(it, memories) }
        if (eligible.isEmpty()) {
            _state.value = BatchState.Done(BatchSummary())
            return@coroutineScope
        }
        var processed = 0
        var cacheHits = 0
        var remoteCalls = 0
        var succeeded = 0
        var failed = 0
        var skipped = 0
        var canceled = false
        for (t in eligible) {
            ensureActive()
            _state.value = BatchState.Running(
                processed = processed,
                total = eligible.size,
                merchant = t.merchantOrBeneficiary,
                cacheHits = cacheHits,
                succeeded = succeeded,
                failed = failed,
                skipped = skipped,
                canceled = false,
            )
            val merchant = t.merchantOrBeneficiary
            if (merchant.isNullOrBlank()) {
                processed++
                skipped++
                continue
            }
            val request = aiService.buildRequest(
                merchant = merchant,
                type = t.transactionType,
                amount = t.amount,
                currency = t.currency,
                categories = categories,
                includeExactAmount = false,
            )
            // Check cache first.
            val cached = aiService.lookupCache(merchant) { id -> categories.any { it.id == id && it.enabled } }
            val outcome = if (cached != null) {
                // Reuse cache → wrap in a synthetic Success.
                AiCategorizationOutcome.Success(
                    AiCategorizationResult(
                        categoryId = cached.categoryId,
                        categoryName = categories.firstOrNull { it.id == cached.categoryId }?.nameAr.orEmpty(),
                        normalizedMerchantName = merchant,
                        confidence = cached.confidence,
                        explanation = cached.explanation,
                        providerName = cached.providerName,
                        modelName = cached.modelName,
                        responseVersion = cached.resultVersion,
                    )
                )
            } else {
                try {
                    aiService.categorize(merchant, request)
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    AiCategorizationOutcome.Unclassified
                }
            }
            when (outcome) {
                is AiCategorizationOutcome.Success -> {
                    if (cached != null) cacheHits++ else remoteCalls++
                    // Insert into the suggestion queue.
                    suggestionRepository.insertFromResult(t, outcome.result)
                    succeeded++
                }
                is AiCategorizationOutcome.Failed -> failed++
                AiCategorizationOutcome.Unclassified -> failed++
            }
            processed++
        }
        if (currentJob?.isCancelled == true) canceled = true
        _state.value = BatchState.Done(
            BatchSummary(
                processed = processed,
                cacheHits = cacheHits,
                remoteCalls = remoteCalls,
                succeeded = succeeded,
                failed = failed,
                skipped = skipped,
                canceled = canceled,
            ),
        )
    }

    private fun ensureActive() {
        if (currentJob?.isCancelled == true) {
            throw kotlinx.coroutines.CancellationException("cancelled")
        }
    }

    companion object {
        internal fun isEligible(t: TransactionEntity, memories: List<MerchantMemory>): Boolean {
            if (t.categoryId != null) return false
            if (t.userConfirmed) return false
            if (t.financialTreatment != FinancialTreatment.EXPENSE && t.financialTreatment != FinancialTreatment.BANK_FEE) return false
            if (t.transactionType in NON_ELIGIBLE_TYPES) return false
            if (t.status == com.baraa.masroof.transaction.TransactionStatus.DECLINED ||
                t.status == com.baraa.masroof.transaction.TransactionStatus.PENDING) return false
            val merchantKey = com.baraa.masroof.transaction.MerchantNormalizer.normalize(t.merchantOrBeneficiary)
            val mem = memories.firstOrNull { it.normalizedKey == merchantKey }
            if (mem != null && mem.confirmationCount >= 1 && mem.enabled) return false
            return true
        }

        private val NON_ELIGIBLE_TYPES = setOf(
            com.baraa.masroof.transaction.TransactionType.REFUND,
            com.baraa.masroof.transaction.TransactionType.SALARY,
            com.baraa.masroof.transaction.TransactionType.INTERNAL_TRANSFER,
            com.baraa.masroof.transaction.TransactionType.TRANSFER_IN,
            com.baraa.masroof.transaction.TransactionType.TRANSFER_OUT,
            com.baraa.masroof.transaction.TransactionType.CARD_PAYMENT,
        )
    }
}

sealed interface BatchState {
    data object Idle : BatchState
    data class Running(
        val processed: Int,
        val total: Int,
        val merchant: String?,
        val cacheHits: Int = 0,
        val succeeded: Int = 0,
        val failed: Int = 0,
        val skipped: Int = 0,
        val canceled: Boolean = false,
    ) : BatchState
    data class Done(val summary: BatchSummary) : BatchState
    data class Error(val message: String) : BatchState
}

data class BatchPlan(
    val eligible: Int,
    val cached: Int,
    val remote: Int,
    val providerName: String,
)

data class BatchSummary(
    val processed: Int = 0,
    val cacheHits: Int = 0,
    val remoteCalls: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val canceled: Boolean = false,
)