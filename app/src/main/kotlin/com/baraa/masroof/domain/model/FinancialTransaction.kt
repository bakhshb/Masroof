package com.baraa.masroof.domain.model

import com.baraa.masroof.core.money.Money
import java.time.Instant

/**
 * Reconciled real-world financial result after ownership resolution and
 * related-event matching when required.
 *
 * Multiple [ParsedEvent]s may link to one transaction via [linkedParsedEventIds].
 * Container ids reference [FinancialContainer.id] values (accounts or cards).
 *
 * [categoryId] is an opaque identifier until categorization is modeled in a
 * later phase.
 *
 * DOMAIN.md suggests a `status` / TransactionStatus field but does not enumerate
 * values; that concept is deferred rather than inventing provisional vocabulary.
 */
data class FinancialTransaction(
    val id: String,
    val type: FinancialTransactionType,
    val amount: Money,
    val occurredAt: Instant,
    val sourceContainerId: String?,
    val destinationContainerId: String?,
    val merchant: String?,
    val counterparty: String?,
    val categoryId: String?,
    val linkedParsedEventIds: List<String>,
)
