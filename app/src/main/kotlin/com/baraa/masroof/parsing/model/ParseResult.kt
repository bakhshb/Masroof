package com.baraa.masroof.parsing.model

import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.parsing.validator.ValidationFinding

/**
 * Explicit parse outcome. Expected SMS failures must not rely on exceptions.
 *
 * Does not encode ownership, self-transfer, expense/income, or net-worth meaning.
 */
sealed interface ParseResult {
    data class Success(
        val event: ParsedEvent,
    ) : ParseResult

    data class Partial(
        val draft: ParsedEventDraft,
        val event: ParsedEvent?,
        val findings: List<ValidationFinding>,
    ) : ParseResult

    data class ReviewRequired(
        val draft: ParsedEventDraft?,
        val event: ParsedEvent?,
        val findings: List<ValidationFinding>,
        val reasons: List<String>,
    ) : ParseResult

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
