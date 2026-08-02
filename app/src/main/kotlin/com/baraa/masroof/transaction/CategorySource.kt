package com.baraa.masroof.transaction

/** How a transaction got its category. */
enum class CategorySource {
    /** A financial-treatment rule fired (card payment, refund, etc.). */
    RULE,

    /** Matched a previous user-confirmed entry in merchant memory. */
    MERCHANT_MEMORY,

    /** The user picked the category directly. */
    USER,

    /** Reserved for a future AI-based classifier. Not used today. */
    FUTURE_AI,

    /** No category assigned yet. */
    UNCLASSIFIED,
}
