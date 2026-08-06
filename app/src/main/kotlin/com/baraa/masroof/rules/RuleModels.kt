package com.baraa.masroof.rules

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.Category
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.MerchantMemory
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.ParsedTransaction
import com.baraa.masroof.transaction.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Pure snapshot of an active typed identifier for rule evaluation.
 * Populated by the import/orchestrator layer; rules stay Room-free.
 */
data class AccountIdentifierSnapshot(
    val accountId: Long,
    val identifierType: AccountIdentifierType,
    val normalizedValue: String,
)

/**
 * Priority order for rule evaluation. Lower `order` = higher priority = evaluated
 * first. The first rule to return a non-null [RuleResult] wins.
 *
 * This order is part of the public contract — it intentionally encodes the
 * safety guarantees: a declined transaction is always IGNORED, a refund is
 * always a REFUND, a card payment is always a credit-card payment, etc. —
 * regardless of what merchant memory or generic rules would otherwise say.
 */
enum class RulePriority(val order: Int) {
    /** Declined, status-based exclusions. Highest priority. */
    SAFETY(1),

    /** Refund, credit-card payment, bank fee, salary. */
    SAFETY_CRITICAL(2),

    /** Exact internal-transfer rules using owned accounts. */
    INTERNAL_TRANSFER(3),

    /** User-confirmed merchant memory. */
    MERCHANT_MEMORY(4),

    /** High-confidence merchant rules. */
    MERCHANT_RULE(5),

    /** Generic category rules. */
    CATEGORY_RULE(6),

    /** Unclassified — surfaced for user review. */
    FALLBACK(7),
}

/**
 * Snapshot of everything a rule needs to evaluate a transaction. Pure data
 * (no Android, no DB) so rules are easy to unit-test.
 */
data class RuleInput(
    val sender: String?,
    val body: String?,
    val amount: BigDecimal?,
    val currency: com.baraa.masroof.transaction.Currency,
    val type: TransactionType,
    val status: com.baraa.masroof.transaction.TransactionStatus,
    val date: LocalDate?,
    val time: LocalTime?,
    val normalizedMerchantKey: String?,
    val parsed: ParsedTransaction,
)

/**
 * Read-only context passed to every rule. Holds the user's owned accounts,
 * merchant memory entries, the current category list, and optional typed
 * identifier snapshots for value-based account matching. Pure JVM data.
 */
data class RuleContext(
    val ownedAccounts: List<FinancialAccount>,
    val merchantMemories: List<MerchantMemory>,
    val categories: List<Category>,
    val accountIdentifiers: List<AccountIdentifierSnapshot> = emptyList(),
)

/** Output of a single rule. Null means "this rule does not match". */
data class RuleResult(
    val financialTreatment: FinancialTreatment,
    val categoryId: Long?,
    val confidence: Int,
    val reason: String,
    val source: com.baraa.masroof.transaction.CategorySource,
    /** True if the result must NOT count toward expenses / income totals. */
    val excludeFromSpending: Boolean,
)

/**
 * Contract for a single financial-treatment rule. Rules are pure: they take
 * an input + context and either return a result (rule matched) or null
 * (rule does not apply, fall through to the next rule).
 */
interface TransactionRule {
    val name: String
    val priority: RulePriority
    fun evaluate(input: RuleInput, context: RuleContext): RuleResult?
}
