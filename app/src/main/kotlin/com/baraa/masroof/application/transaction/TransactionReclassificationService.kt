package com.baraa.masroof.application.transaction

import com.baraa.masroof.application.logging.AppLogCategories
import com.baraa.masroof.application.logging.AppLogFormatting
import com.baraa.masroof.application.logging.AppLogService
import com.baraa.masroof.application.review.EffectiveParsedEventProvider
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.ownership.OwnershipResolver
import com.baraa.masroof.domain.repository.FinancialTransactionRepository

sealed interface ReclassificationResult {
    data class Success(val transaction: FinancialTransaction) : ReclassificationResult

    data class Rejected(val reason: String) : ReclassificationResult
}

/**
 * Updates an already-persisted single-SMS [FinancialTransaction] type and container wiring.
 */
class TransactionReclassificationService(
    private val financialTransactionRepository: FinancialTransactionRepository,
    private val effectiveParsedEventProvider: EffectiveParsedEventProvider,
    private val ownershipResolver: OwnershipResolver,
    private val appLogService: AppLogService? = null,
) {
    suspend fun reclassify(
        transactionId: String,
        newType: FinancialTransactionType,
    ): ReclassificationResult {
        if (newType !in ALLOWED_TYPES) {
            return ReclassificationResult.Rejected("type_not_allowed")
        }
        val existing = financialTransactionRepository.getById(transactionId)
            ?: return ReclassificationResult.Rejected("transaction_not_found")
        if (existing.type == newType) {
            return ReclassificationResult.Success(existing)
        }

        val rawSmsIds = financialTransactionRepository.listRawSmsIds(transactionId)
        if (rawSmsIds.size != 1) {
            return ReclassificationResult.Rejected("paired_transaction_not_supported")
        }

        val record = effectiveParsedEventProvider.findEffectiveByRawSmsId(rawSmsIds.single())
            ?: return ReclassificationResult.Rejected("parsed_event_missing")
        val event = record.event

        val sourceAccountId = event.sourceAccountRef?.let(FinancialContainerIdFactory::accountId)
        val destAccountId = event.destinationAccountRef?.let(FinancialContainerIdFactory::accountId)
        val cardId = event.cardRef?.let(FinancialContainerIdFactory::cardId)

        val (sourceId, destId) = when (newType) {
            FinancialTransactionType.EXTERNAL_TRANSFER_IN -> {
                val destRef = event.destinationAccountRef
                    ?: return ReclassificationResult.Rejected("missing_destination")
                if (ownershipResolver.resolveAccount(destRef) != OwnershipStatus.OWNED) {
                    return ReclassificationResult.Rejected("destination_not_owned")
                }
                if (FinancialContainerIdFactory.accountId(destRef) == null) {
                    return ReclassificationResult.Rejected("destination_not_durable")
                }
                null to destAccountId
            }

            FinancialTransactionType.EXTERNAL_TRANSFER_OUT -> {
                val sourceRef = event.sourceAccountRef
                    ?: return ReclassificationResult.Rejected("missing_source")
                if (ownershipResolver.resolveAccount(sourceRef) != OwnershipStatus.OWNED) {
                    return ReclassificationResult.Rejected("source_not_owned")
                }
                if (FinancialContainerIdFactory.accountId(sourceRef) == null) {
                    return ReclassificationResult.Rejected("source_not_durable")
                }
                sourceAccountId to null
            }

            FinancialTransactionType.EXPENSE ->
                (cardId ?: sourceAccountId) to null

            FinancialTransactionType.INCOME ->
                null to destAccountId

            FinancialTransactionType.CREDIT_CARD_PAYMENT ->
                sourceAccountId to cardId

            FinancialTransactionType.REFUND ->
                null to (destAccountId ?: cardId)

            FinancialTransactionType.CASH_WITHDRAWAL,
            FinancialTransactionType.BILL_PAYMENT,
            FinancialTransactionType.FEE,
            ->
                sourceAccountId to null

            else ->
                return ReclassificationResult.Rejected("type_not_allowed")
        }

        val updated = existing.copy(
            type = newType,
            sourceContainerId = sourceId,
            destinationContainerId = destId,
        )
        if (!financialTransactionRepository.update(updated)) {
            return ReclassificationResult.Rejected("update_failed")
        }
        appLogService?.info(
            AppLogCategories.TRANSACTION,
            "Reclassified ${AppLogFormatting.maskId(transactionId)} to ${newType.name.lowercase()}",
        )
        return ReclassificationResult.Success(updated)
    }

    companion object {
        val ALLOWED_TYPES: Set<FinancialTransactionType> = setOf(
            FinancialTransactionType.EXPENSE,
            FinancialTransactionType.INCOME,
            FinancialTransactionType.CREDIT_CARD_PAYMENT,
            FinancialTransactionType.REFUND,
            FinancialTransactionType.CASH_WITHDRAWAL,
            FinancialTransactionType.BILL_PAYMENT,
            FinancialTransactionType.FEE,
            FinancialTransactionType.EXTERNAL_TRANSFER_IN,
            FinancialTransactionType.EXTERNAL_TRANSFER_OUT,
        )
    }
}
