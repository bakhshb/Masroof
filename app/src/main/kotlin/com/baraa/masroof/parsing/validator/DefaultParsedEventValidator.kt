package com.baraa.masroof.parsing.validator

import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.parsing.model.ParsedEventDraft

/**
 * Bank-agnostic validator for unambiguous PARSING_SPEC rules that do not need
 * extractor provenance or AlJazira label dictionaries.
 *
 * Implemented now:
 * - V-008: missing merchant on purchase is a warning, not an error
 * - V-009: missing amount on financial families is an error
 *
 * Deferred to P4 (require extraction provenance / bank context):
 * - V-001 / V-002 (card/account suffix must not *become* amount)
 * - V-003…V-007
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

        if (draft.parseStatus == ParseStatus.SUCCESS && findings.any { it.severity == ValidationSeverity.ERROR }) {
            findings += ValidationFinding(
                code = "STATUS_CONFLICT",
                message = "ParseStatus.SUCCESS is incompatible with validation errors",
                severity = ValidationSeverity.ERROR,
            )
        }

        return ValidationResult(findings)
    }

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
