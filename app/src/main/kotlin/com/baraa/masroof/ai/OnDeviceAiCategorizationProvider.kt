package com.baraa.masroof.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

/**
 * On-device categorizer. Uses the same sanitized [AiCategorizationRequest] /
 * [AiPromptBuilder] / [AiResponseValidator] path as the remote provider —
 * never sends raw SMS. Requires a local model file via [OnDeviceLlmEngine].
 */
class OnDeviceAiCategorizationProvider(
    private val config: AiProviderConfig,
    private val engine: OnDeviceLlmEngine,
) : AiCategorizationProvider {

    override val providerName: String = config.providerLabel.ifBlank { "On-device" }

    init {
        require(config.enabled) { "OnDeviceAiCategorizationProvider requires enabled=true" }
        require(config.deploymentMode == AiDeploymentMode.ON_DEVICE) {
            "OnDeviceAiCategorizationProvider requires ON_DEVICE mode"
        }
    }

    override suspend fun categorize(request: AiCategorizationRequest): AiCategorizationOutcome {
        val start = System.currentTimeMillis()
        if (!config.enabled) {
            return failed(FailureReason.DISABLED, start, responseValid = false)
        }
        if (!engine.isModelAvailable()) {
            return failed(FailureReason.MODEL_NOT_READY, start, responseValid = false)
        }
        return try {
            val system = AiPromptBuilder.systemPrompt(request.language)
            val user = AiPromptBuilder.userPrompt(request)
            val prompt = buildString {
                appendLine(system)
                appendLine()
                appendLine(user)
                appendLine()
                appendLine("Respond with JSON only, matching the schema:")
                appendLine("""{"category_id":0,"category_name":"","normalized_merchant_name":"","confidence":0,"explanation":""}""")
            }
            val raw = withTimeout(config.timeoutMillis) { engine.generate(prompt) }
            val json = extractJsonObject(raw)
                ?: return failed(FailureReason.MALFORMED, start, responseValid = false)
            val result = AiResponseValidator.validateDirect(
                rawJson = json,
                request = request,
                providerName = providerName,
                modelName = config.modelName.ifBlank { fileNameHint(config.onDeviceModelPath) },
            ) ?: return failed(FailureReason.MALFORMED, start, responseValid = false)
            AiCategorizationOutcome.Success(result)
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            failed(FailureReason.TIMEOUT, start, responseValid = false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed(FailureReason.UNKNOWN, start, responseValid = false)
        }
    }

    private fun failed(
        reason: FailureReason,
        start: Long,
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
            httpStatusGroup = 0,
            cacheHit = false,
            responseValid = responseValid,
        ),
    )

    companion object {
        /** Pull the first JSON object out of free-form model text. */
        fun extractJsonObject(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
            val fenced = runCatching {
                Regex(
                    pattern = "```(?:json)?\\s*(\\{.*?})\\s*```",
                    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                ).find(trimmed)?.groupValues?.getOrNull(1)
            }.getOrNull()
            if (fenced != null) return fenced.trim()
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
            return null
        }

        private fun fileNameHint(path: String): String =
            path.substringAfterLast('/').ifBlank { "on-device-model" }
    }
}
