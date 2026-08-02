package com.baraa.masroof.rules

import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.FinancialTreatment

/**
 * Deterministic rule engine. Walks the rules in [RulePriority] order; the
 * first non-null [RuleResult] wins. If no rule matches, the engine returns
 * a PENDING_REVIEW result so the transaction is surfaced in the review UI.
 *
 * The engine is pure: no I/O, no logging, no Android dependencies. It can
 * be unit-tested with hand-built [RuleInput] and [RuleContext] objects.
 */
class RuleEngine(private val rules: List<TransactionRule>) {

    /**
     * The final verdict of running the rule engine. The [reason] string is
     * diagnostic-only (safe to log / surface in the UI) and explains which
     * rule fired (or that none did).
     */
    data class Verdict(
        val financialTreatment: FinancialTreatment,
        val categoryId: Long?,
        val confidence: Int,
        val reason: String,
        val source: com.baraa.masroof.transaction.CategorySource,
        val excludeFromSpending: Boolean,
    )

    fun classify(input: RuleInput, context: RuleContext): Verdict {
        val sortedRules = rules.sortedBy { it.priority.order }
        for (rule in sortedRules) {
            val result = rule.evaluate(input, context) ?: continue
            return Verdict(
                financialTreatment = result.financialTreatment,
                categoryId = result.categoryId,
                confidence = result.confidence,
                reason = "${rule.name}: ${result.reason}",
                source = result.source,
                excludeFromSpending = result.excludeFromSpending,
            )
        }
        // No rule matched → PENDING_REVIEW. The user can edit the category
        // and treatment, and (optionally) save the choice to merchant
        // memory.
        return Verdict(
            financialTreatment = FinancialTreatment.PENDING_REVIEW,
            categoryId = null,
            confidence = 0,
            reason = "no rule matched",
            source = com.baraa.masroof.transaction.CategorySource.UNCLASSIFIED,
            excludeFromSpending = true,
        )
    }

    /**
     * Apply the engine to a stored [TransactionEntity] and return a new
     * entity with the financial-treatment fields updated. The original
     * entity is not mutated; the new one is intended to replace it via
     * [androidx.room.Update].
     */
    fun applyTo(entity: TransactionEntity, context: RuleContext): TransactionEntity {
        val input = RuleInput(
            sender = entity.originalSender,
            body = null, // the body is not stored by default for privacy
            amount = entity.amount,
            currency = entity.currency,
            type = entity.transactionType,
            status = entity.status,
            date = entity.transactionDate,
            time = entity.transactionTime,
            normalizedMerchantKey = entity.transactionSimilarityKey, // similarity key used as a proxy
            parsed = com.baraa.masroof.transaction.ParsedTransaction(
                originalSender = entity.originalSender,
                originalMessage = null,
                transactionType = entity.transactionType,
                amount = entity.amount,
                currency = entity.currency,
                merchant = entity.merchantOrBeneficiary,
                accountOrCardLastFourDigits = entity.accountOrCardLastFourDigits,
                transactionDate = entity.transactionDate,
                transactionTime = entity.transactionTime,
                status = entity.status,
                confidence = entity.confidence,
                parsingNotes = entity.parsingNotes,
            ),
        )
        val v = classify(input, context)
        return entity.copy(
            financialTreatment = v.financialTreatment,
            categoryId = v.categoryId ?: entity.categoryId,
            categorySource = v.source,
            categoryConfidence = v.confidence,
            needsReview = v.financialTreatment == FinancialTreatment.PENDING_REVIEW,
            exclusionReason = if (v.excludeFromSpending) v.reason else null,
        )
    }
}
