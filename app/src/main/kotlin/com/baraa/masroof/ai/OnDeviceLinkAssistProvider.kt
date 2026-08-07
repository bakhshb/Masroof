package com.baraa.masroof.ai

/**
 * On-device link assist from the local SMS body (last-4 + keywords).
 *
 * Does not load or run a native LLM — MediaPipe was crashing devices.
 * User still confirms before posting.
 */
class OnDeviceLinkAssistProvider(
    private val config: AiProviderConfig,
    @Suppress("unused") private val engine: OnDeviceLlmEngine = MediaPipeOnDeviceLlmEngine(),
) {
    init {
        require(config.enabled) { "OnDeviceLinkAssistProvider requires enabled=true" }
        require(config.deploymentMode == AiDeploymentMode.ON_DEVICE) {
            "OnDeviceLinkAssistProvider requires ON_DEVICE mode"
        }
    }

    /** Ready whenever on-device mode is enabled — no model file required. */
    fun isReady(): Boolean =
        config.enabled && config.deploymentMode == AiDeploymentMode.ON_DEVICE

    suspend fun suggest(request: LinkAssistRequest): LinkAssistOutcome {
        if (!config.enabled) {
            return LinkAssistOutcome.Failed(
                FailureReason.DISABLED,
                "الذكاء المحلي غير مفعّل — فعّله من الإعدادات",
            )
        }
        if (request.accounts.isEmpty()) {
            return LinkAssistOutcome.Failed(
                FailureReason.MALFORMED,
                "لا توجد حسابات مملوكة للاقتراح — أضف حسابًا أولًا",
            )
        }
        if (request.smsBody.isBlank()) {
            return LinkAssistOutcome.Failed(
                FailureReason.MALFORMED,
                "نص الرسالة غير متوفر محلياً — أعد استيراد الرسائل بعد تحديث التطبيق",
            )
        }

        val fromSms = OnDeviceLinkAssist.suggestFromSmsBody(request)
        return if (fromSms != null) {
            LinkAssistOutcome.Success(fromSms)
        } else {
            LinkAssistOutcome.Failed(
                FailureReason.MALFORMED,
                "لا يوجد تطابق واضح في نص الرسالة. اختر يدويًا.",
            )
        }
    }
}
