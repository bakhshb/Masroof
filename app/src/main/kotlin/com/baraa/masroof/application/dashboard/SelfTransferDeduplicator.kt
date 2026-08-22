package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.parsing.repository.ParsedEventRecord

/**
 * Removes duplicate representations of the same internal transfer.
 *
 * Banks often emit two SMS legs (OUT + IN), each parsed with both endpoints, which
 * can produce two [FinancialTransactionType.SELF_TRANSFER] rows for one movement.
 * Orphan single-leg [EXTERNAL_TRANSFER_IN]/[EXTERNAL_TRANSFER_OUT] rows are dropped
 * when a self-transfer already covers the same amount and owned endpoint.
 */
object SelfTransferDeduplicator {
    data class TransferEndpoints(
        val sourceId: String,
        val destId: String,
        val amount: Money,
    )

    fun filter(
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
    ): List<FinancialTransaction> {
        val parsedById = parsedRecords.associateBy { it.event.id }
        fun rawSmsIds(tx: FinancialTransaction): Set<String> =
            tx.linkedParsedEventIds.mapNotNull { parsedById[it]?.event?.rawSmsId }.toSet()

        fun endpointsKey(tx: FinancialTransaction): TransferEndpoints? {
            val source = tx.sourceContainerId ?: return null
            val dest = tx.destinationContainerId ?: return null
            return TransferEndpoints(source, dest, tx.amount)
        }

        val suppressed = mutableSetOf<String>()

        transactions
            .filter { it.type == FinancialTransactionType.SELF_TRANSFER }
            .groupBy { endpointsKey(it) }
            .filterKeys { it != null }
            .values
            .filter { it.size > 1 }
            .forEach { group ->
                val canonical = group.maxWith(
                    compareBy<FinancialTransaction> { it.linkedParsedEventIds.size }
                        .thenBy { rawSmsIds(it).size }
                        .thenBy { it.id },
                )
                group.filter { it.id != canonical.id }.forEach { suppressed.add(it.id) }
            }

        val canonicalSelfTransfers = transactions.filter {
            it.type == FinancialTransactionType.SELF_TRANSFER && it.id !in suppressed
        }
        val selfRawSmsIds = canonicalSelfTransfers.flatMap { rawSmsIds(it) }.toSet()
        val selfEndpointKeys = canonicalSelfTransfers.mapNotNull { endpointsKey(it) }.toSet()

        return transactions.filter { tx ->
            when {
                tx.id in suppressed -> false

                tx.type == FinancialTransactionType.EXTERNAL_TRANSFER_IN -> {
                    val overlapsSelfSms = rawSmsIds(tx).any { it in selfRawSmsIds }
                    val dest = tx.destinationContainerId
                    val overlapsSelfAmount = dest != null &&
                        selfEndpointKeys.any { it.destId == dest && it.amount == tx.amount }
                    !(overlapsSelfSms || overlapsSelfAmount)
                }

                tx.type == FinancialTransactionType.EXTERNAL_TRANSFER_OUT -> {
                    val overlapsSelfSms = rawSmsIds(tx).any { it in selfRawSmsIds }
                    val source = tx.sourceContainerId
                    val overlapsSelfAmount = source != null &&
                        selfEndpointKeys.any { it.sourceId == source && it.amount == tx.amount }
                    !(overlapsSelfSms || overlapsSelfAmount)
                }

                else -> true
            }
        }
    }
}
