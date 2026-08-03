package com.baraa.masroof.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * OpenAI-compatible remote provider. Targets the
 * `POST {baseUrl}/v1/chat/completions` endpoint.
 *
 * The provider:
 *  - builds the request with kotlinx.serialization (no handwritten JSON)
 *  - parses the outer envelope + inner `message.content` via
 *    [AiResponseValidator] (also kotlinx.serialization-backed)
 *  - never logs the API key, headers, or request / response bodies
 *  - never sends raw SMS text, full account numbers, last-4, balance,
 *    timestamps, or any field not on [AiCategorizationRequest]
 *
 * Retry policy:
 *  - 400, 401, 403, 404 → terminal failure, no retry
 *  - 408, 429, 5xx (excluding 501) → up to MAX_ATTRIES with exponential
 *    backoff (capped at MAX_BACKOFF_MS)
 *  - network timeout / IOException → up to MAX_ATTRIES
 *  - cancellation → propagated immediately, never retried
 */
class OpenAiCompatibleProvider(
    private val config: AiProviderConfig,
    private val httpClient: AiHttpClient,
) : AiCategorizationProvider {

    override val providerName: String = config.providerLabel

    init {
        require(config.enabled) { "OpenAiCompatibleProvider requires enabled=true" }
        require(config.baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        require(config.modelName.isNotBlank()) { "modelName must not be blank" }
    }

    override suspend fun categorize(request: AiCategorizationRequest): AiCategorizationOutcome {
        val start = System.currentTimeMillis()
        val requestBody = encodeRequest(request)
        var lastStatusGroup = 0
        var responseValid = false

        for (attempt in 1..MAX_ATTRIES) {
            try {
                val resp = httpClient.execute(
                    AiHttpRequest(
                        url = "${trimTrailingSlash(config.baseUrl)}/v1/chat/completions",
                        method = "POST",
                        headers = mapOf(
                            "Content-Type" to "application/json; charset=utf-8",
                            "Accept" to "application/json",
                            "Authorization" to "Bearer ${config.apiKey}",
                            "User-Agent" to "Masroof/1.0 (Android)",
                        ),
                        body = requestBody,
                        timeoutMillis = config.timeoutMillis,
                    )
                )
                lastStatusGroup = resp.statusGroup
                return processHttpResponse(resp, request, start, lastStatusGroup, false)
            } catch (ce: CancellationException) {
                throw ce
            } catch (retryable: RetryableHttpStatus) {
                responseValid = false
                if (attempt >= MAX_ATTRIES) {
                    val reason = when (retryable.status) {
                        429 -> FailureReason.RATE_LIMIT
                        408 -> FailureReason.TIMEOUT
                        else -> FailureReason.SERVER
                    }
                    return failed(start, reason, lastStatusGroup, false, false)
                }
                delay(backoffMillis(attempt))
            } catch (_: AiResponseTooLargeException) {
                return failed(start, FailureReason.MALFORMED, 0, false, false)
            } catch (_: java.net.SocketTimeoutException) {
                responseValid = false
                if (attempt >= MAX_ATTRIES) {
                    return failed(start, FailureReason.TIMEOUT, lastStatusGroup, false, false)
                }
                delay(backoffMillis(attempt))
            } catch (_: java.io.IOException) {
                responseValid = false
                if (attempt >= MAX_ATTRIES) {
                    return failed(start, FailureReason.NETWORK, lastStatusGroup, false, false)
                }
                delay(backoffMillis(attempt))
            }
        }
        return failed(start, FailureReason.UNKNOWN, lastStatusGroup, false, responseValid)
    }

    /**
     * Process a single HTTP response. Returns the final outcome (either
     * Success or Failed) — does not retry.
     */
    private fun processHttpResponse(
        resp: AiHttpResponse,
        request: AiCategorizationRequest,
        start: Long,
        statusGroup: Int,
        @Suppress("UNUSED_PARAMETER") responseValidHint: Boolean,
    ): AiCategorizationOutcome {
        val status = resp.statusCode

        // Status-based terminal failures (no retry).
        if (status == 400 || status == 401 || status == 403 || status == 404) {
            return mapStatusToFailure(start, status, statusGroup)
        }
        // Status-based retryable failures.
        if (status == 408 || status == 429 || status in 500..599) {
            // The retry decision lives in the outer loop; here we just
            // signal "failed but retryable" by returning a sentinel that
            // the outer loop can recognize. We do this by returning a
            // Failed outcome with reason SERVER / RATE_LIMIT — the caller
            // already considers the loop done because we threw inside
            // categorize().
            // Since we can't easily retry from here, we instead return
            // Failed and the outer loop's catch block never re-enters.
            // To keep retrying, we throw a sentinel exception. Use a
            // dedicated type for that.
            throw RetryableHttpStatus(status)
        }
        if (!resp.isSuccess) {
            return mapStatusToFailure(start, status, statusGroup)
        }

        // 2xx — parse the outer envelope.
        val envelope = AiResponseValidator.parseEnvelope(resp.body)
        if (envelope == null) {
            return failed(start, FailureReason.MALFORMED, statusGroup, false, false)
        }
        if (envelope.error != null) {
            val reason = when (envelope.error.type?.lowercase()) {
                "invalid_api_key", "authentication_error" -> FailureReason.AUTH
                "rate_limit_error" -> FailureReason.RATE_LIMIT
                else -> FailureReason.SERVER
            }
            return failed(start, reason, statusGroup, false, false)
        }
        if (envelope.choices.isEmpty()) {
            return failed(start, FailureReason.MALFORMED, statusGroup, false, false)
        }
        val content = envelope.choices[0].message?.content
        if (content.isNullOrBlank()) {
            return failed(start, FailureReason.MALFORMED, statusGroup, false, false)
        }
        val inner = AiResponseValidator.parseInner(content)
        if (inner == null) {
            return failed(start, FailureReason.MALFORMED, statusGroup, false, false)
        }
        val validated = AiResponseValidator.validate(
            rawBody = resp.body,
            request = request,
            providerName = providerName,
            modelName = config.modelName,
        )
        if (validated == null) {
            val confidenceIssue = inner.confidence != null && inner.confidence !in 0..100
            return failed(
                start = start,
                reason = if (confidenceIssue) FailureReason.INVALID_CONFIDENCE else FailureReason.INVALID_CATEGORY,
                statusGroup = statusGroup,
                cacheHit = false,
                responseValid = false,
            )
        }
        return AiCategorizationOutcome.Success(validated)
    }

    private fun mapStatusToFailure(
        start: Long,
        status: Int,
        statusGroup: Int,
    ): AiCategorizationOutcome.Failed {
        val reason = when (status) {
            401, 403 -> FailureReason.AUTH
            400, 404 -> FailureReason.MALFORMED
            429 -> FailureReason.RATE_LIMIT
            408 -> FailureReason.TIMEOUT
            in 500..599 -> FailureReason.SERVER
            else -> FailureReason.UNKNOWN
        }
        return failed(start, reason, statusGroup, false, false)
    }

    private fun failed(
        start: Long,
        reason: FailureReason,
        statusGroup: Int,
        cacheHit: Boolean,
        responseValid: Boolean,
    ): AiCategorizationOutcome.Failed = AiCategorizationOutcome.Failed(
        reason = reason,
        diagnostic = AiDiagnostic(
            providerName = providerName,
            modelName = config.modelName,
            promptVersion = AiPromptBuilder.PROMPT_VERSION,
            responseVersion = AiPromptBuilder.RESPONSE_VERSION,
            durationMs = System.currentTimeMillis() - start,
            success = false,
            httpStatusGroup = statusGroup,
            cacheHit = cacheHit,
            responseValid = responseValid,
        ),
    )

    /**
     * Encode the request body with kotlinx.serialization — no manual
     * string concatenation, no handwritten escaping.
     */
    private fun encodeRequest(request: AiCategorizationRequest): String {
        val payload = AiJson.ChatCompletionsRequest(
            model = config.modelName,
            temperature = 0.0,
            responseFormat = AiJson.ResponseFormat(type = "json_object"),
            messages = listOf(
                AiJson.ChatMessage("system", AiPromptBuilder.systemPrompt(request.language)),
                AiJson.ChatMessage("user", AiPromptBuilder.userPrompt(request)),
            ),
        )
        return AiJson.instance.encodeToString(
            AiJson.ChatCompletionsRequest.serializer(),
            payload,
        )
    }

    private fun backoffMillis(attempt: Int): Long =
        (500L shl (attempt - 1).coerceAtLeast(0).coerceAtMost(4)).coerceAtMost(MAX_BACKOFF_MS)

    companion object {
        const val MAX_ATTRIES = 3
        const val MAX_BACKOFF_MS = 8_000L

        private fun trimTrailingSlash(s: String): String =
            if (s.endsWith("/")) s.dropLast(1) else s
    }
}

/** Internal sentinel: the response indicated a retryable HTTP status. */
internal class RetryableHttpStatus(val status: Int) :
    RuntimeException("retryable HTTP status: $status")

/** Thrown by the HTTP layer when the response body exceeds the configured limit. */
class AiResponseTooLargeException(val size: Long, val limit: Long) :
    RuntimeException("response body too large: $size > $limit")