package com.baraa.masroof.rules

import com.baraa.masroof.data.db.Category
import com.baraa.masroof.rules.rules.BankFeeRule
import com.baraa.masroof.rules.rules.CardPaymentRule
import com.baraa.masroof.rules.rules.DeclinedRule
import com.baraa.masroof.rules.rules.GenericCategoryRule
import com.baraa.masroof.rules.rules.HighConfidenceMerchantRule
import com.baraa.masroof.rules.rules.InternalTransferRule
import com.baraa.masroof.rules.rules.InvestmentTransferRule
import com.baraa.masroof.rules.rules.MerchantMemoryRule
import com.baraa.masroof.rules.rules.PendingStatusRule
import com.baraa.masroof.rules.rules.RefundRule
import com.baraa.masroof.rules.rules.SalaryRule
import com.baraa.masroof.transaction.TransactionType

/**
 * Builds a fully-configured [RuleEngine] from the available stores.
 *
 * Rules are added in [RulePriority] order. The high-confidence merchant
 * rule and the generic category rule are seeded with curated example
 * patterns; the user can extend them in a future settings screen.
 */
object RuleEngineFactory {

    /**
     * High-confidence merchant tokens. Conservative — only very common
     * merchants that the parser reliably identifies. Add new ones via the
     * review UI in a future iteration.
     */
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
        "stc" to "جوال",
        "mobily" to "جوال",
        "zain" to "جوال",
        "almarai" to "مقاضي",
        "panda" to "مقاضي",
        "othaim" to "مقاضي",
        "carrefour" to "مقاضي",
        "lulu" to "مقاضي",
    )

    /**
     * Generic category patterns. The user's review confirmation can
     * promote any of these to merchant memory.
     */
    private val GENERIC_CATEGORY_PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("""\b(supermarket|grocery|hypermarket|بقالة|سوبرماركت|هايبر|تموينات)\b""") to "مقاضي",
        Regex("""\b(fuel|gas|petrol|بنزين|وقود|محطة)\b""") to "وقود",
        Regex("""\b(pharmacy|drug|صيدلية)\b""") to "صيدلية",
        Regex("""\b(telecom|mobile|recharge|باقة|فاتورة|اتصالات|جوال)\b""") to "جوال",
        Regex("""\b(internet|broadband|إنترنت|نت)\b""") to "إنترنت",
        Regex("""\b(cafe|coffee|كوفي|قهوة|مقھى|كافيه)\b""") to "مقاهي",
        Regex("""\b(restaurant|food|dining|مطعم|مطاعم|أكل)\b""") to "مطاعم",
        Regex("""\b(delivery|توصيل)\b""") to "توصيل طعام",
    )

    fun build(
        categories: List<Category>,
        feeCategoryId: Long?,
    ): RuleEngine {
        val categoryByName = categories.associateBy { it.nameAr }
        fun catByName(name: String): Category? = categoryByName[name]

        val tokenToCategory = HIGH_CONFIDENCE_MERCHANT_TOKENS.mapValues { (_, name) ->
            name
        }

        val rules: List<TransactionRule> = listOf(
            DeclinedRule(),
            PendingStatusRule(),
            CardPaymentRule(),
            RefundRule(),
            BankFeeRule(feeCategoryIdResolver = { feeCategoryId }),
            SalaryRule(),
            // InternalTransferRule + InvestmentTransferRule must run before
            // MerchantMemoryRule so a confirmed merchant memory can't
            // override a safety/correctness-critical classification.
            InternalTransferRule(),
            InvestmentTransferRule(),
            MerchantMemoryRule(),
            HighConfidenceMerchantRule(
                tokenToCategory = tokenToCategory,
                categoryByName = ::catByName,
            ),
            GenericCategoryRule(
                patternToCategoryName = GENERIC_CATEGORY_PATTERNS,
                categoryByName = ::catByName,
            ),
        )
        // Sanity: assert we cover the documented priority order. If a new
        // rule is added without updating this list, the engine will still
        // work but the priority coverage assertion below will fail in tests.
        return RuleEngine(rules)
    }

    /** Exposed so the tests can verify the priority order documented above. */
    fun documentedRuleOrder(): List<RulePriority> = listOf(
        RulePriority.SAFETY,             // DeclinedRule + PendingStatusRule
        RulePriority.SAFETY_CRITICAL,    // CardPaymentRule + RefundRule + BankFeeRule + SalaryRule
        RulePriority.INTERNAL_TRANSFER,  // InternalTransferRule + InvestmentTransferRule
        RulePriority.MERCHANT_MEMORY,    // MerchantMemoryRule
        RulePriority.MERCHANT_RULE,      // HighConfidenceMerchantRule
        RulePriority.CATEGORY_RULE,      // GenericCategoryRule
    )

    /** Exposed so the test suite can reference the same constants. */
    val highConfidenceMerchantTokens: Map<String, String> get() = HIGH_CONFIDENCE_MERCHANT_TOKENS
    val genericCategoryPatterns: List<Pair<Regex, String>> get() = GENERIC_CATEGORY_PATTERNS

    /** Sanity helper: does a parsed type match a known safety pattern? */
    fun isTransferLikeType(t: TransactionType): Boolean = t in setOf(
        TransactionType.TRANSFER_IN,
        TransactionType.TRANSFER_OUT,
        TransactionType.INTERNAL_TRANSFER,
    )
}
