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
}

/** Currency of the transaction amount. */
enum class Currency {
    SAR,
    USD,
    EUR,
    UNKNOWN,
}
