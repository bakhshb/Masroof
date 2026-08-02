package com.baraa.masroof.transaction

/** Coarse classification of a bank-transaction SMS. */
enum class TransactionType {
    PURCHASE,
    ONLINE_PURCHASE,
    CASH_WITHDRAWAL,
    TRANSFER_OUT,
    TRANSFER_IN,
    CARD_PAYMENT,
    REFUND,
    SALARY,
    DEPOSIT,
    BANK_FEE,
    INTERNAL_TRANSFER,
    INVESTMENT_TRANSFER,
    DECLINED,
    UNKNOWN,
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
