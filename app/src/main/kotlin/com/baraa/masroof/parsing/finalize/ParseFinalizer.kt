package com.baraa.masroof.parsing.finalize

import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.parsing.model.ParseResult
import com.baraa.masroof.parsing.model.ParsedEventDraft
import com.baraa.masroof.parsing.validator.ParsedEventValidator
import com.baraa.masroof.parsing.validator.ValidationFinding
import com.baraa.masroof.parsing.validator.ValidationSeverity

/**
 * Controlled finalization gate: the only production path that may emit
 * [ParseResult.Success].
 *
 * extract → draft → validate → finalize
 */
class ParseFinalizer(
    private val validator: ParsedEventValidator,
) {
    fun finalize(draft: ParsedEventDraft, eventId: String): ParseResult {
        val validation = validator.validate(draft)
        val family = draft.messageFamily
        val details = draft.details

        if (family == null || draft.bank == null || draft.confidence == null) {
            return ParseResult.Invalid(
                findings = validation.findings + ValidationFinding(
                    code = "INCOMPLETE_DRAFT",
                    message = "Draft lacks bank, family, or confidence",
                    severity = ValidationSeverity.ERROR,
                ),
                draft = draft,
            )
        }

        when (family) {
            MessageFamily.OTP,
            MessageFamily.NON_FINANCIAL,
            MessageFamily.BALANCE_NOTICE,
            -> {
                val finalized = draft.copy(parseStatus = ParseStatus.NON_FINANCIAL)
                    .toParsedEvent(eventId)
                return ParseResult.NonFinancial(
                    reason = "non_financial_family:$family",
                    confidence = draft.confidence,
                    event = finalized,
                    details = details,
                )
            }

            MessageFamily.UNKNOWN -> {
                val finalized = draft.copy(parseStatus = ParseStatus.REVIEW_REQUIRED)
                    .toParsedEvent(eventId)
                return ParseResult.ReviewRequired(
                    draft = draft.copy(parseStatus = ParseStatus.REVIEW_REQUIRED),
                    event = finalized,
                    details = details,
                    findings = validation.findings,
                    reasons = listOf("unknown_or_unsupported_format") +
                        validation.errors.map { it.message },
                )
            }

            else -> {
                if (validation.errors.isNotEmpty()) {
                    val hasAmbiguousAmount = validation.errors.any { it.code == "V-007" }
                    val status = if (hasAmbiguousAmount || family.isFinancial) {
                        ParseStatus.REVIEW_REQUIRED
                    } else {
                        ParseStatus.INVALID
                    }
                    val withStatus = draft.copy(parseStatus = status)
                    return if (status == ParseStatus.REVIEW_REQUIRED) {
                        ParseResult.ReviewRequired(
                            draft = withStatus,
                            event = runCatching { withStatus.toParsedEvent(eventId) }.getOrNull(),
                            details = details,
                            findings = validation.findings,
                            reasons = validation.errors.map { it.message },
                        )
                    } else {
                        ParseResult.Invalid(findings = validation.findings, draft = withStatus)
                    }
                }

                if (!validation.isAcceptableForAutomaticUse) {
                    val withStatus = draft.copy(parseStatus = ParseStatus.REVIEW_REQUIRED)
                    return ParseResult.ReviewRequired(
                        draft = withStatus,
                        event = runCatching { withStatus.toParsedEvent(eventId) }.getOrNull(),
                        details = details,
                        findings = validation.findings,
                        reasons = validation.findings.map { it.message },
                    )
                }

                val successDraft = draft.copy(parseStatus = ParseStatus.SUCCESS)
                val event = successDraft.toParsedEvent(eventId)
                return ParseResult.Success(event = event, details = details)
            }
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
