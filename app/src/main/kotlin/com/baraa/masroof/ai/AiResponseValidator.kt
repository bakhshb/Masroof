package com.baraa.masroof.ai

import kotlinx.serialization.SerializationException

/**
 * Single source of truth for parsing the OpenAI-compatible response.
 *
 * Two layers of deserialization:
 *   1. Outer envelope via [AiJson.ChatCompletionsResponse] → typed.
 *   2. Inner `choices[0].message.content` is itself a JSON STRING; we
 *      parse it with [AiJson.CategorizationPayload] → typed.
 *
 * The handwritten brace-balanced parser and escape-aware substring
 * extractor used in earlier versions have been removed.
 *
 * Failure modes (all return null from [validate]; the caller maps them
 * to [FailureReason]):
 *  - empty / blank choices
 *  - missing message or null content
 *  - malformed outer JSON
 *  - malformed inner JSON
 *  - missing / out-of-range confidence
 *  - invented category id (not in the request's allowed list)
 *  - provider error envelope
 *  - oversized body (handled by the HTTP layer before this is called)
 */
object AiResponseValidator {

    /** Cap on explanation length. Anything longer is truncated. */
    private const val MAX_EXPLANATION_LENGTH = 200

    /** Truncation length for raw provider error messages. */
    private const val MAX_ERROR_LENGTH = 120

    /**
     * Validate the response body. Returns a sanitized
     * [AiCategorizationResult] on success; null on any failure.
     */
    fun validate(
        rawBody: String,
        request: AiCategorizationRequest,
        providerName: String,
        modelName: String,
    ): AiCategorizationResult? {
        // 1. Outer envelope.
        val envelope = parseEnvelope(rawBody) ?: return null
        if (envelope.error != null) {
            // Provider returned an explicit error payload — sanitize and
            // signal MALFORMED (the caller maps it). We do NOT echo the
            // raw error message into the result.
            return null
        }
        if (envelope.choices.isEmpty()) return null
        val first = envelope.choices[0]
        val content = first.message?.content?.takeIf { it.isNotBlank() } ?: return null
        // 2. Inner categorization payload.
        val inner = parseInner(content) ?: return null
        val categoryId = inner.categoryId ?: return null
        val categoryName = inner.categoryName?.trim().orEmpty()
        val normalizedMerchant = inner.normalizedMerchantName?.trim().orEmpty()
        val confidence = inner.confidence ?: return null
        val explanation = inner.explanation?.trim().orEmpty()
        // 3. Sanity-check the payload against the request's allowed list.
        if (categoryName.isEmpty() || normalizedMerchant.isEmpty()) return null
        if (confidence !in 0..100) return null
        if (request.allowedCategories.none { it.id == categoryId }) return null
        return AiCategorizationResult(
            categoryId = categoryId,
            categoryName = categoryName,
            normalizedMerchantName = normalizedMerchant,
            confidence = confidence,
            explanation = if (explanation.length > MAX_EXPLANATION_LENGTH) {
                explanation.substring(0, MAX_EXPLANATION_LENGTH)
            } else {
                explanation
            },
            providerName = providerName,
            modelName = modelName,
            responseVersion = AiPromptBuilder.RESPONSE_VERSION,
        )
    }

    /**
     * Validate a bare categorization JSON object (on-device models return
     * JSON directly, not an OpenAI chat-completions envelope).
     */
    fun validateDirect(
        rawJson: String,
        request: AiCategorizationRequest,
        providerName: String,
        modelName: String,
    ): AiCategorizationResult? {
        val inner = parseInner(rawJson) ?: return null
        val categoryId = inner.categoryId ?: return null
        val categoryName = inner.categoryName?.trim().orEmpty()
        val normalizedMerchant = inner.normalizedMerchantName?.trim()
            ?.ifEmpty { request.normalizedMerchant }
            .orEmpty()
            .ifEmpty { request.normalizedMerchant }
        val confidence = inner.confidence ?: return null
        val explanation = inner.explanation?.trim().orEmpty()
        if (categoryName.isEmpty()) return null
        if (normalizedMerchant.isEmpty()) return null
        if (confidence !in 0..100) return null
        if (request.allowedCategories.none { it.id == categoryId }) return null
        return AiCategorizationResult(
            categoryId = categoryId,
            categoryName = categoryName,
            normalizedMerchantName = normalizedMerchant,
            confidence = confidence,
            explanation = if (explanation.length > MAX_EXPLANATION_LENGTH) {
                explanation.substring(0, MAX_EXPLANATION_LENGTH)
            } else {
                explanation
            },
            providerName = providerName,
            modelName = modelName,
            responseVersion = AiPromptBuilder.RESPONSE_VERSION,
        )
    }

    /** Parse the outer envelope, returning null on malformed input. */
    fun parseEnvelope(rawBody: String): AiJson.ChatCompletionsResponse? {
        if (rawBody.isBlank()) return null
        return try {
            AiJson.instance.decodeFromString(
                AiJson.ChatCompletionsResponse.serializer(),
                rawBody,
            )
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Parse the inner categorization payload. */
    fun parseInner(rawContent: String): AiJson.CategorizationPayload? {
        if (rawContent.isBlank()) return null
        return try {
            AiJson.instance.decodeFromString(
                AiJson.CategorizationPayload.serializer(),
                rawContent,
            )
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Build a safe, truncated provider-error string for logging. The
     * raw provider error message is sanitized to a short summary so it
     * does not leak into logs or UI.
     */
    fun summarizeError(error: AiJson.ProviderError?): String? {
        if (error == null) return null
        val message = error.message.take(MAX_ERROR_LENGTH)
        val type = error.type?.take(40)
        return if (type.isNullOrBlank()) message else "$type: $message"
    }
}