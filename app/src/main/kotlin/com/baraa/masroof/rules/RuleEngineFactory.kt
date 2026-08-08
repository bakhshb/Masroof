package com.baraa.masroof.rules

import com.baraa.masroof.data.db.Category
import com.baraa.masroof.rules.rules.ArabicMerchantCategoryRule
import com.baraa.masroof.rules.rules.BankFeeRule
import com.baraa.masroof.rules.rules.CardPaymentRule
import com.baraa.masroof.rules.rules.CreditLimitChangeRule
import com.baraa.masroof.rules.rules.DeclinedRule
import com.baraa.masroof.rules.rules.HighConfidenceMerchantRule
import com.baraa.masroof.rules.rules.InternalTransferRule
import com.baraa.masroof.rules.rules.MerchantMemoryRule
import com.baraa.masroof.rules.rules.ParsedTypeFallbackRule
import com.baraa.masroof.rules.rules.PendingStatusRule
import com.baraa.masroof.rules.rules.RefundRule
import com.baraa.masroof.rules.rules.SalaryRule
import com.baraa.masroof.rules.rules.WalletTopUpRule
import com.baraa.masroof.transaction.TransactionType

/**
 * Builds a fully-configured [RuleEngine]. The single source of truth for
 * rule order is the [RulePriority] enum; this factory walks the enum and
 * instantiates the matching rule for each non-FALLBACK priority. New
 * priorities are added by introducing a new enum constant + a new rule
 * class; no separate documentation list is maintained.
 */
object RuleEngineFactory {

    /**
     * The set of priorities for which the engine registers a rule. Adding
     * a rule for a new priority is a one-line change here.
     */
    val REGISTERED_PRIORITIES: List<RulePriority> = listOf(
        RulePriority.SAFETY,             // DeclinedRule + CreditLimitChangeRule + PendingStatusRule
        RulePriority.SAFETY_CRITICAL,    // CardPaymentRule + RefundRule + BankFeeRule + SalaryRule
        RulePriority.INTERNAL_TRANSFER,  // InternalTransferRule + WalletTopUpRule
        RulePriority.MERCHANT_MEMORY,    // MerchantMemoryRule
        RulePriority.MERCHANT_RULE,      // HighConfidenceMerchantRule
        RulePriority.CATEGORY_RULE,      // ArabicMerchantCategoryRule
        RulePriority.FALLBACK,           // ParsedTypeFallbackRule
    )

    fun build(
        categories: List<Category>,
        feeCategoryId: Long?,
    ): RuleEngine {
        val categoryByName = categories.associateBy { it.nameAr }
        fun catByName(name: String): Category? = categoryByName[name]

        val rules: List<TransactionRule> = listOf(
            DeclinedRule(),
            CreditLimitChangeRule(),
            PendingStatusRule(),
            CardPaymentRule(),
            RefundRule(),
            BankFeeRule(feeCategoryIdResolver = { feeCategoryId }),
            SalaryRule(),
            // InternalTransferRule + WalletTopUpRule
            // all run at INTERNAL_TRANSFER priority. They all return null
            // when only one side is known (or none) so the engine falls
            // through to PENDING_REVIEW.
            InternalTransferRule(),
            WalletTopUpRule(),
            // MerchantMemoryRule runs BEFORE the generic category rules so
            // a user-confirmed mapping always wins over a generic pattern.
            MerchantMemoryRule(),
            HighConfidenceMerchantRule(
                tokenToCategory = HIGH_CONFIDENCE_MERCHANT_TOKENS,
                categoryByName = ::catByName,
            ),
            ArabicMerchantCategoryRule(categoryByName = ::catByName),
            ParsedTypeFallbackRule(),
        )
        return RuleEngine(rules)
    }

    // -- High-confidence merchant tokens (English, conservative) ---------

    private val HIGH_CONFIDENCE_MERCHANT_TOKENS: Map<String, String> = mapOf(
        "starbucks" to "مقاهي",
        "caribou" to "مقاهي",
        "dunkin" to "مقاهي",
        "kfc" to "مطاعم",
        "mcdonalds" to "مطاعم",
        "burger" to "مطاعم",
        "pizza" to "مطاعم",
        "jahez" to "توصيل طعام",
        "hungerstation" to "توصيل طعام",
        "talabat" to "توصيل طعام",
        "almarai" to "مقاضي",
        "panda" to "مقاضي",
        "othaim" to "مقاضي",
        "carrefour" to "مقاضي",
        "lulu" to "مقاضي",
    )

    /** Exposed so the test suite can reference the same constants. */
    val highConfidenceMerchantTokens: Map<String, String> get() = HIGH_CONFIDENCE_MERCHANT_TOKENS

    /** Exposed so the test suite can verify the priority order. */
    val documentedPriorities: List<RulePriority> get() = REGISTERED_PRIORITIES

    /** Exposed for the diagnostic screen / debug export. */
    fun describeActiveRules(): List<String> = REGISTERED_PRIORITIES
        .flatMap { p ->
            when (p) {
                RulePriority.SAFETY -> listOf("DeclinedRule", "CreditLimitChangeRule", "PendingStatusRule")
                RulePriority.SAFETY_CRITICAL -> listOf("CardPaymentRule", "RefundRule", "BankFeeRule", "SalaryRule")
                RulePriority.INTERNAL_TRANSFER -> listOf("InternalTransferRule", "WalletTopUpRule")
                RulePriority.MERCHANT_MEMORY -> listOf("MerchantMemoryRule")
                RulePriority.MERCHANT_RULE -> listOf("HighConfidenceMerchantRule")
                RulePriority.CATEGORY_RULE -> listOf("ArabicMerchantCategoryRule")
                RulePriority.FALLBACK -> listOf("ParsedTypeFallbackRule")
            }.map { "${it}@${p.name}#${p.order}" }
        }

    /** Sanity helper: does a parsed type match a known safety pattern? */
    fun isTransferLikeType(t: TransactionType): Boolean = t in setOf(
        TransactionType.TRANSFER_IN,
        TransactionType.TRANSFER_OUT,
        TransactionType.INTERNAL_TRANSFER,
    )
}
