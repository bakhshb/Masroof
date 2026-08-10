package com.baraa.masroof.parsing.model

import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.parsing.validator.ValidationFinding

/**
 * Explicit parse outcome. Expected SMS failures must not rely on exceptions.
 *
 * [ParsedEvent.parseStatus] must agree with the sealed variant when an event is present.
 * Does not encode ownership, self-transfer, expense/income, or net-worth meaning.
 *
 * [ParsedEventDetails] carries typed parse-time facts (reference, balances, biller,
 * local timestamp) that are not on [ParsedEvent].
 */
sealed interface ParseResult {
    data class Success(
        val event: ParsedEvent,
        val details: ParsedEventDetails = ParsedEventDetails(),
    ) : ParseResult {
        init {
            require(event.parseStatus == ParseStatus.SUCCESS) {
                "ParseResult.Success requires event.parseStatus == SUCCESS, was ${event.parseStatus}"
            }
        }
    }

    data class Partial(
        val draft: ParsedEventDraft,
        val event: ParsedEvent?,
        val findings: List<ValidationFinding>,
        val details: ParsedEventDetails = ParsedEventDetails(),
    ) : ParseResult {
        init {
            if (event != null) {
                require(event.parseStatus == ParseStatus.PARTIAL) {
                    "ParseResult.Partial event must have parseStatus PARTIAL, was ${event.parseStatus}"
                }
            }
        }
    }

    data class ReviewRequired(
        val draft: ParsedEventDraft?,
        val event: ParsedEvent?,
        val findings: List<ValidationFinding>,
        val reasons: List<String>,
        val details: ParsedEventDetails = ParsedEventDetails(),
    ) : ParseResult {
        init {
            if (event != null) {
                require(event.parseStatus == ParseStatus.REVIEW_REQUIRED) {
                    "ParseResult.ReviewRequired event must have parseStatus REVIEW_REQUIRED, was ${event.parseStatus}"
                }
            }
        }
    }

    data class NonFinancial(
        val reason: String,
        val confidence: Confidence? = null,
        val event: ParsedEvent? = null,
        val details: ParsedEventDetails = ParsedEventDetails(),
    ) : ParseResult {
        init {
            if (event != null) {
                require(event.parseStatus == ParseStatus.NON_FINANCIAL) {
                    "ParseResult.NonFinancial event must have parseStatus NON_FINANCIAL, was ${event.parseStatus}"
                }
            }
        }
    }

    data class Unsupported(
        val reason: String,
    ) : ParseResult

    data class Invalid(
        val findings: List<ValidationFinding>,
        val draft: ParsedEventDraft? = null,
    ) : ParseResult
}
