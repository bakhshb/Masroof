package com.baraa.masroof.ai

/**
 * Abstraction for AI-assisted merchant categorization.
 *
 * Implementations:
 *  - [DisabledAiCategorizationProvider] — short-circuits with [FailureReason.DISABLED]
 *  - [MockAiCategorizationProvider] — deterministic / programmable for tests
 *  - [OpenAiCompatibleProvider] — minimal OpenAI-compatible remote provider
 *    with pluggable [AiHttpClient] for testability
 *
 * All providers MUST:
 *  - honor `enabled` flag in their configuration (skip when disabled)
 *  - never log request or response bodies
 *  - never include the API key in any returned [AiCategorizationResult]
 *  - return within their configured timeout
 *  - return [AiCategorizationOutcome.Failed] with a sanitized reason on
 *    any non-success path (no exception bubbles up to the caller)
 */
interface AiCategorizationProvider {
    val providerName: String
    suspend fun categorize(request: AiCategorizationRequest): AiCategorizationOutcome
}