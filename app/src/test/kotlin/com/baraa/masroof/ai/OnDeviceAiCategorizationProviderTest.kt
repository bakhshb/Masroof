package com.baraa.masroof.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceAiCategorizationProviderTest {

    @Test
    fun extractJsonObject_fromFencedMarkdown() {
        val raw = """
            Sure.
            ```json
            {"category_id":3,"category_name":"طعام","normalized_merchant_name":"cafe","confidence":88,"explanation":"ok"}
            ```
        """.trimIndent()
        val json = OnDeviceAiCategorizationProvider.extractJsonObject(raw)
        assertNotNull(json)
        assertTrue(json!!.contains("\"category_id\":3"))
    }

    @Test
    fun onDeviceProvider_returnsSuccessFromEngineJson() = runBlocking {
        val engine = object : OnDeviceLlmEngine {
            override fun isModelAvailable(): Boolean = true
            override suspend fun generate(prompt: String): String =
                """{"category_id":7,"category_name":"مواصلات","normalized_merchant_name":"ride","confidence":91,"explanation":"local"}"""
        }
        val provider = OnDeviceAiCategorizationProvider(
            config = AiProviderConfig(
                enabled = true,
                deploymentMode = AiDeploymentMode.ON_DEVICE,
                providerLabel = "On-device",
                onDeviceModelPath = "/tmp/fake.task",
                modelName = "gemma-test",
            ),
            engine = engine,
        )
        val outcome = provider.categorize(
            AiCategorizationRequest(
                normalizedMerchant = "ride",
                transactionType = "PURCHASE",
                amountBucket = AmountBucket.UNDER_50,
                currency = com.baraa.masroof.transaction.Currency.SAR,
                allowedCategories = listOf(AllowedCategory(7, "مواصلات")),
                channel = Channel.POS,
                language = "ar",
            ),
        )
        val success = outcome as AiCategorizationOutcome.Success
        assertEquals(7L, success.result.categoryId)
        assertEquals(91, success.result.confidence)
    }

    @Test
    fun onDeviceProvider_modelMissing() = runBlocking {
        val engine = object : OnDeviceLlmEngine {
            override fun isModelAvailable(): Boolean = false
            override suspend fun generate(prompt: String): String = error("should not run")
        }
        val provider = OnDeviceAiCategorizationProvider(
            config = AiProviderConfig(
                enabled = true,
                deploymentMode = AiDeploymentMode.ON_DEVICE,
                onDeviceModelPath = "/missing.task",
            ),
            engine = engine,
        )
        val outcome = provider.categorize(
            AiCategorizationRequest(
                normalizedMerchant = "x",
                transactionType = "PURCHASE",
                amountBucket = AmountBucket.UNDER_50,
                currency = com.baraa.masroof.transaction.Currency.SAR,
                allowedCategories = emptyList(),
                channel = Channel.UNKNOWN,
                language = "ar",
            ),
        )
        val failed = outcome as AiCategorizationOutcome.Failed
        assertEquals(FailureReason.MODEL_NOT_READY, failed.reason)
    }

    @Test
    fun validatorSkipsApiKeyForOnDevice() {
        val cfg = AiProviderConfig(
            enabled = true,
            deploymentMode = AiDeploymentMode.ON_DEVICE,
            onDeviceModelPath = "/data/model.task",
        )
        val errors = AiSettingsValidator.validate(cfg, hasApiKey = false)
        assertTrue(errors.none { it.errorKey == AiSettingsValidator.ErrorKey.MISSING_API_KEY })
    }
}
