package com.baraa.masroof.domain.assembly

import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.ids.TransactionIdFactory
import com.baraa.masroof.domain.matching.TransferMatchPair
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.rules.ClassificationEvidence
import com.baraa.masroof.domain.rules.ClassificationResult
import com.baraa.masroof.domain.rules.ContainerKind
import com.baraa.masroof.domain.rules.ResolvedContainerFacts
import com.baraa.masroof.domain.rules.TransactionClassifier
import java.time.Instant

/**
 * Pure assembly of [FinancialTransaction] from validated evidence + ownership.
 * No Room / Android.
 *
 * Passes only facts P8 genuinely knows into P2 classification — never fabricates
 * temporary account/card domain objects or invented account/card types solely to
 * satisfy the classifier. Container ids come from [FinancialContainerIdFactory]
 * on durable known-bank refs.
 */
object TransactionAssembler {
    sealed interface Outcome {
        data class Assembled(
            val transaction: FinancialTransaction,
            val rawSmsIds: List<String>,
        ) : Outcome

        data object PendingMatch : Outcome

        data class NeedsReview(
            val reasons: List<String>,
        ) : Outcome

        data object Ignored : Outcome
    }

    /**
     * Assemble a single ParsedEvent when possible.
     *
     * [sourceOwnership]/[destinationOwnership]/[cardOwnership] come from P7.
     * [receivedAt] is the RawSms receipt fallback clock.
     */
    fun assembleSingle(
        event: ParsedEvent,
        receivedAt: Instant,
        sourceOwnership: OwnershipStatus,
        destinationOwnership: OwnershipStatus,
        cardOwnership: OwnershipStatus,
    ): Outcome {
        when (event.messageFamily) {
            MessageFamily.OTP,
            MessageFamily.BALANCE_NOTICE,
            MessageFamily.NON_FINANCIAL,
            -> return Outcome.Ignored

            MessageFamily.UNKNOWN ->
                return Outcome.NeedsReview(listOf("unknown_message_family"))

            else -> Unit
        }

        val amount = event.amount
            ?: return Outcome.NeedsReview(listOf("missing_amount"))

        // Transfers with an UNKNOWN-bank side cannot be finalized without a match.
        if (event.messageFamily == MessageFamily.TRANSFER_OUT ||
            event.messageFamily == MessageFamily.TRANSFER_IN
        ) {
            if (hasUnknownBankSide(event)) {
                return Outcome.PendingMatch
            }
        }

        val sourceFacts = accountFacts(event.sourceAccountRef, sourceOwnership)
        val destinationFacts = when (event.messageFamily) {
            MessageFamily.CARD_PAYMENT ->
                cardFacts(event.cardRef, cardOwnership)
                    ?: accountFacts(event.destinationAccountRef, destinationOwnership)

            else -> accountFacts(event.destinationAccountRef, destinationOwnership)
        }
        val instrumentFacts = when (event.messageFamily) {
            MessageFamily.PURCHASE ->
                cardFacts(event.cardRef, cardOwnership)
                    ?: accountFacts(event.sourceAccountRef, sourceOwnership)

            else -> null
        }

        val classification = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = event.messageFamily,
                source = sourceFacts,
                destination = destinationFacts,
                instrument = instrumentFacts,
                purchaseChannel = event.purchaseChannel,
                bankNetworkType = event.bankNetworkType,
            ),
        )

        return when (classification) {
            is ClassificationResult.Classified -> {
                val tx = buildTransaction(
                    type = classification.transactionType,
                    amount = amount,
                    occurredAt = event.occurredAt ?: receivedAt,
                    event = event,
                    linkedEventIds = listOf(event.id),
                    rawSmsIds = listOf(event.rawSmsId),
                )
                Outcome.Assembled(tx.transaction, tx.rawSmsIds)
            }

            is ClassificationResult.NeedsReview -> {
                // Conservative purchase: family alone is enough for EXPENSE when amount exists.
                if (event.messageFamily == MessageFamily.PURCHASE &&
                    classification.tentativeType == FinancialTransactionType.EXPENSE
                ) {
                    val tx = buildTransaction(
                        type = FinancialTransactionType.EXPENSE,
                        amount = amount,
                        occurredAt = event.occurredAt ?: receivedAt,
                        event = event,
                        linkedEventIds = listOf(event.id),
                        rawSmsIds = listOf(event.rawSmsId),
                    )
                    return Outcome.Assembled(tx.transaction, tx.rawSmsIds)
                }
                if (event.messageFamily == MessageFamily.TRANSFER_IN ||
                    event.messageFamily == MessageFamily.TRANSFER_OUT
                ) {
                    Outcome.PendingMatch
                } else {
                    Outcome.NeedsReview(classification.reasons)
                }
            }
        }
    }

    /**
     * Assemble a mutually unique OUT↔IN pair into one SELF_TRANSFER when both
     * local known-bank endpoints resolve OWNED.
     */
    fun assembleMatchedPair(
        pair: TransferMatchPair,
        outgoingSourceOwnership: OwnershipStatus,
        incomingDestinationOwnership: OwnershipStatus,
    ): Outcome {
        if (outgoingSourceOwnership != OwnershipStatus.OWNED) {
            return Outcome.PendingMatch
        }
        if (incomingDestinationOwnership != OwnershipStatus.OWNED) {
            return Outcome.PendingMatch
        }

        val out = pair.outgoing.event
        val inn = pair.incoming.event
        val amount = out.amount ?: return Outcome.NeedsReview(listOf("missing_amount"))
        if (inn.amount == null || amount != inn.amount) {
            return Outcome.NeedsReview(listOf("matched_pair_amount_mismatch"))
        }

        val sourceRef = out.sourceAccountRef
            ?: return Outcome.NeedsReview(listOf("matched_pair_missing_source"))
        val destRef = inn.destinationAccountRef
            ?: return Outcome.NeedsReview(listOf("matched_pair_missing_destination"))
        if (sourceRef.bank == Bank.UNKNOWN || destRef.bank == Bank.UNKNOWN) {
            return Outcome.NeedsReview(listOf("matched_pair_requires_known_bank_endpoints"))
        }

        val sourceId = FinancialContainerIdFactory.accountId(sourceRef)
            ?: return Outcome.NeedsReview(listOf("matched_pair_source_not_durable"))
        val destId = FinancialContainerIdFactory.accountId(destRef)
            ?: return Outcome.NeedsReview(listOf("matched_pair_destination_not_durable"))

        val classification = TransactionClassifier.classify(
            ClassificationEvidence(
                messageFamily = MessageFamily.TRANSFER_OUT,
                source = ResolvedContainerFacts(
                    kind = ContainerKind.ACCOUNT,
                    ownership = OwnershipStatus.OWNED,
                ),
                destination = ResolvedContainerFacts(
                    kind = ContainerKind.ACCOUNT,
                    ownership = OwnershipStatus.OWNED,
                ),
                bankNetworkType = out.bankNetworkType,
            ),
        )

        if (classification !is ClassificationResult.Classified ||
            classification.transactionType != FinancialTransactionType.SELF_TRANSFER
        ) {
            return Outcome.NeedsReview(
                listOf("matched_pair_not_self_transfer") + classification.reasons,
            )
        }

        val occurredAt = listOfNotNull(out.occurredAt, inn.occurredAt).minOrNull()
            ?: minOf(pair.outgoing.receivedAt, pair.incoming.receivedAt)

        val rawSmsIds = listOf(out.rawSmsId, inn.rawSmsId).sorted()
        val linked = listOf(out.id, inn.id).sorted()
        val tx = FinancialTransaction(
            id = TransactionIdFactory.fromRawSmsIds(rawSmsIds),
            type = FinancialTransactionType.SELF_TRANSFER,
            amount = amount,
            occurredAt = occurredAt,
            sourceContainerId = sourceId,
            destinationContainerId = destId,
            merchant = null,
            counterparty = out.counterparty ?: inn.counterparty,
            categoryId = null,
            linkedParsedEventIds = linked,
        )
        return Outcome.Assembled(tx, rawSmsIds)
    }

    private fun hasUnknownBankSide(event: ParsedEvent): Boolean =
        event.sourceAccountRef?.bank == Bank.UNKNOWN ||
            event.destinationAccountRef?.bank == Bank.UNKNOWN

    private fun accountFacts(
        ref: AccountReference?,
        ownership: OwnershipStatus,
    ): ResolvedContainerFacts? {
        if (ref == null) return null
        val masked = ref.maskedNumber?.trim().orEmpty()
        if (masked.isEmpty()) return null
        return ResolvedContainerFacts(
            kind = ContainerKind.ACCOUNT,
            ownership = ownership,
            knownCardType = null,
        )
    }

    private fun cardFacts(
        ref: CardReference?,
        ownership: OwnershipStatus,
    ): ResolvedContainerFacts? {
        if (ref == null) return null
        val last4 = ref.last4?.trim().orEmpty()
        if (last4.isEmpty()) return null
        // P7 registry does not know CardType — leave knownCardType null.
        return ResolvedContainerFacts(
            kind = ContainerKind.CARD,
            ownership = ownership,
            knownCardType = null,
        )
    }

    private data class Built(
        val transaction: FinancialTransaction,
        val rawSmsIds: List<String>,
    )

    private fun buildTransaction(
        type: FinancialTransactionType,
        amount: com.baraa.masroof.core.money.Money,
        occurredAt: Instant,
        event: ParsedEvent,
        linkedEventIds: List<String>,
        rawSmsIds: List<String>,
    ): Built {
        // Resolve durable ids from references only (null for Bank.UNKNOWN / incomplete).
        val durableSourceAccountId = event.sourceAccountRef?.let(FinancialContainerIdFactory::accountId)
        val durableDestAccountId = event.destinationAccountRef?.let(FinancialContainerIdFactory::accountId)
        val durableCardId = event.cardRef?.let(FinancialContainerIdFactory::cardId)

        val (sourceId, destId) = when (event.messageFamily) {
            MessageFamily.PURCHASE ->
                (durableCardId ?: durableSourceAccountId) to null

            MessageFamily.CARD_PAYMENT ->
                durableSourceAccountId to durableCardId

            MessageFamily.REFUND ->
                // Incoming value to the user's container — never put the receiver in source.
                null to (durableDestAccountId ?: durableCardId)

            MessageFamily.WITHDRAWAL,
            MessageFamily.BILL_PAYMENT,
            MessageFamily.FEE,
            MessageFamily.TRANSFER_OUT,
            MessageFamily.TRANSFER_IN,
            ->
                durableSourceAccountId to durableDestAccountId

            else ->
                durableSourceAccountId to durableDestAccountId
        }

        return Built(
            transaction = FinancialTransaction(
                id = TransactionIdFactory.fromRawSmsIds(rawSmsIds),
                type = type,
                amount = amount,
                occurredAt = occurredAt,
                sourceContainerId = sourceId,
                destinationContainerId = destId,
                merchant = event.merchant,
                counterparty = event.counterparty,
                categoryId = null,
                linkedParsedEventIds = linkedEventIds.sorted(),
            ),
            rawSmsIds = rawSmsIds.sorted(),
        )
    }
}
