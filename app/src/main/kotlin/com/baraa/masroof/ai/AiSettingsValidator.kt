package com.baraa.masroof.ai

/**
 * Validates AI settings. Pure (no I/O), so unit-testable.
 *
 * The settings UI uses this to surface field-level errors and to
 * decide whether the "Test Connection" button is enabled.
 */
object AiSettingsValidator {

    /**
     * Result of validating one field. [errorKey] maps to a user-facing
     * string resource id from the UI layer.
     */
    data class FieldError(val field: Field, val errorKey: ErrorKey)

    enum class Field { BASE_URL, MODEL_NAME, API_KEY, MIN_CONFIDENCE }
    enum class ErrorKey {
        EMPTY_BASE_URL,
        INVALID_BASE_URL,
        HTTP_NOT_ALLOWED,
        EMPTY_MODEL_NAME,
        MISSING_API_KEY,
        CONFIDENCE_OUT_OF_RANGE,
    }

    /**
     * Validate all fields. Returns the list of errors (empty when the
     * config is ready to use).
     */
    fun validate(config: AiProviderConfig, hasApiKey: Boolean): List<FieldError> {
        val errors = ArrayList<FieldError>()
        // Base URL.
        val url = config.baseUrl.trim()
        if (url.isEmpty()) {
            errors += FieldError(Field.BASE_URL, ErrorKey.EMPTY_BASE_URL)
        } else if (!isValidUrl(url)) {
            errors += FieldError(Field.BASE_URL, ErrorKey.INVALID_BASE_URL)
        } else if (config.requireHttps && url.lowercase().startsWith("http://")) {
            errors += FieldError(Field.BASE_URL, ErrorKey.HTTP_NOT_ALLOWED)
        }
        // Model name.
        if (config.modelName.isBlank()) {
            errors += FieldError(Field.MODEL_NAME, ErrorKey.EMPTY_MODEL_NAME)
        }
        // API key.
        if (!hasApiKey) {
            errors += FieldError(Field.API_KEY, ErrorKey.MISSING_API_KEY)
        }
        // Confidence.
        if (config.minimumConfidence !in 0..100) {
            errors += FieldError(Field.MIN_CONFIDENCE, ErrorKey.CONFIDENCE_OUT_OF_RANGE)
        }
        return errors
    }

    /**
     * Acceptable URLs are HTTPS / HTTP. We do NOT enforce HTTPS by
     * default here — the caller decides whether to flag HTTP as an
     * error (see [requireHttps] in [AiProviderConfig]).
     */
    private fun isValidUrl(s: String): Boolean {
        val regex = Regex("^https?://[^\\s]+$", RegexOption.IGNORE_CASE)
        return regex.matches(s)
    }
}