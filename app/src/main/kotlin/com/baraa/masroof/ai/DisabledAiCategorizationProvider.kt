package com.baraa.masroof.ai

/**
 * No-op provider used when the user has not enabled AI or has disabled it
 * from the AI settings screen. Always returns [FailureReason.DISABLED] so
 * the engine falls through to deterministic rules + manual review.
 */
class DisabledAiCategorizationProvider : AiCategorizationProvider {
    override val providerName: String = "disabled"

    override suspend fun categorize(request: AiCategorizationRequest): AiCategorizationOutcome {
        // No diagnostic recorded — disabled calls should not appear in
        // troubleshooting screens.
        return AiCategorizationOutcome.Failed(
            reason = FailureReason.DISABLED,
            diagnostic = AiDiagnostic(
                providerName = providerName,
                modelName = "n/a",
                promptVersion = AiPromptBuilder.PROMPT_VERSION,
                responseVersion = "n/a",
                durationMs = 0L,
                success = false,
                httpStatusGroup = 0,
                cacheHit = false,
                responseValid = false,
            ),
        )
    }
}