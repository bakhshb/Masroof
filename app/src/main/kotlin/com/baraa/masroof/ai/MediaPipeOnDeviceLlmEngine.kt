package com.baraa.masroof.ai

/**
 * Placeholder for a future on-device LLM backend.
 *
 * MediaPipe GenAI was removed from the runtime: native inference was aborting
 * the process on several devices (Android "you have a bug"). Link assist now
 * uses SMS-text heuristics only via [OnDeviceLinkAssistProvider].
 */
class MediaPipeOnDeviceLlmEngine(
    private val modelPathProvider: () -> String = { "" },
) : OnDeviceLlmEngine {

    override fun isModelAvailable(): Boolean = OnDeviceModelStore.isPresent(modelPathProvider())

    override suspend fun generate(prompt: String): String {
        error("تشغيل النموذج المحلي معطّل مؤقتًا — اقتراح الربط يعتمد على نص الرسالة فقط")
    }

    override fun close() = Unit
}
