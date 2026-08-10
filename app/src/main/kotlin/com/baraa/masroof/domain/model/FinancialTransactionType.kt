package com.baraa.masroof.domain.model

/**
 * Reconciled financial meaning of a [FinancialTransaction].
 *
 * Distinct from [MessageFamily] (parse-time) and from [BankNetworkType].
 */
enum class FinancialTransactionType {
    EXPENSE,
    INCOME,
    SELF_TRANSFER,
    EXTERNAL_TRANSFER_IN,
    EXTERNAL_TRANSFER_OUT,
    CREDIT_CARD_PAYMENT,
    REFUND,
    CASH_WITHDRAWAL,
    FEE,
    ADJUSTMENT,
    UNKNOWN,
}
