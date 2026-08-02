package com.baraa.masroof.rules.rules

import com.baraa.masroof.rules.RuleContext
import com.baraa.masroof.rules.RuleInput
import com.baraa.masroof.rules.RulePriority
import com.baraa.masroof.rules.RuleResult
import com.baraa.masroof.rules.TransactionRule
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionType

/**
 * Treats an investment transfer as INVESTMENT when the destination matches
 * one of the user's owned INVESTMENT_ACCOUNT entries.
 *
 * Falls through to merchant memory / generic rules if the destination is
 * unknown — the user can then confirm the assignment in the review UI.
 */
class InvestmentTransferRule : TransactionRule {
    override val name: String = "InvestmentTransferRule"
    override val priority: RulePriority = RulePriority.INTERNAL_TRANSFER
    override fun evaluate(input: RuleInput, context: RuleContext): RuleResult? {
        if (input.type != TransactionType.INVESTMENT_TRANSFER) return null
        val investments = context.ownedAccounts.filter {
            it.isOwnedByUser && it.isActive && it.accountType == AccountType.INVESTMENT_ACCOUNT
        }
        if (investments.isEmpty()) return null

        val body = input.body.orEmpty() + " " + input.parsed.merchant.orEmpty()
        val matched = investments.firstOrNull { acc ->
            acc.displayName.lowercase() in body.lowercase() ||
                (acc.institutionName?.lowercase()?.let { it in body.lowercase() } ?: false)
        }
        if (matched == null) return null

        return RuleResult(
            financialTreatment = FinancialTreatment.INVESTMENT,
            categoryId = null,
            confidence = 85,
            reason = "transfer to owned investment account: ${matched.displayName}",
            source = CategorySource.RULE,
            excludeFromSpending = true, // investments are tracked separately
        )
    }
}
