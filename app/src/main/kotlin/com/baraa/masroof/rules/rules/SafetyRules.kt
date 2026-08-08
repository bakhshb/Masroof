package com.baraa.masroof.rules.rules

import com.baraa.masroof.rules.RuleContext
import com.baraa.masroof.rules.RuleInput
import com.baraa.masroof.rules.RulePriority
import com.baraa.masroof.rules.RuleResult
import com.baraa.masroof.rules.TransactionRule
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType

/**
 * Declined transactions are ALWAYS IGNORED, regardless of merchant memory
 * or any other rule. Safety priority = 1.
 */
class DeclinedRule : TransactionRule {
    override val name: String = "DeclinedRule"
    override val priority: RulePriority = RulePriority.SAFETY
    override fun evaluate(input: RuleInput, context: RuleContext): RuleResult? {
        if (input.status != TransactionStatus.DECLINED) return null
        return RuleResult(
            financialTreatment = FinancialTreatment.IGNORED,
            categoryId = null,
            confidence = 100,
            reason = "transaction was declined",
            source = CategorySource.RULE,
            excludeFromSpending = true,
        )
    }
}

/**
 * Credit-limit change notices are informational only — never spending and
 * never posted to the ledger.
 */
class CreditLimitChangeRule : TransactionRule {
    override val name: String = "CreditLimitChangeRule"
    override val priority: RulePriority = RulePriority.SAFETY
    override fun evaluate(input: RuleInput, context: RuleContext): RuleResult? {
        val body = input.body.orEmpty().lowercase(java.util.Locale.ROOT)
        val fromBody = LIMIT_CUES.any { it in body }
        if (!fromBody) return null
        return RuleResult(
            financialTreatment = FinancialTreatment.IGNORED,
            categoryId = null,
            confidence = 100,
            reason = "credit limit change — ignored for balances",
            source = CategorySource.RULE,
            excludeFromSpending = true,
        )
    }

    companion object {
        private val LIMIT_CUES = listOf(
            "تغيير حد الرصيد",
            "تم تغيير الحد الائتماني",
            "تغيير الحد الائتماني",
            "الحد الائتماني الجديد",
            "credit limit change",
            "new credit limit",
        )
    }
}

/**
 * Transactions still pending confirmation stay PENDING_REVIEW. The
 * spending calculator excludes them from confirmed totals.
 */
class PendingStatusRule : TransactionRule {
    override val name: String = "PendingStatusRule"
    override val priority: RulePriority = RulePriority.SAFETY
    override fun evaluate(input: RuleInput, context: RuleContext): RuleResult? {
        if (input.status != TransactionStatus.PENDING) return null
        return RuleResult(
            financialTreatment = FinancialTreatment.PENDING_REVIEW,
            categoryId = null,
            confidence = 100,
            reason = "transaction is still pending",
            source = CategorySource.RULE,
            excludeFromSpending = true,
        )
    }
}
