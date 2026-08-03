package com.baraa.masroof.ai

import kotlinx.coroutines.CancellationException

/**
 * OpenAI-compatible remote provider. Targets the
 * `POST {baseUrl}/v1/chat/completions` endpoint (configurable base URL).
 *
 * The provider never:
 *  - logs the API key
 *  - logs request / response bodies
 *  - retries authentication failures (4xx other than 429)
 *  - retries indefinitely
 *  - sends raw SMS text, full account numbers, last-4 digits, balance,
 *    timestamps, or any field that is not on [AiCategorizationRequest]
 *
 * Backoff strategy:
 *  - 429 → exponential backoff (1s, 2s, 4s, capped at MAX_RETRIES)
 *  - 5xx → exponential backoff
 *  - 401/403 → no retry
 *  - 4xx other → no retry
 *  - timeout → one retry
 *  - network error → one retry
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
        val body = buildRequestBody(request)
        var attempt = 0
        var lastStatusGroup = 0
        var responseValid = false
        while (attempt < MAX_ATTEMPTS) {
            try {
                val resp = httpClient.execute(
                    AiHttpRequest(
                        url = "${trimTrailingSlash(config.baseUrl)}/v1/chat/completions",
                        method = "POST",
                        headers = mapOf(
                            "Content-Type" to "application/json",
                            "Authorization" to "Bearer ${config.apiKey}",
                        ),
                        body = body,
                        timeoutMillis = config.timeoutMillis,
                    )
                )
                lastStatusGroup = resp.statusGroup
                if (resp.isSuccess) {
                    val content = extractContentField(resp.body)
                    if (content == null) {
                        return failed(
                            start = start,
                            reason = FailureReason.MALFORMED,
                            statusGroup = resp.statusGroup,
                            cacheHit = false,
                            responseValid = false,
                        )
                    }
                    val result = AiResponseParser.validate(
                        rawBody = content,
                        request = request,
                        providerName = providerName,
                        modelName = config.modelName,
                    )
                    responseValid = result != null
                    return if (result != null) {
                        AiCategorizationOutcome.Success(result)
                    } else {
                        failed(
                            start = start,
                            reason = FailureReason.INVALID_CATEGORY,
                            statusGroup = resp.statusGroup,
                            cacheHit = false,
                            responseValid = false,
                        )
                    }
                }
                if (resp.statusCode == 401 || resp.statusCode == 403) {
                    return failed(start, FailureReason.AUTH, resp.statusGroup, false, false)
                }
                if (resp.statusCode == 429) {
                    // backoff and retry
                    attempt++
                    kotlinx.coroutines.delay(backoffMillis(attempt))
                    continue
                }
                if (resp.statusCode in 500..599) {
                    attempt++
                    kotlinx.coroutines.delay(backoffMillis(attempt))
                    continue
                }
                return failed(start, FailureReason.MALFORMED, resp.statusGroup, false, false)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: java.net.SocketTimeoutException) {
                attempt++
                if (attempt >= MAX_ATTEMPTS) {
                    return failed(start, FailureReason.TIMEOUT, lastStatusGroup, false, responseValid)
                }
                kotlinx.coroutines.delay(backoffMillis(attempt))
            } catch (_: java.io.IOException) {
                attempt++
                if (attempt >= MAX_ATTEMPTS) {
                    return failed(start, FailureReason.NETWORK, lastStatusGroup, false, responseValid)
                }
                kotlinx.coroutines.delay(backoffMillis(attempt))
            }
        }
        return failed(start, FailureReason.UNKNOWN, lastStatusGroup, false, responseValid)
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

    private fun buildRequestBody(request: AiCategorizationRequest): String {
        // Tiny hand-built JSON body to avoid pulling in a JSON library.
        val system = escapeJson(AiPromptBuilder.systemPrompt(request.language))
        val user = escapeJson(AiPromptBuilder.userPrompt(request))
        return buildString {
            append("{")
            append("\"model\":\"").append(escapeJson(config.modelName)).append("\",")
            append("\"temperature\":0,")
            append("\"messages\":[")
            append("{\"role\":\"system\",\"content\":\"").append(system).append("\"},")
            append("{\"role\":\"user\",\"content\":\"").append(user).append("\"}")
            append("]")
            append("}")
        }
    }

    /**
     * Extract the first choice's `content` from an OpenAI-style response.
     * We don't parse the whole envelope — we just look for the substring
     * between `"content":"` and the next `"}`. Robust enough for any
     * OpenAI-compatible provider, and avoids a JSON dependency.
     */
    private fun extractContentField(body: String): String? {
        val key = "\"content\":\""
        val start = body.indexOf(key).takeIf { it >= 0 } ?: return null
        val valueStart = start + key.length
        val sb = StringBuilder()
        var braceDepth = 0
        var i = valueStart
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) {
                // Decode the escape sequence so the inner parser sees a
                // normal JSON string. Only the standard JSON escapes are
                // recognized; everything else is passed through as-is.
                when (body[i + 1]) {
                    '"', '\\', '/' -> { sb.append(body[i + 1]); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append(''); i += 2 }
                    'u' -> {
                        if (i + 5 < body.length) {
                            val hex = body.substring(i + 2, i + 6)
                            try { sb.append(hex.toInt(16).toChar()) } catch (_: Throwable) { sb.append(body[i + 1]) }
                            i += 6
                        } else { sb.append(body[i]); i++ }
                    }
                    else -> { sb.append(body[i]); i++ }
                }
                continue
            }
            if (c == '"') {
                return sb.toString()
            }
            if (c == '{') braceDepth++
            if (c == '}') braceDepth--
            sb.append(c); i++
        }
        return sb.toString()
    }

    private fun backoffMillis(attempt: Int): Long =
        (1000L shl (attempt - 1).coerceAtLeast(0).coerceAtMost(3))

    companion object {
        const val MAX_ATTEMPTS = 3

        private fun trimTrailingSlash(s: String): String =
            if (s.endsWith("/")) s.dropLast(1) else s

        private fun escapeJson(s: String): String = buildString(s.length) {
            for (c in s) when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
    }
}