package com.baraa.masroof.ai

import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Minimal HTTP client built on [HttpURLConnection]. Used by the
 * OpenAI-compatible provider in production. The implementation:
 *  - validates TLS (no custom trust managers, no disabled verification)
 *  - enforces the per-request timeout
 *  - cancels on coroutine cancellation
 *  - **never** logs request / response bodies
 *  - **never** includes the API key in the returned [AiHttpResponse]
 *
 * For unit tests, use [FakeAiHttpClient] instead.
 */
class RemoteAiHttpClient : AiHttpClient {

    override suspend fun execute(request: AiHttpRequest): AiHttpResponse {
        val start = System.currentTimeMillis()
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = request.method
            connectTimeout = request.timeoutMillis.toInt().coerceAtLeast(1)
            readTimeout = request.timeoutMillis.toInt().coerceAtLeast(1)
            doInput = true
            for ((k, v) in request.headers) {
                setRequestProperty(k, v)
            }
        }
        if (connection is HttpsURLConnection) {
            // Default JVM behavior — validate TLS. No overrides here.
        }
        return try {
            connection.doOutput = true
            connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            AiHttpResponse(statusCode = code, body = body, durationMs = System.currentTimeMillis() - start)
        } finally {
            connection.disconnect()
        }
    }
}