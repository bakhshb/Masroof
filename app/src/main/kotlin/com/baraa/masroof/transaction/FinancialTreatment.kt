package com.baraa.masroof.transaction

/**
 * How a parsed transaction affects the user's finances. Separate from
 * [TransactionType] so that the same parsed type can be classified
 * differently (e.g. a CARD_PAYMENT is a credit-card settlement, not a new
 * expense; a salary deposit is income even though it parses as DEPOSIT).
 */
enum class FinancialTreatment {
    /** A consumer purchase or service that increases spending. */
    EXPENSE,

    /** Money received: salary, refunds to balance, etc. */
    INCOME,

    /** Money moved between two of the user's own accounts. */
    INTERNAL_TRANSFER,

    /** A credit-card bill payment. Not a new expense. */
    CREDIT_CARD_PAYMENT,

    /** Money moved into an investment account. Tracked separately. */
    INVESTMENT,

    /** A reversal of a previous spend. */
    REFUND,

    /** A bank fee / service charge. */
    BANK_FEE,

    /** ATM / cash withdrawal from a bank or salary account. Tracked separately from purchases; does not require a cash-on-hand account. */
    CASH_WITHDRAWAL,

    /** The user must review this transaction before it can be tallied. */
    PENDING_REVIEW,

    /** Declined, advertisement, OTP, or otherwise irrelevant. */
    IGNORED,
    ;

    /** True when a balanced journal needs both a source and a destination *user* account. */
    val requiresTwoAccounts: Boolean
        get() = this == INTERNAL_TRANSFER ||
            this == CREDIT_CARD_PAYMENT ||
            this == INVESTMENT
}
