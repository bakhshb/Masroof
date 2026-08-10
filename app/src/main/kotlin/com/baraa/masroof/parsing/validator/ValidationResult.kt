package com.baraa.masroof.parsing.validator

/**
 * Aggregated validation outcome for a parse draft/event.
 */
data class ValidationResult(
    val findings: List<ValidationFinding>,
) {
    val errors: List<ValidationFinding>
        get() = findings.filter { it.severity == ValidationSeverity.ERROR }

    val warnings: List<ValidationFinding>
        get() = findings.filter { it.severity == ValidationSeverity.WARNING }

    val isAcceptableForAutomaticUse: Boolean
        get() = errors.isEmpty()
}
