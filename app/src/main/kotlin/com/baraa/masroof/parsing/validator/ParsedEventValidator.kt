package com.baraa.masroof.parsing.validator

import com.baraa.masroof.parsing.model.ParsedEventDraft

/**
 * Validates structured parse drafts without guessing missing values.
 */
interface ParsedEventValidator {
    fun validate(draft: ParsedEventDraft): ValidationResult
}
