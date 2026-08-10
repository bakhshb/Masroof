package com.baraa.masroof.domain.model

/**
 * Outcome of parsing a single SMS into a [ParsedEvent].
 */
enum class ParseStatus {
    SUCCESS,
    PARTIAL,
    REVIEW_REQUIRED,
    NON_FINANCIAL,
    UNSUPPORTED,
    INVALID,
}
