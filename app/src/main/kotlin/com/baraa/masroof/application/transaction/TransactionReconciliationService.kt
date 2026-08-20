package com.baraa.masroof.application.transaction

import com.baraa.masroof.application.review.EffectiveParsedEventProvider
import com.baraa.masroof.domain.assembly.TransactionAssembler
import com.baraa.masroof.domain.matching.TransactionMatcher
import com.baraa.masroof.domain.matching.TransferMatchCandidate
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.ReviewKind
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
                    event = candidate.event,
                    receivedAt = candidate.receivedAt,
                    sourceOwnership = candidate.sourceOwnership,
                    destinationOwnership = candidate.destinationOwnership,
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

    private suspend fun persist(
        transaction: com.baraa.masroof.domain.model.FinancialTransaction,
        rawSmsIds: List<String>,
    ): PersistOutcome =
        when (financialTransactionRepository.save(transaction, rawSmsIds)) {
            FinancialTransactionSaveResult.Saved -> PersistOutcome.Saved
            FinancialTransactionSaveResult.AlreadyExists -> PersistOutcome.Already
            is FinancialTransactionSaveResult.Conflict -> PersistOutcome.Failed
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
