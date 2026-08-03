package com.baraa.masroof.ai

/**
 * Test-only provider that returns pre-programmed responses. NEVER call
 * this from production code paths — the application always picks
 * [DisabledAiCategorizationProvider] or [OpenAiCompatibleProvider] based
 * on user settings.
 *
 * Used in unit tests to:
 *  - verify the engine invokes the AI categorizer exactly when expected
 *  - verify request payloads exclude sensitive fields
 *  - verify cache reuse, confidence thresholds, and acceptance flow
 */
class MockAiCategorizationProvider(
    var responses: MutableMap<String, AiCategorizationResult> = mutableMapOf(),
    private val defaultResponse: AiCategorizationResult? = null,
    var nextOutcome: AiCategorizationOutcome? = null,
    var invocations: Int = 0,
    var lastRequest: AiCategorizationRequest? = null,
) : AiCategorizationProvider {
    override val providerName: String = "mock"

    override suspend fun categorize(request: AiCategorizationRequest): AiCategorizationOutcome {
        invocations += 1
        lastRequest = request
        nextOutcome?.let { return it }
        val key = request.normalizedMerchant.lowercase().trim()
        val result = responses[key] ?: defaultResponse
        return if (result != null) {
            AiCategorizationOutcome.Success(result)
        } else {
            AiCategorizationOutcome.Unclassified
        }
    }
}