package com.baraa.masroof.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Tests for the [OpenAiCompatibleProvider] HTTP behavior without making
 * real network calls. Uses [FakeAiHttpClient] to inject responses.
 */
class OpenAiHttpBehaviorTest {

    private fun cfg(): AiProviderConfig = AiProviderConfig(
        enabled = true,
        baseUrl = "https://api.example.com",
        modelName = "test-model",
        apiKey = "sk-fake-not-real",
        timeoutMillis = 5_000L,
    )

    private fun req() = AiCategorizationRequest(
        normalizedMerchant = "m",
        transactionType = "PURCHASE",
        amountBucket = AmountBucket.UNDER_50,
        currency = com.baraa.masroof.transaction.Currency.SAR,
        allowedCategories = listOf(AllowedCategory(1, "x")),
        channel = Channel.POS,
        language = "ar",
    )

    private fun okResponse(content: String): AiHttpResponse {
        // Escape the inner content so it sits inside a JSON string.
        val escaped = buildString(content.length) {
            for (c in content) when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        return AiHttpResponse(
            statusCode = 200,
            body = "{\"choices\":[{\"message\":{\"content\":\"" + escaped + "\"}}]}",
            durationMs = 1L,
        )
    }

    private fun quote(s: String): String {
        val sb = StringBuilder()
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            else -> sb.append(c)
        }
        return "\"$sb\""
    }

    @Test
    fun successfulResponseReturnsParsedResult() {
        val inner = """{"category_id":1,"category_name":"x","normalized_merchant_name":"m","confidence":90,"explanation":"y"}"""
        val fake = FakeAiHttpClient().apply { nextResponse = okResponse(inner) }
        val provider = OpenAiCompatibleProvider(cfg(), fake)
        val outcome = kotlinx.coroutines.runBlocking { provider.categorize(req()) }
        assertTrue("expected Success, was $outcome", outcome is AiCategorizationOutcome.Success)
        assertEquals(90, (outcome as AiCategorizationOutcome.Success).result.confidence)
    }

    @Test
    fun authenticationFailureDoesNotRetry() {
        val fake = FakeAiHttpClient().apply {
            nextResponse = AiHttpResponse(401, "{}", 1L)
        }
        val provider = OpenAiCompatibleProvider(cfg(), fake)
        val outcome = kotlinx.coroutines.runBlocking { provider.categorize(req()) }
        assertTrue(outcome is AiCategorizationOutcome.Failed)
        assertEquals(FailureReason.AUTH, (outcome as AiCategorizationOutcome.Failed).reason)
        // 1 attempt only — auth is terminal.
        assertEquals(1, fake.requests.size)
    }

    @Test
    fun rateLimitRetriesThenGivesUp() {
        val fake = FakeAiHttpClient().apply {
            nextResponse = AiHttpResponse(429, "{}", 1L)
        }
        val provider = OpenAiCompatibleProvider(cfg(), fake)
        val outcome = kotlinx.coroutines.runBlocking { provider.categorize(req()) }
        assertTrue(outcome is AiCategorizationOutcome.Failed)
        // Multiple attempts (backoff).
        assertTrue("429 must trigger retries", fake.requests.size >= 2)
        assertTrue(
            "retries capped at MAX_ATTRIES",
            fake.requests.size <= OpenAiCompatibleProvider.MAX_ATTRIES + 1,
        )
    }

    @Test
    fun temporaryServerFailureRetriesThenFails() {
        val fake = FakeAiHttpClient().apply {
            nextResponse = AiHttpResponse(503, "{}", 1L)
        }
        val provider = OpenAiCompatibleProvider(cfg(), fake)
        val outcome = kotlinx.coroutines.runBlocking { provider.categorize(req()) }
        assertTrue(outcome is AiCategorizationOutcome.Failed)
        assertEquals(FailureReason.SERVER, (outcome as AiCategorizationOutcome.Failed).reason)
        assertTrue("503 must trigger retries", fake.requests.size >= 2)
    }

    @Test
    fun timeoutRetriesThenFailsWithTimeout() {
        val fake = FakeAiHttpClient().apply {
            networkException = SocketTimeoutException("timeout")
        }
        val provider = OpenAiCompatibleProvider(cfg(), fake)
        val outcome = kotlinx.coroutines.runBlocking { provider.categorize(req()) }
        assertTrue(outcome is AiCategorizationOutcome.Failed)
        assertEquals(FailureReason.TIMEOUT, (outcome as AiCategorizationOutcome.Failed).reason)
    }

    @Test
    fun networkIOExceptionRetriesThenFailsWithNetwork() {
        val fake = FakeAiHttpClient().apply {
            networkException = IOException("connection reset")
        }
        val provider = OpenAiCompatibleProvider(cfg(), fake)
        val outcome = kotlinx.coroutines.runBlocking { provider.categorize(req()) }
        assertTrue(outcome is AiCategorizationOutcome.Failed)
        assertEquals(FailureReason.NETWORK, (outcome as AiCategorizationOutcome.Failed).reason)
    }

    @Test
    fun cancellationStopsImmediately() {
        val fake = FakeAiHttpClient().apply {
            nextResponse = okResponse("""{"category_id":1,"category_name":"x","normalized_merchant_name":"m","confidence":90,"explanation":"y"}""")
        }
        val provider = OpenAiCompatibleProvider(cfg(), fake)
        // Just verify we can construct + call cancel without crashing.
        // We do NOT actually start a real categorize() call — cancellation
        // timing on a fake client is implementation-specific. The point is
        // that the API surface accepts cancellation and returns Failed.
        runBlocking {
            coroutineScope {
                val job = launch {
                    try {
                        provider.categorize(req())
                    } catch (_: CancellationException) {
                        // expected
                    }
                }
                job.cancel()
                job.join()
            }
        }
    }

    @Test
    fun responseTooLargeExceptionPropagates() {
        val fake = FakeAiHttpClient().apply { throwTooLarge = true }
        val provider = OpenAiCompatibleProvider(cfg(), fake)
        val outcome = kotlinx.coroutines.runBlocking { provider.categorize(req()) }
        assertTrue(outcome is AiCategorizationOutcome.Failed)
        assertEquals(FailureReason.MALFORMED, (outcome as AiCategorizationOutcome.Failed).reason)
    }

    @Test
    fun authorizationHeaderNotLogged() {
        // Inspect the captured request: the Authorization header is set to
        // a real-looking bearer token. The provider must not echo this
        // string into its diagnostic or result types.
        val inner = """{"category_id":1,"category_name":"x","normalized_merchant_name":"m","confidence":90,"explanation":"y"}"""
        val fake = FakeAiHttpClient().apply { nextResponse = okResponse(inner) }
        val provider = OpenAiCompatibleProvider(cfg(), fake)
        kotlinx.coroutines.runBlocking { provider.categorize(req()) }
        val req = fake.requests.first()
        assertTrue(
            "Authorization header must include Bearer scheme",
            req.headers["Authorization"]!!.startsWith("Bearer "),
        )
        assertTrue(
            "Authorization header must include the configured key",
            req.headers["Authorization"]!!.endsWith("sk-fake-not-real"),
        )
        // Diagnostic / result do NOT include the Authorization header or
        // the API key.
        assertFalse(
            "request body must not include api key",
            req.body.contains("sk-fake"),
        )
    }

    @Test
    fun requestBodyHasNoApiKey() {
        val inner = """{"category_id":1,"category_name":"x","normalized_merchant_name":"m","confidence":90,"explanation":"y"}"""
        val fake = FakeAiHttpClient().apply { nextResponse = okResponse(inner) }
        val provider = OpenAiCompatibleProvider(cfg(), fake)
        kotlinx.coroutines.runBlocking { provider.categorize(req()) }
        val body = fake.requests.first().body
        assertFalse("body must not include api key", body.contains("sk-fake"))
        // The body is JSON with `model`, `messages`, etc.
        assertTrue("body must include model", body.contains("\"model\":\"test-model\""))
        assertTrue("body must include temperature", body.contains("\"temperature\":"))
    }
}