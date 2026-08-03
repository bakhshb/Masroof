package com.baraa.masroof.ai

import com.baraa.masroof.transaction.Currency

/**
 * Privacy-preserving data shape sent to an AI categorization provider.
 *
 * **What is NEVER included**: the raw SMS body, full account / card numbers,
 * last-4 digits, balance, phone number, sender, beneficiary details, SMS
 * timestamp, full transaction history, user name, location, parser
 * diagnostics.
 *
 * **What IS included**:
 *  - [normalizedMerchant] (best-effort cleaned merchant name)
 *  - [transactionType] (purchase / online / atm)
 *  - [amountBucket] (coarse range — never the exact amount, unless
 *    [includeExactAmount] is true AND the user explicitly enabled it)
 *  - [currency]
 *  - [allowedCategories] (so the model cannot invent categories)
 *  - [channel] (generic channel: POS / ONLINE / ATM, no specifics)
 *  - [language] ("ar" or "en")
 */
data class AiCategorizationRequest(
    val normalizedMerchant: String,
    val transactionType: String,
    val amountBucket: AmountBucket,
    val currency: Currency,
    val allowedCategories: List<AllowedCategory>,
    val channel: Channel,
    val language: String,
    val includeExactAmount: Boolean = false,
    val exactAmountBucketOnly: Double? = null, // populated only when includeExactAmount
)

/** A single allowed category in the AI request — id + Arabic name. */
data class AllowedCategory(
    val id: Long,
    val nameAr: String,
)

/** Coarse amount bucket to protect the user's financial precision. */
enum class AmountBucket(val displayNameAr: String) {
    UNDER_50("أقل من ٥٠"),
    FROM_50_TO_199("٥٠ – ١٩٩"),
    FROM_200_TO_499("٢٠٠ – ٤٩٩"),
    FROM_500_TO_999("٥٠٠ – ٩٩٩"),
    FROM_1000_TO_4999("١٠٠٠ – ٤٩٩٩"),
    FROM_5000_AND_ABOVE("٥٠٠٠ فأكثر");

    companion object {
        /** Bucket the exact amount. [amount] is the user's currency value. */
        fun bucket(amount: Double): AmountBucket = when {
            amount < 50.0 -> UNDER_50
            amount < 200.0 -> FROM_50_TO_199
            amount < 500.0 -> FROM_200_TO_499
            amount < 1000.0 -> FROM_500_TO_999
            amount < 5000.0 -> FROM_1000_TO_4999
            else -> FROM_5000_AND_ABOVE
        }
    }
}

/** Generic channel — never includes merchant or institution name. */
enum class Channel(val displayNameAr: String) {
    POS("نقطة بيع"),
    ONLINE("عبر الإنترنت"),
    ATM("صراف آلي"),
    UNKNOWN("غير محدد"),
}