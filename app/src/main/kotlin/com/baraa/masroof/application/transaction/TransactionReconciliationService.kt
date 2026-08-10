package com.baraa.masroof.application.transaction

import com.baraa.masroof.domain.assembly.TransactionAssembler
import com.baraa.masroof.domain.matching.TransactionMatcher
import com.baraa.masroof.domain.matching.TransferMatchCandidate
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.ownership.OwnershipResolver
import com.baraa.masroof.domain.repository.FinancialTransactionRepository
import com.baraa.masroof.domain.repository.FinancialTransactionSaveResult
import com.baraa.masroof.domain.repository.RawSmsRepository
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import com.baraa.masroof.parsing.repository.ParsedEventRepository
import kotlinx.coroutines.CancellationException

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
 */
class TransactionReconciliationService(
    private val parsedEventRepository: ParsedEventRepository,
    private val rawSmsRepository: RawSmsRepository,
    private val financialTransactionRepository: FinancialTransactionRepository,
    private val ownershipResolver: OwnershipResolver,
) {
    suspend fun reconcileStoredEvents(): ReconciliationSummary {
        val records = parsedEventRepository.listAll()
        return reconcileRecords(records)
    }

    /**
     * Reconcile after a newly saved ParsedEvent. Failures are swallowed by callers
     * that treat P8 as derived processing.
     */
    suspend fun reconcileAfterParsedEvent(event: ParsedEvent): ReconciliationSummary {
        val records = parsedEventRepository.listAll()
        return reconcileRecords(records)
    }

    private suspend fun reconcileRecords(
        records: List<ParsedEventRecord>,
    ): ReconciliationSummary {
        var assembledSingle = 0
        var matchedPairs = 0
        var pendingMatch = 0
        var needsReview = 0
        var ignored = 0
        var alreadyLinked = 0
        var failed = 0

        val unresolvedTransfers = mutableListOf<TransferMatchCandidate>()

        for (record in records) {
            val event = record.event
            if (financialTransactionRepository.isRawSmsLinked(event.rawSmsId)) {
                alreadyLinked++
                continue
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
                    val single = TransactionAssembler.assembleSingle(
                        event = event,
                        receivedAt = receivedAt,
                        sourceOwnership = sourceOwn,
                        destinationOwnership = destOwn,
                        cardOwnership = cardOwn,
                    )
                    when (single) {
                        is TransactionAssembler.Outcome.Assembled -> {
                            when (
                                persist(single.transaction, single.rawSmsIds)
                            ) {
                                PersistOutcome.Saved -> assembledSingle++
                                PersistOutcome.Already -> alreadyLinked++
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

                        is TransactionAssembler.Outcome.NeedsReview -> needsReview++
                        TransactionAssembler.Outcome.Ignored -> ignored++
                    }
                }

                else -> {
                    when (
                        val outcome = TransactionAssembler.assembleSingle(
                            event = event,
                            receivedAt = receivedAt,
                            sourceOwnership = sourceOwn,
                            destinationOwnership = destOwn,
                            cardOwnership = cardOwn,
                        )
                    ) {
                        is TransactionAssembler.Outcome.Assembled -> {
                            when (persist(outcome.transaction, outcome.rawSmsIds)) {
                                PersistOutcome.Saved -> assembledSingle++
                                PersistOutcome.Already -> alreadyLinked++
                                PersistOutcome.Failed -> failed++
                            }
                        }

                        TransactionAssembler.Outcome.PendingMatch -> pendingMatch++
                        is TransactionAssembler.Outcome.NeedsReview -> needsReview++
                        TransactionAssembler.Outcome.Ignored -> ignored++
                    }
                }
            }
        }

        // Pair matching among unresolved transfers (exclude those that got assembled).
        val stillOpen = unresolvedTransfers.filterNot {
            // Re-check: some may have been linked if we ever assemble within loop (not yet).
            false
        }.filter {
            !financialTransactionRepository.isRawSmsLinked(it.event.rawSmsId)
        }

        // Adjust pendingMatch: candidates that successfully match shouldn't stay pending.
        val pairs = TransactionMatcher.findMutuallyUniquePairs(stillOpen)
        val matchedEventIds = mutableSetOf<String>()
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
                            // Those two were counted as pending earlier.
                            pendingMatch = (pendingMatch - 2).coerceAtLeast(0)
                        }

                        PersistOutcome.Already -> alreadyLinked += 2
                        PersistOutcome.Failed -> failed++
                    }
                }

                else -> Unit
            }
        }

        return ReconciliationSummary(
            assembledSingle = assembledSingle,
            matchedPairs = matchedPairs,
            pendingMatch = pendingMatch,
            needsReview = needsReview,
            ignored = ignored,
            alreadyLinked = alreadyLinked,
            failed = failed,
        )
    }

    private suspend fun persist(
        transaction: com.baraa.masroof.domain.model.FinancialTransaction,
        rawSmsIds: List<String>,
    ): PersistOutcome =
        try {
            when (financialTransactionRepository.save(transaction, rawSmsIds)) {
                FinancialTransactionSaveResult.Saved -> PersistOutcome.Saved
                FinancialTransactionSaveResult.AlreadyExists -> PersistOutcome.Already
                is FinancialTransactionSaveResult.Conflict -> PersistOutcome.Failed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            PersistOutcome.Failed
        }

    private enum class PersistOutcome { Saved, Already, Failed }
}
