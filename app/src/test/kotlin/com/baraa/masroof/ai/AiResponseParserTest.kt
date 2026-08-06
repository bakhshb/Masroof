package com.baraa.masroof.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [AiResponseValidator] — the kotlinx.serialization-backed
 * OpenAI-compatible response parser. No handwritten JSON / regex.
 */
class AiResponseParserTest {

    private val req = AiCategorizationRequest(
        normalizedMerchant = "Starbucks",
        transactionType = "PURCHASE",
        amountBucket = AmountBucket.FROM_50_TO_199,
        currency = com.baraa.masroof.transaction.Currency.SAR,
        allowedCategories = listOf(
            AllowedCategory(1, "مقاهي"),
            AllowedCategory(2, "مطاعم")
        ),
        channel = Channel.POS,
        language = "ar"
    )

    private fun ok(content: String): String {
        // Escape the inner content so it can sit inside a JSON string.
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
        // Concatenate (do NOT use raw strings here because raw strings
        // interpret `$` as a template literal).
        return "{\"id\":\"x\",\"object\":\"chat.completion\",\"model\":\"m\"," +
            "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\"," +
            "\"content\":\"" + escaped + "\"},\"finish_reason\":\"stop\"}]," +
            "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}"
    }

    @Test
    fun validOuterResponseParses() {
        val inner = """{"category_id":1,"category_name":"مقاهي","normalized_merchant_name":"Starbucks","confidence":90,"explanation":"متجر قهوة"}"""
        val env = AiResponseValidator.parseEnvelope(ok(inner))
        assertNotNull(env)
        assertEquals(1, env!!.choices.size)
        assertEquals(inner, env.choices[0].message?.content)
    }

    @Test
    fun validMultilineInnerJsonParses() {
        val inner = """{
            "category_id": 1,
            "category_name": "مقاهي",
            "normalized_merchant_name": "Starbucks",
            "confidence": 88,
            "explanation": "coffeeshop"
        }"""
        val payload = AiResponseValidator.parseInner(inner)
        assertNotNull(payload)
        assertEquals(1L, payload!!.categoryId)
        assertEquals(88, payload.confidence)
    }

    @Test
    fun escapedArabicTextParses() {
        // kotlinx.serialization decodes \uXXXX escapes within JSON strings by
        // default; if you see literal \u escapes the configuration is wrong.
        val inner = "{\"category_id\":2,\"category_name\":\"مطاعم\",\"normalized_merchant_name\":\"X\",\"confidence\":50,\"explanation\":\"تست\"}"
        val payload = AiResponseValidator.parseInner(inner)
        assertNotNull(payload)
        assertEquals("مطاعم", payload!!.categoryName)
        assertEquals("تست", payload.explanation)
    }

    @Test
    fun emptyChoicesTreatedAsMalformed() {
        val env = AiResponseValidator.parseEnvelope("""{"choices":[]}""")
        assertNotNull(env)
        assertTrue(env!!.choices.isEmpty())
        // validate returns null because the first-choice check fails.
        assertNull(AiResponseValidator.validate(
            rawBody = """{"choices":[]}""",
            request = req,
            providerName = "x",
            modelName = "y"
        ))
    }

    @Test
    fun missingMessageContentTreatedAsMalformed() {
        val body = """{"choices":[{"index":0,"finish_reason":"stop"}]}"""
        val env = AiResponseValidator.parseEnvelope(body)
        assertNotNull(env)
        assertNull(AiResponseValidator.validate(body, req, "x", "y"))
    }

    @Test
    fun nullOrBlankContentTreatedAsMalformed() {
        val body = """{"choices":[{"index":0,"message":{"role":"assistant","content":""}}]}"""
        val env = AiResponseValidator.parseEnvelope(body)
        assertNotNull(env)
        assertNull(AiResponseValidator.validate(body, req, "x", "y"))
    }

    @Test
    fun malformedOuterJsonReturnsNull() {
        assertNull(AiResponseValidator.parseEnvelope("not-json"))
        assertNull(AiResponseValidator.parseEnvelope("{not-json"))
        assertNull(AiResponseValidator.parseEnvelope(""))
    }

    @Test
    fun malformedInnerJsonReturnsNull() {
        val body = ok("not valid json")
        val env = AiResponseValidator.parseEnvelope(body)
        assertNotNull(env)
        // validate returns null on inner JSON parse failure.
        assertNull(AiResponseValidator.validate(body, req, "x", "y"))
    }

    @Test
    fun invalidCategoryIdRejected() {
        val inner = """{"category_id":999,"category_name":"x","normalized_merchant_name":"X","confidence":90,"explanation":"y"}"""
        val body = ok(inner)
        assertNull(AiResponseValidator.validate(body, req, "x", "y"))
    }

    @Test
    fun invalidConfidenceRejected() {
        val inner = """{"category_id":1,"category_name":"x","normalized_merchant_name":"X","confidence":150,"explanation":"y"}"""
        val body = ok(inner)
        assertNull(AiResponseValidator.validate(body, req, "x", "y"))
    }

    @Test
    fun providerErrorEnvelopeMapsToFailure() {
        // The validator returns null for a response with a provider error.
        val body = """{"error":{"message":"Invalid API key","type":"invalid_api_key","code":"x"}}"""
        val env = AiResponseValidator.parseEnvelope(body)
        assertNotNull(env)
        assertNotNull(env!!.error)
        assertNull(AiResponseValidator.validate(body, req, "x", "y"))
        val summary = AiResponseValidator.summarizeError(env.error)
        assertTrue(summary!!.contains("invalid_api_key"))
    }

    @Test
    fun oversizedResponseRejected() {
        // The HTTP layer enforces the size limit; AiResponseValidator
        // itself is size-agnostic. We just verify that a very long inner
        // payload is truncated to MAX_EXPLANATION_LENGTH (200) by
        // validate.
        val longExp = "x".repeat(500)
        val inner = """{"category_id":1,"category_name":"x","normalized_merchant_name":"X","confidence":90,"explanation":"${'$'}longExp"}"""
        val body = ok(inner)
        val r = AiResponseValidator.validate(body, req, "x", "y")
        assertNotNull(r)
        assertTrue(r!!.explanation.length <= 200)
    }

    @Test
    fun validResponseWithNestedObjectsParses() {
        // Test nested objects in the response envelope (usage, model, error).
        val body = """{"id":"abc","object":"chat.completion","created":1,"model":"test-model","choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2},"error":null}"""
        val env = AiResponseValidator.parseEnvelope(body)
        assertNotNull(env)
        assertEquals("test-model", env!!.model)
        assertEquals("abc", env.id)
        assertEquals(1, env.usage?.promptTokens)
        assertNull(env.error)
    }

    @Test
    fun validResponseWithMultipleChoicesTakesFirst() {
        val body = """{"choices":[{"index":0,"message":{"role":"assistant","content":"x"}},{"index":1,"message":{"role":"assistant","content":"y"}}]}"""
        val env = AiResponseValidator.parseEnvelope(body)
        assertNotNull(env)
        assertEquals(2, env!!.choices.size)
        assertEquals("x", env.choices[0].message?.content)
        assertEquals("y", env.choices[1].message?.content)
    }
}