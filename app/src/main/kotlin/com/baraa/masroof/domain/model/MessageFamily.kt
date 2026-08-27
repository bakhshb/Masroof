package com.baraa.masroof.domain.model

/**
 * High-level SMS message family after classification.
 *
 * Purchase channel (POS / ONLINE) is modeled separately via [PurchaseChannel],
 * not as distinct families.
 */
enum class MessageFamily {
    PURCHASE,
    TRANSFER_IN,
    TRANSFER_OUT,
    CARD_PAYMENT,
    BILL_PAYMENT,
    /** Loan/financing installment debited from a current account (قسط تمويل). */
    FINANCING_INSTALLMENT,
    WITHDRAWAL,
    REFUND,
    FEE,
    BALANCE_NOTICE,
    OTP,
    NON_FINANCIAL,
    UNKNOWN,
}
