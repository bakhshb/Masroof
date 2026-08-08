package com.baraa.masroof.transaction

/**
 * Money-flow polarity derived from [TransactionType].
 * Separate from ledger [FinancialTreatment] and from merchant categories.
 */
enum class MoneyFlowDirection {
    /** Money enters the user's account (salary, refund, transfer in). */
    INFLOW,

    /** Money leaves the user's account (purchase, fee, transfer out). */
    OUTFLOW,

    /** Movement between the user's own accounts (not income/expense). */
    TRANSFER,

    /** Informational / non-financial — no money movement. */
    NONE,
}
