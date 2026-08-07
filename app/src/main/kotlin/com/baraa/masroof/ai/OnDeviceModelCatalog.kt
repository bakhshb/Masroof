package com.baraa.masroof.ai

/**
 * Curated MediaPipe / LiteRT models compatible with [MediaPipeOnDeviceLlmEngine].
 * Files are not bundled; the user downloads from Hugging Face (license gated)
 * then copies into [OnDeviceModelStore.modelsDirectory].
 *
 * Source: https://huggingface.co/litert-community/Gemma3-1B-IT
 */
data class OnDeviceModelOption(
    val id: String,
    /** Arabic display name in settings. */
    val titleAr: String,
    /** Short Arabic description (size / device needs). */
    val summaryAr: String,
    /** Expected file name under app `ai_models/`. */
    val fileName: String,
    /** Hugging Face model page (accept license, then download the file). */
    val pageUrl: String,
    /** Direct file URL (still requires HF login + license accept). */
    val downloadUrl: String,
    val recommended: Boolean = false,
)

object OnDeviceModelCatalog {
    private const val PAGE = "https://huggingface.co/litert-community/Gemma3-1B-IT"
    private const val RESOLVE = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main"

    val options: List<OnDeviceModelOption> = listOf(
        OnDeviceModelOption(
            id = "gemma3-1b-int4-task",
            titleAr = "Gemma 3 — 1B int4 (موصى به)",
            summaryAr = "الأصغر والأنسب لربط الحسابات (~530 ميغابايت). يحتاج جهازًا حديثًا (مثل Pixel 8 / S23).",
            fileName = "gemma3-1b-it-int4.task",
            pageUrl = PAGE,
            downloadUrl = "$RESOLVE/gemma3-1b-it-int4.task?download=true",
            recommended = true,
        ),
        OnDeviceModelOption(
            id = "gemma3-1b-int4-litertlm",
            titleAr = "Gemma 3 — 1B int4 (.litertlm)",
            summaryAr = "نفس النموذج بصيغة LiteRT (~560 ميغابايت). استخدمه إن كان ملف .task لا يعمل على جهازك.",
            fileName = "gemma3-1b-it-int4.litertlm",
            pageUrl = PAGE,
            downloadUrl = "$RESOLVE/gemma3-1b-it-int4.litertlm?download=true",
        ),
    )

    fun findByFileName(fileName: String): OnDeviceModelOption? =
        options.firstOrNull { it.fileName.equals(fileName, ignoreCase = true) }
}
