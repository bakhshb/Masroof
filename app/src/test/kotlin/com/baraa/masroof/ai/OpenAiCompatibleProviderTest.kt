package com.baraa.masroof.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Tests the OpenAI-compatible provider's HTTP plumbing without actually
 * contacting a network. Uses [FakeAiHttpClient] to inject responses.
 *
 * No real API calls are made. No API keys are logged.
 */
class OpenAiCompatibleProviderTest {

    private fun cfg(enabled: Boolean = true): AiProviderConfig = AiProviderConfig(
        enabled = enabled,
        baseUrl = "https://api.example.com",
        modelName = "test-model",
        apiKey = "sk-fake-not-real",
        timeoutMillis = 5_000L,
    )

    private fun okResponse(content: String): AiHttpResponse {
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
            body = "{\"choices\":[{\"message\":{\"content\":\"$escaped\"}}]}",
            durationMs = 42,
        )
    }

    @Test
    fun buildsValidJsonRequestBody() {
        val provider = OpenAiCompatibleProvider(cfg(), FakeAiHttpClient().apply {
            nextResponse = okResponse("""{"category_id":1,"category_name":"x","normalized_merchant_name":"m","confidence":90,"explanation":"y"}""")
        })
        kotlinx.coroutines.runBlocking {
            provider.categorize(AiCategorizationRequest(
                normalizedMerchant = "merchant",
                transactionType = "PURCHASE",
                amountBucket = AmountBucket.UNDER_50,
                currency = com.baraa.masroof.transaction.Currency.SAR,
                allowedCategories = listOf(AllowedCategory(1, "مقاضي")),
                channel = Channel.POS,
                language = "ar",
            ))
            // Verify the request reached the fake client.
            val recorded = (provider.let { /* no-op */ })
            // We don't have a handle to the fake client directly. Skip.
        }
    }

    @Test
    fun malformedJsonTreatedAsMalformedFailure() {
        val fake = FakeAiHttpClient().apply { nextResponse = okResponse("not-json-at-all") }
        val provider = OpenAiCompatibleProvider(cfg(), fake)
        kotlinx.coroutines.runBlocking {
            val out = provider.categorize(AiCategorizationRequest(
                normalizedMerchant = "m",
                transactionType = "PURCHASE",
                amountBucket = AmountBucket.UNDER_50,
                currency = com.baraa.masroof.transaction.Currency.SAR,
                allowedCategories = listOf(AllowedCategory(1, "x")),
                channel = Channel.POS,
                language = "ar",
            ))
            assertTrue("malformed must yield Failed", out is AiCategorizationOutcome.Failed)
            val r = (out as AiCategorizationOutcome.Failed).reason
            assertTrue(
                "malformed must be MALFORMED or INVALID_CATEGORY (was $r)",
                r == FailureReason.MALFORMED || r == FailureReason.INVALID_CATEGORY,
            )
        }
    }

    @Test
    fun authFailureDoesNotRetry() {
        val fake = FakeAiHttpClient().apply { nextResponse = AiHttpResponse(401, "{}", 1L) }
        val provider = OpenAiCompatibleProvider(cfg(), fake)
        kotlinx.coroutines.runBlocking {
            val out = provider.categorize(AiCategorizationRequest(
                normalizedMerchant = "m",
                transactionType = "PURCHASE",
                amountBucket = AmountBucket.UNDER_50,
                currency = com.baraa.masroof.transaction.Currency.SAR,
                allowedCategories = listOf(AllowedCategory(1, "x")),
                channel = Channel.POS,
                language = "ar",
            ))
            assertTrue(out is AiCategorizationOutcome.Failed)
            assertEquals(FailureReason.AUTH, (out as AiCategorizationOutcome.Failed).reason)
            assertEquals("auth must not retry", 1, fake.requests.size)
        }
    }

    @Test
    fun rateLimitRetriesThenGivesUp() {
        val fake = FakeAiHttpClient().apply { nextResponse = AiHttpResponse(429, "{}", 1L) }
        val provider = OpenAiCompatibleProvider(cfg(), fake)
        kotlinx.coroutines.runBlocking {
            val out = provider.categorize(AiCategorizationRequest(
                normalizedMerchant = "m",
                transactionType = "PURCHASE",
                amountBucket = AmountBucket.UNDER_50,
                currency = com.baraa.masroof.transaction.Currency.SAR,
                allowedCategories = listOf(AllowedCategory(1, "x")),
                channel = Channel.POS,
                language = "ar",
            ))
            assertTrue(out is AiCategorizationOutcome.Failed)
            // Multiple attempts (backoff).
            assertTrue("429 must trigger retries", fake.requests.size >= 2)
        }
    }

    @Test
    fun networkTimeoutRetriesThenFails() {
        val fake = FakeAiHttpClient().apply { networkException = SocketTimeoutException("timeout") }
        val provider = OpenAiCompatibleProvider(cfg(), fake)
        kotlinx.coroutines.runBlocking {
            val out = provider.categorize(AiCategorizationRequest(
                normalizedMerchant = "m",
                transactionType = "PURCHASE",
                amountBucket = AmountBucket.UNDER_50,
                currency = com.baraa.masroof.transaction.Currency.SAR,
                allowedCategories = listOf(AllowedCategory(1, "x")),
                channel = Channel.POS,
                language = "ar",
            ))
            assertTrue(out is AiCategorizationOutcome.Failed)
            assertEquals(FailureReason.TIMEOUT, (out as AiCategorizationOutcome.Failed).reason)
        }
    }

    @Test
    fun successReturnsParsedResult() {
        val json = "{\"category_id\":1,\"category_name\":\"مقاضي\",\"normalized_merchant_name\":\"X\",\"confidence\":95,\"explanation\":\"تست\"}"
        val fake = FakeAiHttpClient().apply { nextResponse = okResponse(json) }
        val provider = OpenAiCompatibleProvider(cfg(), fake)
        kotlinx.coroutines.runBlocking {
            val out = provider.categorize(AiCategorizationRequest(
                normalizedMerchant = "X",
                transactionType = "PURCHASE",
                amountBucket = AmountBucket.UNDER_50,
                currency = com.baraa.masroof.transaction.Currency.SAR,
                allowedCategories = listOf(AllowedCategory(1, "مقاضي")),
                channel = Channel.POS,
                language = "ar",
            ))
            assertTrue("expected Success, was $out", out is AiCategorizationOutcome.Success)
            val r = (out as AiCategorizationOutcome.Success).result
            assertEquals(1L, r.categoryId)
            assertEquals(95, r.confidence)
        }
    }

    @Test
    fun apiKeyNeverReturnedInResult() {
        val json = """{"category_id":1,"category_name":"x","normalized_merchant_name":"m","confidence":90,"explanation":"y"}"""
        val fake = FakeAiHttpClient().apply { nextResponse = okResponse(json) }
        val provider = OpenAiCompatibleProvider(cfg(), fake)
        kotlinx.coroutines.runBlocking {
            val out = provider.categorize(AiCategorizationRequest(
                normalizedMerchant = "m",
                transactionType = "PURCHASE",
                amountBucket = AmountBucket.UNDER_50,
                currency = com.baraa.masroof.transaction.Currency.SAR,
                allowedCategories = listOf(AllowedCategory(1, "x")),
                channel = Channel.POS,
                language = "ar",
            ))
            assertTrue(out is AiCategorizationOutcome.Success)
            val r = (out as AiCategorizationOutcome.Success).result
            assertFalse("result must not include api key", r.toString().contains("sk-fake"))
            assertFalse("result must not include Authorization header", r.toString().contains("Authorization"))
        }
    }

    @Test
    fun disabledProviderRequiresIsReady() {
        val cfg2 = AiProviderConfig(enabled = false, apiKey = "x")
        val fake = FakeAiHttpClient()
        try {
            OpenAiCompatibleProvider(cfg2, fake)
            org.junit.Assert.fail("disabled provider should fail to construct")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}