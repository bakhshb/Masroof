package com.baraa.masroof.domain.model

/**
 * Reconciled classification of a [FinancialTransaction] as listed in DOMAIN §3.13.
 *
 * Distinct from [MessageFamily] (parse-time) and from [BankNetworkType].
 *
 * Note: DOMAIN defines a single enum that currently mixes several concerns —
 * economic treatment ([EXPENSE], [INCOME]), ownership outcome ([SELF_TRANSFER],
 * [EXTERNAL_TRANSFER_IN], [EXTERNAL_TRANSFER_OUT]), and event kinds
 * ([CREDIT_CARD_PAYMENT], [REFUND], [CASH_WITHDRAWAL], [FEE]). P1 keeps the
 * specification's exact members and does not invent a split hierarchy; P2 rules
 * must treat this conflation consciously rather than assuming a pure "treatment"
 * or pure "event kind" model.
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
    BILL_PAYMENT,
    /** Account → loan liability settlement; not ordinary spending (like credit card payment). */
    LOAN_REPAYMENT,
    FEE,
    ADJUSTMENT,
    UNKNOWN,
}
