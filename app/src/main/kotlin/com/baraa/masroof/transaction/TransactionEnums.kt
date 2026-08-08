package com.baraa.masroof.transaction

/**
 * Coarse classification of a bank-transaction SMS.
 *
 * This is the only persisted transaction taxonomy. Legacy names are rewritten
 * by Room migration 26→27 before entities are decoded.
 */
enum class TransactionType {
    PURCHASE,
    ONLINE_PURCHASE,
    CASH_WITHDRAWAL,
    TRANSFER_OUT,
    TRANSFER_IN,
    CARD_PAYMENT,
    REFUND,
    SALARY,
    FEE,
    INTERNAL_TRANSFER,
    /** Utility / SADAD / bill payment debit. */
    BILL_PAYMENT,
    /** Other financial event that is not one of the specific types. */
    OTHER_FINANCIAL,
    /** Informational / OTP / settings / ads — not a financial transaction. */
    NON_FINANCIAL,
}

/** Lifecycle status of a transaction. */
enum class TransactionStatus {
    COMPLETED,
    PENDING,
    DECLINED,
    REVERSED,
    UNKNOWN,
    /**
     * The parser did not produce a high-confidence result. The transaction
     * is persisted but the UI should surface it for user review / edit.
     */
    NEEDS_REVIEW,
}

/** Currency of the transaction amount. */
enum class Currency {
    SAR,
    USD,
    EUR,
    UNKNOWN,
}
