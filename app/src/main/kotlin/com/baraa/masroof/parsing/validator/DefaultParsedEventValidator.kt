package com.baraa.masroof.parsing.validator

import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.parsing.model.ParsedEventDraft
import java.math.BigDecimal

/**
 * Bank-agnostic validator encoding unambiguous PARSING_SPEC rules that do not
 * require AlJazira label dictionaries.
 *
 * Implemented now:
 * - V-001 / V-002: amount must not equal card/account last4 solely as a number
 * - V-008: missing merchant on purchase is a warning, not an error
 * - V-009: missing amount on financial families is an error
 *
 * Deferred to bank-specific P4 validation where label/context evidence is required:
 * V-003…V-007.
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

        val amount = draft.amount?.amount
        if (amount != null) {
            val cardLast4 = draft.cardRef?.last4
            if (cardLast4 != null && amountEqualsLast4(amount, cardLast4)) {
                findings += ValidationFinding(
                    code = "V-001",
                    message = "Amount equals card last4 ($cardLast4); unsafe",
                    severity = ValidationSeverity.ERROR,
                )
            }
            val accountLast4s = listOfNotNull(
                draft.sourceAccountRef?.maskedNumber,
                draft.destinationAccountRef?.maskedNumber,
            )
            accountLast4s.forEach { last4 ->
                if (amountEqualsLast4(amount, last4)) {
                    findings += ValidationFinding(
                        code = "V-002",
                        message = "Amount equals account suffix ($last4); unsafe",
                        severity = ValidationSeverity.ERROR,
                    )
                }
            }
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

    private fun amountEqualsLast4(amount: BigDecimal, last4: String): Boolean {
        val digits = last4.filter { it.isDigit() }
        if (digits.length != 4) return false
        return try {
            amount.stripTrailingZeros().compareTo(BigDecimal(digits)) == 0
        } catch (_: NumberFormatException) {
            false
        }
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
