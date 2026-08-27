package com.baraa.masroof.application.transaction

import com.baraa.masroof.application.review.EffectiveParsedEventProvider
import com.baraa.masroof.domain.assembly.TransactionAssembler
import com.baraa.masroof.domain.matching.TransactionMatcher
import com.baraa.masroof.domain.matching.TransferMatchCandidate
import com.baraa.masroof.domain.matching.TransferMatchPair
import com.baraa.masroof.domain.model.BankNetworkType
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.ReviewKind
import com.baraa.masroof.domain.model.ReviewResolutionKind
import com.baraa.masroof.domain.model.ReviewStatus
import com.baraa.masroof.domain.repository.ReviewRepository
import com.baraa.masroof.domain.rules.InformationalMessagePolicy
import com.baraa.masroof.domain.ownership.OwnershipResolver
import com.baraa.masroof.domain.repository.FinancialTransactionRepository
import com.baraa.masroof.domain.repository.FinancialTransactionSaveResult
import com.baraa.masroof.domain.repository.RawSmsRepository
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import com.baraa.masroof.parsing.repository.ParsedEventRepository

/**
 * Operational summary of a reconciliation pass.
 */
data class ReconciliationSummary(
    val assembledSingle: Int = 0,
    val matchedPairs: Int = 0,
    val pendingMatch: Int = 0,
    val needsReview: Int = 0,
    val ignored: Int = 0,
    val alreadyLinked: Int = 0,
    val failed: Int = 0,
)

/**
 * Orchestrates ownership-aware matching/assembly and FinancialTransaction persistence.
 *
 * When [effectiveParsedEventProvider] is present, reconciliation uses corrected
 * projections without mutating stored ParsedEvent rows.
 */
class TransactionReconciliationService(
    private val parsedEventRepository: ParsedEventRepository,
    private val rawSmsRepository: RawSmsRepository,
    private val financialTransactionRepository: FinancialTransactionRepository,
    private val ownershipResolver: OwnershipResolver,
    private val effectiveParsedEventProvider: EffectiveParsedEventProvider? = null,
    private val reviewRepository: ReviewRepository? = null,
) {
    suspend fun reconcileStoredEvents(): ReconciliationSummary =
        reconcileStoredEventsDetailed().summary

    suspend fun reconcileStoredEventsDetailed(): ReconciliationReport {
        val records = loadRecords()
        return reconcileRecordsDetailed(records)
    }

    /**
     * Reconcile after a newly saved ParsedEvent. Failures are swallowed by callers
     * that treat P8 as derived processing.
     */
    suspend fun reconcileAfterParsedEvent(event: ParsedEvent): ReconciliationSummary =
        reconcileAfterParsedEventDetailed(event).summary

    suspend fun reconcileAfterParsedEventDetailed(event: ParsedEvent): ReconciliationReport {
        // Full backlog reconcile keeps pair matching correct after live ingest.
        val records = loadRecords()
        return reconcileRecordsDetailed(records)
    }

    private suspend fun loadRecords(): List<ParsedEventRecord> =
        effectiveParsedEventProvider?.listAllEffective()
            ?: parsedEventRepository.listAll()

    private suspend fun reconcileRecordsDetailed(
        records: List<ParsedEventRecord>,
    ): ReconciliationReport {
        var assembledSingle = 0
        var matchedPairs = 0
        var pendingMatch = 0
        var needsReview = 0
        var ignored = 0
        var alreadyLinked = 0
        var failed = 0

        val unresolvedTransfers = mutableListOf<TransferMatchCandidate>()
        val reviewCandidates = mutableListOf<ReconciliationReviewCandidate>()
        val settledRawSmsIds = linkedSetOf<String>()

        for (record in records) {
            val event = record.event
            if (reviewRepository != null) {
                val review = reviewRepository.findByRawSmsId(event.rawSmsId)
                if (review?.status == ReviewStatus.RESOLVED &&
                    review.resolutionKind == ReviewResolutionKind.USER_NON_FINANCIAL
                ) {
                    ignored++
                    settledRawSmsIds += event.rawSmsId
                    continue
                }
            }
            if (financialTransactionRepository.isRawSmsLinked(event.rawSmsId)) {
                val body = rawSmsRepository.getById(event.rawSmsId)?.body.orEmpty()
                if (
                    eventShouldNotProduceTransaction(event, body) &&
                    financialTransactionRepository.deleteIfExclusiveRawSmsLink(event.rawSmsId)
                ) {
                    // Stale transaction from a prior misclassification (e.g. OTP matched as purchase).
                } else {
                    alreadyLinked++
                    settledRawSmsIds += event.rawSmsId
                    continue
                }
            }

            val receivedAt = rawSmsRepository.getById(event.rawSmsId)?.receivedAt
                ?: continue

            val sourceOwn = event.sourceAccountRef?.let { ownershipResolver.resolveAccount(it) }
                ?: OwnershipStatus.UNKNOWN
            val destOwn = event.destinationAccountRef?.let { ownershipResolver.resolveAccount(it) }
                ?: OwnershipStatus.UNKNOWN
            val cardOwn = event.cardRef?.let { ownershipResolver.resolveCard(it) }
                ?: OwnershipStatus.UNKNOWN

            when (event.messageFamily) {
                MessageFamily.TRANSFER_IN,
                MessageFamily.TRANSFER_OUT,
                -> {
                    val single = finalizeAssemblyOutcome(
                        event = event,
                        outcome = TransactionAssembler.assembleSingle(
                            event = event,
                            receivedAt = receivedAt,
                            sourceOwnership = sourceOwn,
                            destinationOwnership = destOwn,
                            cardOwnership = cardOwn,
                        ),
                    )
                    when (single) {
                        is TransactionAssembler.Outcome.Assembled -> {
                            when (
                                persist(single.transaction, single.rawSmsIds)
                            ) {
                                PersistOutcome.Saved -> {
                                    assembledSingle++
                                    settledRawSmsIds += event.rawSmsId
                                }

                                PersistOutcome.Already -> {
                                    alreadyLinked++
                                    settledRawSmsIds += event.rawSmsId
                                }

                                PersistOutcome.Failed -> failed++
                            }
                        }

                        TransactionAssembler.Outcome.PendingMatch -> {
                            unresolvedTransfers += TransferMatchCandidate(
                                event = event,
                                transactionReference = record.details.transactionReference,
                                occurredAtLocal = record.details.occurredAtLocal,
                                receivedAt = receivedAt,
                                sourceOwnership = sourceOwn,
                                destinationOwnership = destOwn,
                            )
                            pendingMatch++
                        }

                        is TransactionAssembler.Outcome.NeedsReview -> {
                            needsReview++
                            reviewCandidates += ReconciliationReviewCandidate(
                                rawSmsId = event.rawSmsId,
                                kind = ReviewKind.NEEDS_REVIEW,
                                reasons = single.reasons.ifEmpty { listOf("needs_review") },
                            )
                        }

                        TransactionAssembler.Outcome.Ignored -> {
                            ignored++
                            settledRawSmsIds += event.rawSmsId
                        }
                    }
                }

                else -> {
                    when (
                        val outcome = finalizeAssemblyOutcome(
                            event = event,
                            outcome = TransactionAssembler.assembleSingle(
                                event = event,
                                receivedAt = receivedAt,
                                sourceOwnership = sourceOwn,
                                destinationOwnership = destOwn,
                                cardOwnership = cardOwn,
                            ),
                        )
                    ) {
                        is TransactionAssembler.Outcome.Assembled -> {
                            when (persist(outcome.transaction, outcome.rawSmsIds)) {
                                PersistOutcome.Saved -> {
                                    assembledSingle++
                                    settledRawSmsIds += event.rawSmsId
                                }

                                PersistOutcome.Already -> {
                                    alreadyLinked++
                                    settledRawSmsIds += event.rawSmsId
                                }

                                PersistOutcome.Failed -> failed++
                            }
                        }

                        TransactionAssembler.Outcome.PendingMatch -> {
                            pendingMatch++
                            reviewCandidates += ReconciliationReviewCandidate(
                                rawSmsId = event.rawSmsId,
                                kind = ReviewKind.PENDING_MATCH,
                                reasons = listOf("transfer_pending_match"),
                            )
                        }

                        is TransactionAssembler.Outcome.NeedsReview -> {
                            needsReview++
                            reviewCandidates += ReconciliationReviewCandidate(
                                rawSmsId = event.rawSmsId,
                                kind = ReviewKind.NEEDS_REVIEW,
                                reasons = outcome.reasons.ifEmpty { listOf("needs_review") },
                            )
                        }

                        TransactionAssembler.Outcome.Ignored -> {
                            ignored++
                            settledRawSmsIds += event.rawSmsId
                        }
                    }
                }
            }
        }

        val stillOpen = unresolvedTransfers.filter {
            !financialTransactionRepository.isRawSmsLinked(it.event.rawSmsId)
        }

        val pairs = TransactionMatcher.findMutuallyUniquePairs(stillOpen)
        val matchedEventIds = mutableSetOf<String>()
        val matchedRawSmsIds = mutableSetOf<String>()
        for (pair in pairs) {
            if (pair.outgoing.event.id in matchedEventIds ||
                pair.incoming.event.id in matchedEventIds
            ) {
                continue
            }
            if (financialTransactionRepository.isRawSmsLinked(pair.outgoing.event.rawSmsId) ||
                financialTransactionRepository.isRawSmsLinked(pair.incoming.event.rawSmsId)
            ) {
                continue
            }

            val outSourceOwn = pair.outgoing.sourceOwnership
            val inDestOwn = pair.incoming.destinationOwnership
            when (
                val outcome = TransactionAssembler.assembleMatchedPair(
                    pair = pair,
                    outgoingSourceOwnership = outSourceOwn,
                    incomingDestinationOwnership = inDestOwn,
                )
            ) {
                is TransactionAssembler.Outcome.Assembled -> {
                    when (persist(outcome.transaction, outcome.rawSmsIds)) {
                        PersistOutcome.Saved -> {
                            matchedPairs++
                            matchedEventIds += pair.outgoing.event.id
                            matchedEventIds += pair.incoming.event.id
                            matchedRawSmsIds += pair.outgoing.event.rawSmsId
                            matchedRawSmsIds += pair.incoming.event.rawSmsId
                            settledRawSmsIds += pair.outgoing.event.rawSmsId
                            settledRawSmsIds += pair.incoming.event.rawSmsId
                            pendingMatch = (pendingMatch - 2).coerceAtLeast(0)
                        }

                        PersistOutcome.Already -> {
                            alreadyLinked += 2
                            settledRawSmsIds += pair.outgoing.event.rawSmsId
                            settledRawSmsIds += pair.incoming.event.rawSmsId
                        }

                        PersistOutcome.Failed -> failed++
                    }
                }

                else -> Unit
            }
        }

        for (candidate in stillOpen) {
            if (candidate.event.rawSmsId in matchedRawSmsIds) continue
            if (financialTransactionRepository.isRawSmsLinked(candidate.event.rawSmsId)) continue
            when (
                val unmatched = TransactionAssembler.assembleUnmatchedOwnedTransfer(
                    candidate = candidate,
                    pendingCounterparts = stillOpen.filter {
                        it.event.rawSmsId != candidate.event.rawSmsId
                    },
                )
            ) {
                is TransactionAssembler.Outcome.Assembled -> {
                    when (persist(unmatched.transaction, unmatched.rawSmsIds)) {
                        PersistOutcome.Saved -> {
                            assembledSingle++
                            pendingMatch = (pendingMatch - 1).coerceAtLeast(0)
                            settledRawSmsIds += candidate.event.rawSmsId
                        }

                        PersistOutcome.Already -> {
                            alreadyLinked++
                            pendingMatch = (pendingMatch - 1).coerceAtLeast(0)
                            settledRawSmsIds += candidate.event.rawSmsId
                        }

                        PersistOutcome.Failed -> {
                            reviewCandidates += ReconciliationReviewCandidate(
                                rawSmsId = candidate.event.rawSmsId,
                                kind = ReviewKind.PENDING_MATCH,
                                reasons = listOf("transfer_pending_match"),
                            )
                        }
                    }
                }

                else -> {
                    reviewCandidates += ReconciliationReviewCandidate(
                        rawSmsId = candidate.event.rawSmsId,
                        kind = ReviewKind.PENDING_MATCH,
                        reasons = listOf("transfer_pending_match"),
                    )
                }
            }
        }

        val upgraded = upgradeStaleIntraBankExternalPairs(records)
        matchedPairs += upgraded.matchedPairs
        assembledSingle += upgraded.assembledSingle
        alreadyLinked += upgraded.alreadyLinked
        failed += upgraded.failed
        settledRawSmsIds += upgraded.settledRawSmsIds

        val summary = ReconciliationSummary(
            assembledSingle = assembledSingle,
            matchedPairs = matchedPairs,
            pendingMatch = pendingMatch,
            needsReview = needsReview,
            ignored = ignored,
            alreadyLinked = alreadyLinked,
            failed = failed,
        )
        return ReconciliationReport(
            summary = summary,
            reviewCandidates = reviewCandidates
                .groupBy { it.rawSmsId }
                .map { (_, group) ->
                    val first = group.first()
                    ReconciliationReviewCandidate(
                        rawSmsId = first.rawSmsId,
                        kind = first.kind,
                        reasons = group.flatMap { it.reasons }.distinct().sorted(),
                    )
                },
            settledRawSmsIds = settledRawSmsIds,
        )
    }

    private suspend fun upgradeStaleIntraBankExternalPairs(
        records: List<ParsedEventRecord>,
    ): UpgradePassResult {
        val parsedById = records.associateBy { it.event.id }
        val all = financialTransactionRepository.listAll()
        val outs = all.filter { it.type == FinancialTransactionType.EXTERNAL_TRANSFER_OUT }
        val ins = all.filter { it.type == FinancialTransactionType.EXTERNAL_TRANSFER_IN }
        if (outs.isEmpty() || ins.isEmpty()) return UpgradePassResult()

        data class StaleLeg(
            val transaction: FinancialTransaction,
            val event: ParsedEvent,
            val record: ParsedEventRecord?,
        )

        fun staleLegs(
            transactions: List<FinancialTransaction>,
            family: MessageFamily,
        ): List<StaleLeg> =
            transactions.mapNotNull { transaction ->
                val event = transaction.linkedParsedEventIds
                    .mapNotNull { parsedById[it]?.event }
                    .firstOrNull { it.messageFamily == family }
                    ?: return@mapNotNull null
                if (event.bankNetworkType != BankNetworkType.INTRA_BANK) return@mapNotNull null
                StaleLeg(
                    transaction = transaction,
                    event = event,
                    record = records.find { it.event.id == event.id },
                )
            }

        val outLegs = staleLegs(outs, MessageFamily.TRANSFER_OUT)
        val inLegs = staleLegs(ins, MessageFamily.TRANSFER_IN)
        val outByEventId = outLegs.associateBy { it.event.id }
        val inByEventId = inLegs.associateBy { it.event.id }

        val candidates = buildList {
            for (leg in outLegs + inLegs) {
                val sourceOwn = leg.event.sourceAccountRef?.let { ownershipResolver.resolveAccount(it) }
                    ?: OwnershipStatus.UNKNOWN
                val destOwn = leg.event.destinationAccountRef?.let { ownershipResolver.resolveAccount(it) }
                    ?: OwnershipStatus.UNKNOWN
                add(
                    TransferMatchCandidate(
                        event = leg.event,
                        transactionReference = leg.record?.details?.transactionReference,
                        occurredAtLocal = leg.record?.details?.occurredAtLocal,
                        receivedAt = rawSmsRepository.getById(leg.event.rawSmsId)?.receivedAt
                            ?: leg.transaction.occurredAt,
                        sourceOwnership = sourceOwn,
                        destinationOwnership = destOwn,
                    ),
                )
            }
        }
        val pairs = TransactionMatcher.findMutuallyUniquePairs(candidates)
            .filter { pair ->
                pair.outgoing.sourceOwnership == OwnershipStatus.OWNED &&
                    pair.incoming.destinationOwnership == OwnershipStatus.OWNED
            }

        var matchedPairs = 0
        var assembledSingle = 0
        var alreadyLinked = 0
        var failed = 0
        val settledRawSmsIds = linkedSetOf<String>()

        for (pair in pairs) {
            val outLeg = outByEventId[pair.outgoing.event.id] ?: continue
            val inLeg = inByEventId[pair.incoming.event.id] ?: continue
            val sourceOwn = pair.outgoing.sourceOwnership
            val destOwn = pair.incoming.destinationOwnership

            when (
                val outcome = TransactionAssembler.assembleMatchedPair(
                    pair = pair,
                    outgoingSourceOwnership = sourceOwn,
                    incomingDestinationOwnership = destOwn,
                )
            ) {
                is TransactionAssembler.Outcome.Assembled -> {
                    val rawSmsIds = listOf(outLeg.event.rawSmsId, inLeg.event.rawSmsId)
                        .distinct()
                        .sorted()
                    when (upgradePersist(outcome.transaction, outcome.rawSmsIds, rawSmsIds)) {
                        UpgradePersistOutcome.Saved -> {
                            matchedPairs++
                            assembledSingle++
                            settledRawSmsIds += rawSmsIds
                        }

                        UpgradePersistOutcome.Already -> {
                            alreadyLinked += 2
                            settledRawSmsIds += rawSmsIds
                        }

                        UpgradePersistOutcome.Failed -> failed++
                    }
                }

                else -> failed++
            }
        }

        return UpgradePassResult(
            matchedPairs = matchedPairs,
            assembledSingle = assembledSingle,
            alreadyLinked = alreadyLinked,
            failed = failed,
            settledRawSmsIds = settledRawSmsIds,
        )
    }

    private enum class UpgradePersistOutcome { Saved, Already, Failed }

    private suspend fun upgradePersist(
        transaction: FinancialTransaction,
        rawSmsIds: List<String>,
        staleRawSmsIds: List<String>,
    ): UpgradePersistOutcome {
        when (persist(transaction, rawSmsIds)) {
            PersistOutcome.Saved -> return UpgradePersistOutcome.Saved
            PersistOutcome.Already -> return UpgradePersistOutcome.Already
            PersistOutcome.Failed -> Unit
        }

        return when (
            financialTransactionRepository.replaceExclusiveStaleLinks(
                transaction = transaction,
                rawSmsIds = rawSmsIds,
                staleRawSmsIds = staleRawSmsIds,
            )
        ) {
            FinancialTransactionSaveResult.Saved -> UpgradePersistOutcome.Saved
            FinancialTransactionSaveResult.AlreadyExists -> UpgradePersistOutcome.Already
            is FinancialTransactionSaveResult.Conflict -> UpgradePersistOutcome.Failed
        }
    }

    private data class UpgradePassResult(
        val matchedPairs: Int = 0,
        val assembledSingle: Int = 0,
        val alreadyLinked: Int = 0,
        val failed: Int = 0,
        val settledRawSmsIds: Set<String> = emptySet(),
    )

    private suspend fun persist(
        transaction: FinancialTransaction,
        rawSmsIds: List<String>,
    ): PersistOutcome {
        if (transaction.type == FinancialTransactionType.SELF_TRANSFER) {
            val existing = findMatchingSelfTransfer(transaction)
            if (existing != null) {
                var linkedAny = false
                for (rawSmsId in rawSmsIds) {
                    if (!financialTransactionRepository.isRawSmsLinked(rawSmsId)) {
                        if (financialTransactionRepository.linkRawSmsIfAbsent(existing.id, rawSmsId)) {
                            linkedAny = true
                        }
                    }
                }
                return if (linkedAny || rawSmsIds.all { financialTransactionRepository.isRawSmsLinked(it) }) {
                    PersistOutcome.Already
                } else {
                    PersistOutcome.Failed
                }
            }
        }
        return when (financialTransactionRepository.save(transaction, rawSmsIds)) {
            FinancialTransactionSaveResult.Saved -> PersistOutcome.Saved
            FinancialTransactionSaveResult.AlreadyExists -> PersistOutcome.Already
            is FinancialTransactionSaveResult.Conflict -> PersistOutcome.Failed
        }
    }

    private suspend fun findMatchingSelfTransfer(
        transaction: FinancialTransaction,
    ): FinancialTransaction? {
        val source = transaction.sourceContainerId ?: return null
        val dest = transaction.destinationContainerId ?: return null
        return financialTransactionRepository.listAll().firstOrNull { existing ->
            existing.id != transaction.id &&
                existing.type == FinancialTransactionType.SELF_TRANSFER &&
                existing.sourceContainerId == source &&
                existing.destinationContainerId == dest &&
                existing.amount == transaction.amount
        }
    }

    private fun eventShouldNotProduceTransaction(event: ParsedEvent, smsBody: String): Boolean {
        when (event.messageFamily) {
            MessageFamily.OTP,
            MessageFamily.BALANCE_NOTICE,
            MessageFamily.NON_FINANCIAL,
            -> return true

            else -> return InformationalMessagePolicy.shouldAutoIgnore(event, smsBody)
        }
    }

    private suspend fun finalizeAssemblyOutcome(
        event: ParsedEvent,
        outcome: TransactionAssembler.Outcome,
    ): TransactionAssembler.Outcome {
        if (outcome !is TransactionAssembler.Outcome.NeedsReview) {
            return outcome
        }
        val body = rawSmsRepository.getById(event.rawSmsId)?.body.orEmpty()
        return if (InformationalMessagePolicy.shouldAutoIgnore(event, body)) {
            TransactionAssembler.Outcome.Ignored
        } else {
            outcome
        }
    }

    private enum class PersistOutcome { Saved, Already, Failed }
}
