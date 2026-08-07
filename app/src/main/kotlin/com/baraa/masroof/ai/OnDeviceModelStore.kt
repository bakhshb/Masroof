package com.baraa.masroof.ai

import java.io.File

/**
 * Resolves and reports on-device model files. Models are **not** bundled in the
 * APK (too large); the user downloads a `.task` / `.litertlm` file and places
 * it under [modelsDirectory].
 */
object OnDeviceModelStore {
    const val MODELS_DIR_NAME: String = "ai_models"
    const val DEFAULT_MODEL_FILE_NAME: String = "gemma3-1b-it-int4.task"

    private val SUPPORTED_EXTENSIONS = setOf("task", "litertlm")

    fun modelsDirectory(filesDir: File): File = File(filesDir, MODELS_DIR_NAME).also { it.mkdirs() }

    fun defaultModelPath(filesDir: File): String =
        File(modelsDirectory(filesDir), DEFAULT_MODEL_FILE_NAME).absolutePath

    fun pathFor(filesDir: File, fileName: String): String =
        File(modelsDirectory(filesDir), fileName).absolutePath

    fun isPresent(path: String): Boolean {
        if (path.isBlank()) return false
        val file = File(path)
        return file.isFile && file.canRead() && file.length() > 0L
    }

    /** Already-downloaded model files in the app models folder. */
    fun listInstalled(filesDir: File): List<InstalledOnDeviceModel> {
        val dir = modelsDirectory(filesDir)
        val files = dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS && it.length() > 0L }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
        return files.map { file ->
            InstalledOnDeviceModel(
                fileName = file.name,
                absolutePath = file.absolutePath,
                sizeBytes = file.length(),
                catalogMatch = OnDeviceModelCatalog.findByFileName(file.name),
            )
        }
    }
}

data class InstalledOnDeviceModel(
    val fileName: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val catalogMatch: OnDeviceModelOption?,
) {
    val sizeLabel: String
        get() = when {
            sizeBytes >= 1_000_000_000L -> "%.1f غيغابايت".format(sizeBytes / 1_000_000_000.0)
            sizeBytes >= 1_000_000L -> "%.0f ميغابايت".format(sizeBytes / 1_000_000.0)
            else -> "$sizeBytes بايت"
        }
}
