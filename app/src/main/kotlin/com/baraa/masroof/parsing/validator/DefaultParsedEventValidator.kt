package com.baraa.masroof.parsing.validator

import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.parsing.model.AmountSourceKind
import com.baraa.masroof.parsing.model.ParsedEventDraft

/**
 * Bank-agnostic validator.
 *
 * V-008, V-009: structural rules.
 * V-001…V-007: provenance-aware amount safety (enabled when extractors populate
 * [ParsedEventDraft.selectedAmount] / [ParsedEventDraft.amountCandidates]).
 *
 * Numeric coincidence between a legitimate amount and a last4 is **not** an error.
 */
class DefaultParsedEventValidator : ParsedEventValidator {
    override fun validate(draft: ParsedEventDraft): ValidationResult {
        val findings = mutableListOf<ValidationFinding>()
        val family = draft.messageFamily

        if (family != null && family.isFinancial && draft.amount == null) {
            findings += ValidationFinding(
                code = "V-009",
                message = "Required amount is missing for financial family $family",
                severity = ValidationSeverity.ERROR,
            )
        }

        if (family == MessageFamily.PURCHASE && draft.merchant.isNullOrBlank()) {
            findings += ValidationFinding(
                code = "V-008",
                message = "Merchant is missing on purchase; optional but noted",
                severity = ValidationSeverity.WARNING,
            )
        }

        findings += validateAmountProvenance(draft)

        if (draft.parseStatus == ParseStatus.SUCCESS && findings.any { it.severity == ValidationSeverity.ERROR }) {
            findings += ValidationFinding(
                code = "STATUS_CONFLICT",
                message = "ParseStatus.SUCCESS is incompatible with validation errors",
                severity = ValidationSeverity.ERROR,
            )
        }

        return ValidationResult(findings)
    }

    private fun validateAmountProvenance(draft: ParsedEventDraft): List<ValidationFinding> {
        val findings = mutableListOf<ValidationFinding>()
        val hasProvenanceContext =
            draft.selectedAmount != null || draft.amountCandidates.isNotEmpty()
        if (!hasProvenanceContext) {
            // Drafts without extractor provenance (e.g. unit fixtures) skip V-001…V-007.
            return findings
        }

        val txnCandidates = draft.amountCandidates.filter {
            it.sourceKind == AmountSourceKind.TRANSACTION_AMOUNT
        }

        if (txnCandidates.size > 1) {
            findings += ValidationFinding(
                code = "V-007",
                message = "Multiple plausible transaction amounts; cannot disambiguate safely",
                severity = ValidationSeverity.ERROR,
            )
        }

        val selected = draft.selectedAmount
        if (draft.amount != null) {
            if (selected == null) {
                findings += ValidationFinding(
                    code = "V-006",
                    message = "Transaction amount lacks extraction provenance",
                    severity = ValidationSeverity.ERROR,
                )
            } else {
                when (selected.sourceKind) {
                    AmountSourceKind.CARD_LAST4 -> findings += finding(
                        "V-001",
                        "Amount provenance is card last4 label '${selected.evidenceLabel}'",
                    )
                    AmountSourceKind.ACCOUNT_LAST4 -> findings += finding(
                        "V-002",
                        "Amount provenance is account last4 label '${selected.evidenceLabel}'",
                    )
                    AmountSourceKind.AVAILABLE_BALANCE,
                    AmountSourceKind.OUTSTANDING_BALANCE,
                    -> findings += finding(
                        "V-003",
                        "Amount provenance is balance label '${selected.evidenceLabel}'",
                    )
                    AmountSourceKind.REFERENCE -> findings += finding(
                        "V-004",
                        "Amount provenance is reference label '${selected.evidenceLabel}'",
                    )
                    AmountSourceKind.DATE_TIME -> findings += finding(
                        "V-005",
                        "Amount provenance is date/time digits",
                    )
                    AmountSourceKind.OTHER -> findings += finding(
                        "V-006",
                        "Amount not associated with a strong amount label",
                    )
                    AmountSourceKind.TRANSACTION_AMOUNT -> Unit
                }
            }
        }

        return findings
    }

    private fun finding(code: String, message: String) = ValidationFinding(
        code = code,
        message = message,
        severity = ValidationSeverity.ERROR,
    )

    private val MessageFamily.isFinancial: Boolean
        get() = when (this) {
            MessageFamily.PURCHASE,
            MessageFamily.TRANSFER_IN,
            MessageFamily.TRANSFER_OUT,
            MessageFamily.CARD_PAYMENT,
            MessageFamily.BILL_PAYMENT,
            MessageFamily.WITHDRAWAL,
            MessageFamily.REFUND,
            MessageFamily.FEE,
            -> true

            MessageFamily.BALANCE_NOTICE,
            MessageFamily.OTP,
            MessageFamily.NON_FINANCIAL,
            MessageFamily.UNKNOWN,
            -> false
        }
}
