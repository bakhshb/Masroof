package com.baraa.masroof.ai

/**
 * Pluggable HTTP layer used by [OpenAiCompatibleProvider]. The interface
 * is intentionally narrow (one method) so JVM unit tests can wire in a
 * [FakeAiHttpClient] without pulling in OkHttp or any Android-only APIs.
 *
 * Production Android code supplies an OkHttp-backed implementation; JVM
 * tests supply a fake. **All implementations MUST**:
 *  - validate TLS by default (no disabled verification, no user-trusted
 *    certificate bypasses)
 *  - respect [AiHttpRequest.timeoutMillis]
 *  - cancel in-flight requests on coroutine cancellation
 *  - never log request / response bodies
 *  - never include the API key in any [AiHttpResponse]
 */
interface AiHttpClient {
    suspend fun execute(request: AiHttpRequest): AiHttpResponse
}

data class AiHttpRequest(
    val url: String,
    val method: String = "POST",
    val headers: Map<String, String> = emptyMap(),
    val body: String,
    val timeoutMillis: Long = 15_000L,
)

data class AiHttpResponse(
    val statusCode: Int,
    val body: String,
    val durationMs: Long,
) {
    val isSuccess: Boolean get() = statusCode in 200..299
    val statusGroup: Int get() = when (statusCode) {
        in 100..199 -> 1
        in 200..299 -> 2
        in 300..399 -> 3
        in 400..499 -> 4
        in 500..599 -> 5
        else -> 0
    }
}

/** JVM-friendly fake. Records every call and returns pre-programmed responses. */
class FakeAiHttpClient : AiHttpClient {
    val requests: MutableList<AiHttpRequest> = mutableListOf()
    var nextResponse: AiHttpResponse = AiHttpResponse(200, "{}", 0L)
    var nextDelayMs: Long = 0L
    var networkException: Throwable? = null
    var cancelled: Boolean = false

    override suspend fun execute(request: AiHttpRequest): AiHttpResponse {
        requests.add(request)
        if (networkException != null) throw networkException!!
        if (nextDelayMs > 0) kotlinx.coroutines.delay(nextDelayMs)
        return nextResponse
    }
}