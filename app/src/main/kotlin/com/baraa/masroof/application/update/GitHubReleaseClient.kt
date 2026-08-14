package com.baraa.masroof.application.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

class GitHubReleaseClient(
    private val httpClient: OkHttpClient,
    private val owner: String,
    private val repo: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun fetchLatestManifest(token: String): Result<UpdateManifest> {
        val release = fetchLatestRelease(token).getOrElse { return Result.failure(it) }
        val assets = release["assets"]?.jsonArray ?: JsonArray(emptyList())
        val versionAsset = assets.firstOrNull { asset ->
            asset.jsonObject["name"]?.jsonPrimitive?.contentOrNull == VERSION_JSON_NAME
        }?.jsonObject

        val downloadUrl = versionAsset?.get("url")?.jsonPrimitive?.contentOrNull
            ?: return Result.failure(IllegalStateException("version.json asset not found in latest release"))

        return downloadText(downloadUrl, token).mapCatching { body ->
            json.decodeFromString(UpdateManifest.serializer(), body)
        }
    }

    fun downloadReleaseAsset(
        token: String,
        manifest: UpdateManifest,
        destination: java.io.File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Result<java.io.File> {
        val release = fetchLatestRelease(token).getOrElse { return Result.failure(it) }
        val assets = release["assets"]?.jsonArray ?: JsonArray(emptyList())
        val apkAsset = assets.firstOrNull { asset ->
            asset.jsonObject["name"]?.jsonPrimitive?.contentOrNull == manifest.apkFileName
        }?.jsonObject

        val downloadUrl = apkAsset?.get("url")?.jsonPrimitive?.contentOrNull
            ?: return Result.failure(IllegalStateException("APK asset not found: ${manifest.apkFileName}"))

        return downloadBinary(downloadUrl, token, destination, onProgress)
    }

    private fun fetchLatestRelease(token: String): Result<JsonObject> {
        val request =
            Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                .header("Authorization", bearer(token))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()

        return executeJson(request)
    }

    private fun downloadText(url: String, token: String): Result<String> {
        val request =
            Request.Builder()
                .url(url)
                .header("Authorization", bearer(token))
                .header("Accept", "application/octet-stream")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()

        return executeBody(request).map { it.string() }
    }

    private fun downloadBinary(
        url: String,
        token: String,
        destination: java.io.File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit,
    ): Result<java.io.File> {
        val request =
            Request.Builder()
                .url(url)
                .header("Authorization", bearer(token))
                .header("Accept", "application/octet-stream")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()

        return executeBody(request).mapCatching { body ->
            destination.parentFile?.mkdirs()
            val totalBytes = body.contentLength()
            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesReadTotal = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesReadTotal += read
                        onProgress(bytesReadTotal, totalBytes)
                    }
                }
            }
            destination
        }
    }

    private fun executeJson(request: Request): Result<JsonObject> =
        executeBody(request).mapCatching { body ->
            json.parseToJsonElement(body.string()).jsonObject
        }

    private fun executeBody(request: Request): Result<okhttp3.ResponseBody> {
        val response = httpClient.newCall(request).execute()
        val body = response.body
        if (!response.isSuccessful) {
            body?.close()
            val code = response.code
            response.close()
            return Result.failure(
                IllegalStateException(
                    when (code) {
                        401, 403 -> "GitHub authentication failed (HTTP $code)"
                        404 -> "Release not found (HTTP $code)"
                        else -> "GitHub request failed (HTTP $code)"
                    },
                ),
            )
        }
        if (body == null) {
            response.close()
            return Result.failure(IllegalStateException("Empty response body"))
        }
        return Result.success(body)
    }

    private fun bearer(token: String): String =
        if (token.startsWith("Bearer ") || token.startsWith("token ")) token else "Bearer $token"

    companion object {
        const val VERSION_JSON_NAME: String = "version.json"
    }
}
