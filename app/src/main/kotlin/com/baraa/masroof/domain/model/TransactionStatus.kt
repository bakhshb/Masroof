package com.baraa.masroof.domain.model

/**
 * Lifecycle status of a reconciled [FinancialTransaction].
 *
 * DOMAIN.md references this type but does not enumerate values. These values are
 * provisional for P1 so the model can express pending vs confirmed outcomes;
 * exact product statuses should be confirmed before persistence.
 */
enum class TransactionStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    UNKNOWN,
}
