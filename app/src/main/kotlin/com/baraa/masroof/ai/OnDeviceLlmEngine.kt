package com.baraa.masroof.ai

/**
 * Thin backend for on-device text generation. Implementations may use
 * MediaPipe LLM Inference, LiteRT-LM, or a test double.
 */
interface OnDeviceLlmEngine {
    /** True when the model file is present and the engine can run. */
    fun isModelAvailable(): Boolean

    /**
     * Generate a completion for [prompt]. Must not log the prompt or result.
     * Throws on hard failures; callers map to [FailureReason].
     */
    suspend fun generate(prompt: String): String

    fun close() {}
}
