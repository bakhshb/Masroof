package com.baraa.masroof.ai

import android.content.Context
import android.net.Uri
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Downloads or imports on-device model files into [OnDeviceModelStore.modelsDirectory].
 * Never logs tokens or URLs with credentials.
 */
class OnDeviceModelDownloader {

    sealed class Progress {
        data class Running(val downloadedBytes: Long, val totalBytes: Long?) : Progress()
        data class Finished(val file: File) : Progress()
        data class Failed(val messageAr: String) : Progress()
    }

    /**
     * Download [option] into app storage. [hfToken] is required for gated
     * Hugging Face models (Gemma). Pass null only for public files.
     */
    suspend fun download(
        filesDir: File,
        option: OnDeviceModelOption,
        hfToken: String?,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        val dest = File(OnDeviceModelStore.modelsDirectory(filesDir), option.fileName)
        val partial = File(dest.absolutePath + ".partial")
        runCatching {
            if (dest.isFile && dest.length() > MIN_VALID_BYTES) return@runCatching dest
            ensureFreeSpace(OnDeviceModelStore.modelsDirectory(filesDir), ESTIMATED_MODEL_BYTES)
            partial.delete()
            val connection = openDownload(option.downloadUrl, hfToken?.trim()?.takeIf { it.isNotEmpty() })
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    val hint = when (code) {
                        401, 403 -> "يلزم توكن Hugging Face بعد قبول رخصة Gemma"
                        404 -> "الملف غير موجود على الخادم"
                        else -> "فشل التنزيل (رمز $code)"
                    }
                    error(hint)
                }
                val total = connection.contentLengthLong.takeIf { it > 0L }
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER)
                        var downloaded = 0L
                        var lastPosted = -1L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            // Throttle UI updates (~every 512 KiB).
                            if (downloaded - lastPosted >= 512L * 1024L || total == null) {
                                lastPosted = downloaded
                                val snapshot = downloaded
                                withContext(Dispatchers.Main.immediate) {
                                    onProgress(snapshot, total)
                                }
                            }
                        }
                        withContext(Dispatchers.Main.immediate) {
                            onProgress(downloaded, total)
                        }
                    }
                }
                if (partial.length() < MIN_VALID_BYTES) {
                    partial.delete()
                    error("الملف المنزّل صغير جدًا — تحقق من التوكن والرخصة")
                }
                if (dest.exists()) dest.delete()
                if (!partial.renameTo(dest)) {
                    partial.copyTo(dest, overwrite = true)
                    partial.delete()
                }
                dest
            } finally {
                connection.disconnect()
            }
        }.onFailure {
            partial.delete()
        }
    }

    /** Copy a user-picked file (Downloads / Files app) into the models folder. */
    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        preferredFileName: String?,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val name = preferredFileName
                ?.takeIf { it.isNotBlank() }
                ?: queryDisplayName(context, uri)
                ?: "imported-model.task"
            val safeName = sanitizeFileName(name)
            if (safeName.substringAfterLast('.').lowercase() !in setOf("task", "litertlm")) {
                error("اختر ملف نموذج بامتداد .task أو .litertlm")
            }
            val dest = File(OnDeviceModelStore.modelsDirectory(context.filesDir), safeName)
            ensureFreeSpace(OnDeviceModelStore.modelsDirectory(context.filesDir), ESTIMATED_MODEL_BYTES)
            resolver.openInputStream(uri)?.use { input ->
                copyStream(input, dest)
            } ?: error("تعذّر فتح الملف المحدد")
            if (dest.length() < MIN_VALID_BYTES) {
                dest.delete()
                error("الملف صغير جدًا — تأكد أنه نموذج كامل وليس صفحة ويب")
            }
            dest
        }
    }

    private fun openDownload(url: String, hfToken: String?): HttpURLConnection {
        // Manual redirect loop so we can drop Authorization on cross-host CDN hops
        // after HF issues a signed URL.
        var current = url
        var token = hfToken
        repeat(MAX_REDIRECTS) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                useCaches = false
                doInput = true
                setRequestProperty("User-Agent", "MasroofAndroid/1.0")
                setRequestProperty("Accept", "*/*")
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }
            val code = connection.responseCode
            if (code in REDIRECT_CODES) {
                val location = connection.getHeaderField("Location")
                    ?: error("إعادة توجيه بدون عنوان")
                connection.disconnect()
                current = if (location.startsWith("http")) location else {
                    URL(URL(current), location).toString()
                }
                // Signed CDN URLs must not send the HF token.
                if (!current.contains("huggingface.co")) {
                    token = null
                }
                return@repeat
            }
            return connection
        }
        error("تجاوز حد إعادة التوجيه")
    }

    private fun copyStream(input: InputStream, dest: File) {
        val partial = File(dest.absolutePath + ".partial")
        partial.delete()
        partial.outputStream().use { output ->
            input.copyTo(output, DEFAULT_BUFFER)
        }
        if (dest.exists()) dest.delete()
        if (!partial.renameTo(dest)) {
            partial.copyTo(dest, overwrite = true)
            partial.delete()
        }
    }

    private fun ensureFreeSpace(dir: File, needed: Long) {
        val stat = StatFs(dir.absolutePath)
        val available = stat.availableBlocksLong * stat.blockSizeLong
        if (available < needed) {
            error("المساحة غير كافية — حرّر حوالي ${needed / (1024 * 1024)} ميغابايت")
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx < 0) return null
            return it.getString(idx)
        }
    }

    companion object {
        private const val DEFAULT_BUFFER = 256 * 1024
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 120_000
        private const val MAX_REDIRECTS = 8
        private const val MIN_VALID_BYTES = 1_000_000L // reject HTML login pages
        private const val ESTIMATED_MODEL_BYTES = 600L * 1024L * 1024L
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        fun sanitizeFileName(name: String): String {
            val base = name.substringAfterLast('/').substringAfterLast('\\')
            return base.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "model.task" }
        }
    }
}
