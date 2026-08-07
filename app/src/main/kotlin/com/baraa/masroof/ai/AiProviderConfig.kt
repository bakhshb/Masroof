package com.baraa.masroof.ai

/**
 * Settings for the AI categorizer. Constructed in the settings UI / from
 * storage. The API key is held only in memory here — it is never written
 * to disk in plain text. Disk persistence goes through [AiSettingsStore]
 * which uses Keystore-backed encrypted storage.
 *
 * Default values:
 *  - [enabled] = false
 *  - [shareExactAmount] = false
 *  - [minimumConfidence] = 80
 *  - [requireHttps] = true
 *  - [timeoutMillis] = 15_000
 */
data class AiProviderConfig(
    val enabled: Boolean = false,
    val deploymentMode: AiDeploymentMode = AiDeploymentMode.REMOTE,
    val providerLabel: String = "OpenAI-compatible",
    val baseUrl: String = "https://api.openai.com",
    val modelName: String = "gpt-4o-mini",
    /** Absolute path to an on-device `.task` / `.litertlm` model file. */
    val onDeviceModelPath: String = "",
    val apiKey: String = "",
    val shareExactAmount: Boolean = false,
    val minimumConfidence: Int = 80,
    val requireHttps: Boolean = true,
    val timeoutMillis: Long = 15_000L,
) {
    val isRemoteReady: Boolean
        get() = enabled &&
            deploymentMode == AiDeploymentMode.REMOTE &&
            baseUrl.isNotBlank() &&
            modelName.isNotBlank() &&
            apiKey.isNotBlank()

    val isOnDeviceReady: Boolean
        get() = enabled &&
            deploymentMode == AiDeploymentMode.ON_DEVICE &&
            onDeviceModelPath.isNotBlank() &&
            OnDeviceModelStore.isPresent(onDeviceModelPath)

    /** True when the selected deployment can be constructed. */
    val isReady: Boolean
        get() = when (deploymentMode) {
            AiDeploymentMode.REMOTE -> isRemoteReady
            AiDeploymentMode.ON_DEVICE -> isOnDeviceReady
        }
}