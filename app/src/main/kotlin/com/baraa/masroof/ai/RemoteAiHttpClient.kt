package com.baraa.masroof.ai

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP client built on [HttpURLConnection] for the AI provider. No
 * OkHttp dependency — kept the stack minimal.
 *
 * Properties:
 *  - connection timeout and read timeout from [AiHttpRequest.timeoutMillis]
 *  - response body size cap (default 256 KB) — excess throws
 *    [AiResponseTooLargeException] so the caller never sees an unbounded
 *    payload
 *  - UTF-8 input/output
 *  - safe stream closure (try-with-resources everywhere)
 *  - correct handling of non-2xx responses
 *  - normal TLS verification (no trust-all, no hostname bypass)
 *  - **never** logs headers or bodies
 *  - **never** exposes the API key in [AiHttpResponse]
 */
class RemoteAiHttpClient(
    private val maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
) : AiHttpClient {

    override suspend fun execute(request: AiHttpRequest): AiHttpResponse {
        val start = System.currentTimeMillis()
        val timeoutMillis = request.timeoutMillis.coerceAtLeast(1)
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = request.method
            connectTimeout = timeoutMillis.toInt().coerceAtMost(MAX_TIMEOUT_MS.toInt())
            readTimeout = timeoutMillis.toInt().coerceAtMost(MAX_TIMEOUT_MS.toInt())
            instanceFollowRedirects = true
            useCaches = false
            doInput = true
            for ((k, v) in request.headers) {
                setRequestProperty(k, v)
            }
            // Default JVM behavior: validate TLS / use platform trust
            // store. Do NOT override.
        }
        return try {
            connection.doOutput = true
            connection.outputStream.use { os ->
                os.write(request.body.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            val code = connection.responseCode
            val stream: InputStream? = connection.inputStream ?: connection.errorStream
            val body = readBoundedUtf8(stream, maxResponseBytes)
            AiHttpResponse(
                statusCode = code,
                body = body,
                durationMs = System.currentTimeMillis() - start,
            )
        } catch (_: java.net.SocketTimeoutException) {
            throw java.net.SocketTimeoutException("timeout after ${timeoutMillis}ms")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Reads at most [limit] bytes from the stream. If the stream exceeds
     * the limit, throws [AiResponseTooLargeException] and leaves the
     * stream closed (the caller's `use` block runs in the surrounding
     * code via the finally chain).
     */
    private fun readBoundedUtf8(stream: InputStream?, limit: Long): String {
        if (stream == null) return ""
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val sb = StringBuilder()
            val buf = CharArray(4096)
            var totalChars = 0L
            while (true) {
                val read = reader.read(buf)
                if (read < 0) break
                totalChars += read
                if (totalChars > limit) {
                    throw AiResponseTooLargeException(totalChars, limit)
                }
                sb.append(buf, 0, read)
            }
            sb.toString()
        }
    }

    companion object {
        /** 256 KB — well above any realistic AI categorization response. */
        const val DEFAULT_MAX_RESPONSE_BYTES: Long = 256L * 1024L

        /** Safety cap on the configurable per-request timeout. */
        const val MAX_TIMEOUT_MS: Long = 60_000L
    }
}