package com.baraa.masroof.ai

/**
 * Provider's structured JSON response.
 *
 * **Validation contract** — every field is checked before the result is
 * persisted or returned to the engine. Invalid responses degrade safely:
 *  - unknown category id → result is dropped (treated as UNCLASSIFIED)
 *  - confidence outside 0..100 → dropped
 *  - missing required fields → dropped
 *  - empty / malformed JSON → dropped
 *  - oversized [explanation] → truncated
 *
 * The `providerName` and `modelName` are for UI diagnostics only; they
 * never include API keys.
 */
data class AiCategorizationResult(
    val categoryId: Long,
    val categoryName: String,
    val normalizedMerchantName: String,
    val confidence: Int,
    val explanation: String,
    val providerName: String,
    val modelName: String,
    val responseVersion: String,
) {
    /** A safe diagnostic copy with the explanation shortened. */
    fun toDiagnosticCopy(): AiCategorizationResult = copy(
        explanation = if (explanation.length > MAX_DIAGNOSTIC_EXPLANATION) {
            explanation.substring(0, MAX_DIAGNOSTIC_EXPLANATION) + "…"
        } else {
            explanation
        }
    )

    companion object {
        const val MAX_DIAGNOSTIC_EXPLANATION = 80
    }
}

/**
 * Outcome of calling an AI categorizer. [UNCLASSIFIED] means the AI did
 * not produce a usable suggestion and the engine should fall through to
 * manual review. [MALFORMED] covers parsing / validation failures. The
 * non-sensitive diagnostic fields are exposed so the UI can show
 * "vendor X, model Y, 312ms" without leaking the prompt or response.
 */
sealed interface AiCategorizationOutcome {
    data class Success(val result: AiCategorizationResult) : AiCategorizationOutcome
    data object Unclassified : AiCategorizationOutcome
    data class Failed(val reason: FailureReason, val diagnostic: AiDiagnostic) : AiCategorizationOutcome
}

enum class FailureReason {
    DISABLED,
    NETWORK,
    TIMEOUT,
    AUTH,                  // 401 / 403 — never retry
    RATE_LIMIT,            // 429 — back off
    MALFORMED,             // JSON parse failure
    INVALID_CATEGORY,      // model returned an unknown category id
    INVALID_CONFIDENCE,    // confidence outside 0..100
    SERVER,                // 5xx
    UNKNOWN,
}

data class AiDiagnostic(
    val providerName: String,
    val modelName: String,
    val promptVersion: String,
    val responseVersion: String,
    val durationMs: Long,
    val success: Boolean,
    val httpStatusGroup: Int,   // 0 = no HTTP call; 1xx/2xx/3xx/4xx/5xx as hundreds
    val cacheHit: Boolean,
    val responseValid: Boolean,
)