package com.baraa.masroof.data.db

/** Where the transaction date in [TransactionEntity.transactionDate] came from. */
enum class DateSource {
    /** Date was found and parsed from the SMS body. */
    FROM_BODY,

    /** No date in the body; fell back to the SMS received timestamp. */
    FROM_SMS_METADATA,

    /** User explicitly corrected the effective transaction date. */
    USER_CORRECTION,

    /** No date available in the body or SMS metadata. */
    UNKNOWN,
}
