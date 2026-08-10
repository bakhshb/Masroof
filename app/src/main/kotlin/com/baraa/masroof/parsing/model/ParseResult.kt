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
 */
sealed interface ParseResult {
    data class Success(
        val event: ParsedEvent,
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
    ) : ParseResult

    data class Unsupported(
        val reason: String,
    ) : ParseResult

    data class Invalid(
        val findings: List<ValidationFinding>,
        val draft: ParsedEventDraft? = null,
    ) : ParseResult
}
