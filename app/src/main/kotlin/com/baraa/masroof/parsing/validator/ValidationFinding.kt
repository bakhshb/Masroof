package com.baraa.masroof.parsing.validator

/**
 * A single validation finding. Validators report; they do not invent replacements.
 */
data class ValidationFinding(
    val code: String,
    val message: String,
    val severity: ValidationSeverity,
)

enum class ValidationSeverity {
    ERROR,
    WARNING,
}
