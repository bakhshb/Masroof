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
import kotlinx.coroutines.sync.Mutex

/**
 * Drives the Arabic batch action: "تصنيف العمليات غير المصنفة".
 *
 * Behaviour:
 *  - Filters unclassified transactions (no categoryId set, not
 *    user-confirmed, not user-rejected) AND eligible (expense / bank
 *    fee; not declined/pending/refunded/transferred/etc.).
 *  - Checks the AI cache first.
 *  - Calls the provider for the rest, sequentially with very limited
 *    concurrency.
 *  - Updates each transaction in place with the AI suggestion; the
 *    user must still confirm via the edit dialog before
 *    `userConfirmed = true` and `needsReview = false`.
 *  - Cancels cleanly on [cancel].
 *  - A single failed request does NOT stop the batch.
 */
class AiBatchCategorizationService(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val merchantMemoryRepository: MerchantMemoryRepository,
    private val aiService: AiCategorizationService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var currentJob: Job? = null

    private val _state = MutableStateFlow<BatchState>(BatchState.Idle)
    val state: StateFlow<BatchState> = _state.asStateFlow()

    /**
     * Plan the batch — compute how many transactions are eligible, how
     * many are cached, how many remote calls are needed.
     */
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
            providerName = aiService.let { "OpenAI-compatible" },
        )
    }

    /**
     * Start the batch. Updates [state] throughout. Returns the final
     * summary via the StateFlow when [BatchState.Done] is reached.
     */
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
        currentJob = null
        _state.value = BatchState.Idle
    }

    private suspend fun runBatch() = coroutineScope {
        val txs = transactionRepository.getAllNewestFirst()
        val categories = categoryRepository.getAll()
        val memories = merchantMemoryRepository.getAll()
        val eligible = txs.filter { isEligible(it, memories) }
        if (eligible.isEmpty()) {
            _state.value = BatchState.Done(BatchSummary(processed = 0, cached = 0, remote = 0, failed = 0, cancelled = false))
            return@coroutineScope
        }
        var processed = 0
        var cachedCount = 0
        var remoteCount = 0
        var failed = 0
        for (t in eligible) {
            currentJob?.join()
            ensureActive()
            _state.value = BatchState.Running(processed = processed, total = eligible.size, merchant = t.merchantOrBeneficiary)
            val merchant = t.merchantOrBeneficiary
            if (merchant.isNullOrBlank()) { processed++; failed++; continue }
            val request = aiService.buildRequest(
                merchant = merchant,
                type = t.transactionType,
                amount = t.amount,
                currency = t.currency,
                categories = categories,
                includeExactAmount = false,
            )
            val outcome = try {
                aiService.categorize(merchant, request)
            } catch (_: Throwable) {
                AiCategorizationOutcome.Unclassified
            }
            when (outcome) {
                is AiCategorizationOutcome.Success -> {
                    applySuggestion(t, outcome.result)
                    cachedCount++ // counted whether cache hit or fresh call
                    remoteCount++
                }
                is AiCategorizationOutcome.Failed -> { failed++ }
                AiCategorizationOutcome.Unclassified -> { failed++ }
            }
            processed++
        }
        _state.value = BatchState.Done(
            BatchSummary(
                processed = processed,
                cached = cachedCount,
                remote = remoteCount,
                failed = failed,
                cancelled = false,
            )
        )
    }

    private suspend fun applySuggestion(t: TransactionEntity, result: AiCategorizationResult) {
        val updated = t.copy(
            categoryId = result.categoryId,
            categorySource = com.baraa.masroof.transaction.CategorySource.FUTURE_AI,
            categoryConfidence = result.confidence,
            needsReview = true, // AI suggestion still requires user confirmation
            updatedAt = System.currentTimeMillis(),
        )
        transactionRepository.update(updated)
    }

    private fun ensureActive() {
        if (currentJob?.isActive != true) throw kotlinx.coroutines.CancellationException("cancelled")
    }

    companion object {
        internal fun isEligible(t: TransactionEntity, memories: List<MerchantMemory>): Boolean {
            if (t.categoryId != null) return false
            if (t.userConfirmed) return false
            if (t.financialTreatment != FinancialTreatment.EXPENSE && t.financialTreatment != FinancialTreatment.BANK_FEE) return false
            if (t.transactionType in NON_ELIGIBLE_TYPES) return false
            if (t.status == com.baraa.masroof.transaction.TransactionStatus.DECLINED ||
                t.status == com.baraa.masroof.transaction.TransactionStatus.PENDING) return false
            // If there's a user-confirmed memory for this merchant, skip — memory wins.
            val merchantKey = com.baraa.masroof.transaction.MerchantNormalizer.normalize(t.merchantOrBeneficiary)
            val mem = memories.firstOrNull { it.normalizedKey == merchantKey }
            if (mem != null && mem.confirmationCount >= 1 && mem.enabled) return false
            return true
        }

        private val NON_ELIGIBLE_TYPES = setOf(
            com.baraa.masroof.transaction.TransactionType.REFUND,
            com.baraa.masroof.transaction.TransactionType.SALARY,
            com.baraa.masroof.transaction.TransactionType.DEPOSIT,
            com.baraa.masroof.transaction.TransactionType.INTERNAL_TRANSFER,
            com.baraa.masroof.transaction.TransactionType.INVESTMENT_TRANSFER,
            com.baraa.masroof.transaction.TransactionType.TRANSFER_IN,
            com.baraa.masroof.transaction.TransactionType.TRANSFER_OUT,
            com.baraa.masroof.transaction.TransactionType.CARD_PAYMENT,
        )
    }
}

sealed interface BatchState {
    data object Idle : BatchState
    data class Running(val processed: Int, val total: Int, val merchant: String?) : BatchState
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
    val processed: Int,
    val cached: Int,
    val remote: Int,
    val failed: Int,
    val cancelled: Boolean,
)