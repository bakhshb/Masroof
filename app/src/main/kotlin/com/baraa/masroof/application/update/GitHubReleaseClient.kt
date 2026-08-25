package com.baraa.masroof.application.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class GitHubReleaseClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val owner: String,
    private val repo: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun findBestManifest(
        channel: UpdateChannel,
        installedVersionCode: Int,
        token: String? = null,
    ): Result<UpdateManifest?> {
        val tokenWasProvided = token.isConfigured()
        return listReleaseManifests(token, tokenWasProvided).map { manifests ->
            UpdateManifestSelector.bestForChannel(channel, installedVersionCode, manifests)
        }
    }

    fun fetchManifest(
        releaseTag: String,
        token: String? = null,
    ): Result<UpdateManifest> {
        val tokenWasProvided = token.isConfigured()
        val release = fetchReleaseByTag(releaseTag, token, tokenWasProvided).getOrElse { return Result.failure(it) }
        return manifestFromRelease(release, token, tokenWasProvided)
    }

    fun downloadReleaseAsset(
        token: String? = null,
        manifest: UpdateManifest,
        destination: java.io.File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Result<java.io.File> {
        val tokenWasProvided = token.isConfigured()
        val releaseTag = manifest.resolvedReleaseTag()
        val release = fetchReleaseByTag(releaseTag, token, tokenWasProvided).getOrElse { return Result.failure(it) }
        val assets = release["assets"]?.jsonArray ?: JsonArray(emptyList())
        val apkAsset = assets.firstOrNull { asset ->
            asset.jsonObject["name"]?.jsonPrimitive?.contentOrNull == manifest.apkFileName
        }?.jsonObject

        val downloadUrl = apkAsset?.get("url")?.jsonPrimitive?.contentOrNull
            ?: return Result.failure(IllegalStateException("APK asset not found: ${manifest.apkFileName}"))

        return downloadBinary(downloadUrl, token, tokenWasProvided, destination, onProgress)
    }

    private fun listReleaseManifests(token: String?, tokenWasProvided: Boolean): Result<List<UpdateManifest>> {
        val releases = listReleases(token, tokenWasProvided).getOrElse { return Result.failure(it) }
        if (releases.isEmpty()) {
            return Result.success(emptyList())
        }

        val manifests = mutableListOf<UpdateManifest>()
        var manifestFailures = 0
        for (release in releases) {
            manifestFromRelease(release, token, tokenWasProvided)
                .onSuccess { manifests += it }
                .onFailure { manifestFailures++ }
        }
        if (manifests.isEmpty() && manifestFailures > 0) {
            return Result.failure(
                IllegalStateException("Could not read version.json from any GitHub release"),
            )
        }
        return Result.success(manifests)
    }

    private fun manifestFromRelease(
        release: JsonObject,
        token: String?,
        tokenWasProvided: Boolean,
    ): Result<UpdateManifest> {
        val releaseTag =
            release["tag_name"]?.jsonPrimitive?.contentOrNull
                ?: return Result.failure(IllegalStateException("Release is missing tag_name"))
        val assets = release["assets"]?.jsonArray ?: JsonArray(emptyList())
        val versionAsset = assets.firstOrNull { asset ->
            asset.jsonObject["name"]?.jsonPrimitive?.contentOrNull == VERSION_JSON_NAME
        }?.jsonObject

        val downloadUrl = versionAsset?.get("url")?.jsonPrimitive?.contentOrNull
            ?: return Result.failure(IllegalStateException("version.json asset not found in release $releaseTag"))

        return downloadText(downloadUrl, token, tokenWasProvided).mapCatching { body ->
            json.decodeFromString(UpdateManifest.serializer(), body)
                .withReleaseTag(releaseTag)
        }
    }

    private fun listReleases(token: String?, tokenWasProvided: Boolean): Result<List<JsonObject>> {
        val releases = mutableListOf<JsonObject>()
        var page = 1
        while (true) {
            val request =
                authorizedRequest(token)
                    .url("https://api.github.com/repos/$owner/$repo/releases?per_page=$RELEASES_PAGE_SIZE&page=$page")
                    .header("Accept", "application/vnd.github+json")
                    .get()
                    .build()

            val pageReleases =
                executeJsonArray(request, tokenWasProvided).getOrElse { return Result.failure(it) }
            if (pageReleases.isEmpty()) {
                break
            }
            releases += pageReleases.map { it.jsonObject }
            if (pageReleases.size < RELEASES_PAGE_SIZE) {
                break
            }
            page++
        }
        return Result.success(releases)
    }

    private fun fetchReleaseByTag(
        releaseTag: String,
        token: String?,
        tokenWasProvided: Boolean,
    ): Result<JsonObject> {
        val normalizedTag = if (releaseTag.startsWith("v")) releaseTag else "v$releaseTag"
        val request =
            authorizedRequest(token)
                .url("https://api.github.com/repos/$owner/$repo/releases/tags/$normalizedTag")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

        return executeJson(request, tokenWasProvided)
    }

    private fun downloadText(url: String, token: String?, tokenWasProvided: Boolean): Result<String> {
        val request =
            authorizedRequest(token)
                .url(url)
                .header("Accept", "application/octet-stream")
                .get()
                .build()

        return executeBody(request, tokenWasProvided).map { it.string() }
    }

    private fun downloadBinary(
        url: String,
        token: String?,
        tokenWasProvided: Boolean,
        destination: java.io.File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit,
    ): Result<java.io.File> {
        val request =
            authorizedRequest(token)
                .url(url)
                .header("Accept", "application/octet-stream")
                .get()
                .build()

        return executeBody(request, tokenWasProvided).mapCatching { body ->
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

    internal fun buildAuthorizedRequestForTest(token: String?): Request.Builder = authorizedRequest(token)

    private fun authorizedRequest(token: String?): Request.Builder {
        val builder =
            Request.Builder()
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "Masroof-Android")
        val trimmed = token?.trim().orEmpty()
        if (trimmed.isNotEmpty()) {
            builder.header("Authorization", authorizationHeader(trimmed))
        }
        return builder
    }

    private fun executeJson(request: Request, tokenWasProvided: Boolean): Result<JsonObject> =
        executeBody(request, tokenWasProvided).mapCatching { body ->
            json.parseToJsonElement(body.string()).jsonObject
        }

    private fun executeJsonArray(request: Request, tokenWasProvided: Boolean): Result<JsonArray> =
        executeBody(request, tokenWasProvided).mapCatching { body ->
            json.parseToJsonElement(body.string()).jsonArray
        }

    private fun executeBody(request: Request, tokenWasProvided: Boolean): Result<okhttp3.ResponseBody> {
        val response = httpClient.newCall(request).execute()
        val body = response.body
        if (!response.isSuccessful) {
            val errorSnippet = body?.string()?.take(200).orEmpty()
            body?.close()
            val code = response.code
            response.close()
            return Result.failure(
                GitHubRequestException(
                    httpCode = code,
                    tokenWasProvided = tokenWasProvided,
                    message = when (code) {
                        401, 403 ->
                            "GitHub authentication failed (HTTP $code). " +
                                "Use a read-only token with Contents access for this repo."
                        404 -> "Release not found (HTTP $code)"
                        else -> "GitHub request failed (HTTP $code): $errorSnippet"
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

    internal fun authorizationHeader(token: String): String {
        val trimmed = token.trim()
        when {
            trimmed.startsWith("Bearer ", ignoreCase = true) -> return trimmed
            trimmed.startsWith("token ", ignoreCase = true) -> return trimmed
            trimmed.startsWith("github_pat_") -> return "Bearer $trimmed"
            trimmed.startsWith("ghp_") -> return "token $trimmed"
            trimmed.startsWith("gho_") -> return "token $trimmed"
            else -> return "Bearer $trimmed"
        }
    }

    private fun String?.isConfigured(): Boolean = !this.isNullOrBlank()

    companion object {
        const val VERSION_JSON_NAME: String = "version.json"
        private const val RELEASES_PAGE_SIZE: Int = 100

        fun defaultHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
    }
}
