package com.baraa.masroof.rules.rules

import com.baraa.masroof.ledger.LocalTreatmentAuditor
import com.baraa.masroof.rules.RuleContext
import com.baraa.masroof.rules.RuleInput
import com.baraa.masroof.rules.RulePriority
import com.baraa.masroof.rules.RuleResult
import com.baraa.masroof.rules.TransactionRule
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionType

/**
 * Last-resort treatment from parser type + on-device SMS phrase audit when
 * no merchant/category rule matched. Delegates to [LocalTreatmentAuditor].
 */
class ParsedTypeFallbackRule : TransactionRule {
    override val name: String = "ParsedTypeFallbackRule"
    override val priority: RulePriority = RulePriority.FALLBACK

    override fun evaluate(input: RuleInput, context: RuleContext): RuleResult? {
        val audit = LocalTreatmentAuditor.audit(
            type = input.type,
            body = input.body,
            currentTreatment = FinancialTreatment.PENDING_REVIEW,
            hasConfirmedTwoOwnedSides = false,
        )
        if (audit.treatment == FinancialTreatment.PENDING_REVIEW) return null
        if (audit.treatment == FinancialTreatment.IGNORED) {
            return RuleResult(
                financialTreatment = audit.treatment,
                categoryId = null,
                confidence = audit.confidence,
                reason = audit.reasonAr,
                source = CategorySource.RULE,
                excludeFromSpending = true,
            )
        }
        // Two-sided treatments without both accounts stay as pending so the
        // review queue / InternalTransferRule / card-payment path can finish.
        if (audit.treatment.requiresTwoAccounts && !audit.autoApply) {
            return null
        }
        return RuleResult(
            financialTreatment = audit.treatment,
            categoryId = null,
            confidence = audit.confidence,
            reason = audit.reasonAr,
            source = CategorySource.RULE,
            excludeFromSpending = audit.treatment != FinancialTreatment.EXPENSE &&
                audit.treatment != FinancialTreatment.BANK_FEE,
        )
    }

    companion object {
        /** Shared mapping used by import rules and historical relink/post. */
        fun treatmentFor(type: TransactionType, body: String? = null): FinancialTreatment? {
            val treatment = LocalTreatmentAuditor.treatmentFor(type, body)
            return treatment.takeUnless { it == FinancialTreatment.PENDING_REVIEW }
        }
    }
}
