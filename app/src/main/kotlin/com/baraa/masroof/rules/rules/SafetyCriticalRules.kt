package com.baraa.masroof.rules.rules

import com.baraa.masroof.rules.RuleContext
import com.baraa.masroof.rules.RuleInput
import com.baraa.masroof.rules.RulePriority
import com.baraa.masroof.rules.RuleResult
import com.baraa.masroof.rules.TransactionRule
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionType

/**
 * Credit-card bill payment: counts as a CREDIT_CARD_PAYMENT (not a new
 * expense) and never receives a normal expense category. Safety priority.
 */
class CardPaymentRule : TransactionRule {
    override val name: String = "CardPaymentRule"
    override val priority: RulePriority = RulePriority.SAFETY_CRITICAL
    override fun evaluate(input: RuleInput, context: RuleContext): RuleResult? {
        if (input.type != TransactionType.CARD_PAYMENT) return null
        return RuleResult(
            financialTreatment = FinancialTreatment.CREDIT_CARD_PAYMENT,
            categoryId = null,
            confidence = 100,
            reason = "card payment / settlement — not a new expense",
            source = CategorySource.RULE,
            excludeFromSpending = true,
        )
    }
}

/**
 * Refund: a reversal of a previous spend. Reduces net expenses.
 */
class RefundRule : TransactionRule {
    override val name: String = "RefundRule"
    override val priority: RulePriority = RulePriority.SAFETY_CRITICAL
    override fun evaluate(input: RuleInput, context: RuleContext): RuleResult? {
        if (input.type != TransactionType.REFUND) return null
        return RuleResult(
            financialTreatment = FinancialTreatment.REFUND,
            categoryId = null,
            confidence = 100,
            reason = "refund / reversal — reduces net expenses",
            source = CategorySource.RULE,
            excludeFromSpending = true, // refunds don't count as new spending; they offset
        )
    }
}

/**
 * Bank fee / service charge: counts as a BANK_FEE expense. The category is
 * looked up from the seeded "رسوم بنكية" child of "أخرى". If the user has
 * not yet seeded (or has renamed the category), the categoryId is null and
 * the user can pick one in the review UI.
 */
class BankFeeRule(
    private val feeCategoryIdResolver: () -> Long?,
) : TransactionRule {
    override val name: String = "BankFeeRule"
    override val priority: RulePriority = RulePriority.SAFETY_CRITICAL
    override fun evaluate(input: RuleInput, context: RuleContext): RuleResult? {
        if (input.type != TransactionType.FEE) return null
        return RuleResult(
            financialTreatment = FinancialTreatment.BANK_FEE,
            categoryId = feeCategoryIdResolver(),
            confidence = 100,
            reason = "bank fee / service charge",
            source = CategorySource.RULE,
            excludeFromSpending = false, // counts as an expense
        )
    }
}

/**
 * Salary deposit: counts as INCOME, not an expense.
 */
class SalaryRule : TransactionRule {
    override val name: String = "SalaryRule"
    override val priority: RulePriority = RulePriority.SAFETY_CRITICAL
    override fun evaluate(input: RuleInput, context: RuleContext): RuleResult? {
        if (input.type != TransactionType.SALARY) return null
        return RuleResult(
            financialTreatment = FinancialTreatment.INCOME,
            categoryId = null,
            confidence = 100,
            reason = "salary / wages",
            source = CategorySource.RULE,
            excludeFromSpending = true,
        )
    }
}
