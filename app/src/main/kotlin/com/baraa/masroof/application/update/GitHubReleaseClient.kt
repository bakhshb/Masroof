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
    private val apiBaseUrl: String = DEFAULT_API_BASE_URL,
) {
    fun findBestManifest(
        channel: UpdateChannel,
        installedVersionCode: Int,
        token: String? = null,
    ): Result<UpdateManifest?> {
        val tokenWasProvided = token.isConfigured()
        val manifests = mutableListOf<UpdateManifest>()

        when (channel) {
            UpdateChannel.STABLE -> {
                fetchLatestStableManifest(token, tokenWasProvided).fold(
                    onSuccess = { manifests.add(it) },
                    onFailure = { return Result.failure(it) },
                )
            }
            UpdateChannel.NIGHTLY -> {
                val rollingResult = fetchRollingNightlyManifest(token, tokenWasProvided)
                var rollingMissing = false
                rollingResult.fold(
                    onSuccess = { manifests.add(it) },
                    onFailure = { error ->
                        if (error.isNotFound()) {
                            rollingMissing = true
                        } else {
                            return Result.failure(error)
                        }
                    },
                )

                fetchLatestStableManifest(token, tokenWasProvided).onSuccess { manifests.add(it) }

                if (rollingMissing) {
                    scanBestImmutableNightlyManifest(installedVersionCode, token, tokenWasProvided)
                        ?.let { manifests.add(it) }
                }
            }
        }

        return Result.success(
            UpdateManifestSelector.bestForChannel(
                channel = channel,
                installedVersionCode = installedVersionCode,
                manifests = manifests,
            ),
        )
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

    private fun fetchLatestStableManifest(
        token: String?,
        tokenWasProvided: Boolean,
    ): Result<UpdateManifest> {
        val request =
            authorizedRequest(token)
                .url("$apiBaseUrl/repos/$owner/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

        return executeJson(request, tokenWasProvided).fold(
            onSuccess = { release -> manifestFromRelease(release, token, tokenWasProvided) },
            onFailure = { Result.failure(it) },
        )
    }

    private fun fetchRollingNightlyManifest(
        token: String?,
        tokenWasProvided: Boolean,
    ): Result<UpdateManifest> =
        fetchReleaseByTag(ROLLING_NIGHTLY_TAG, token, tokenWasProvided).fold(
            onSuccess = { release -> manifestFromRelease(release, token, tokenWasProvided) },
            onFailure = { Result.failure(it) },
        )

    private fun scanBestImmutableNightlyManifest(
        installedVersionCode: Int,
        token: String?,
        tokenWasProvided: Boolean,
    ): UpdateManifest? {
        var best: UpdateManifest? = null
        var page = 1

        while (page <= MAX_SCAN_PAGES) {
            val pageReleases =
                listReleasesPage(token, tokenWasProvided, page).getOrElse { return best }
            if (pageReleases.isEmpty()) {
                break
            }

            for (release in pageReleases) {
                val releaseTag =
                    release["tag_name"]?.jsonPrimitive?.contentOrNull
                        ?: continue
                if (!IMMUTABLE_NIGHTLY_TAG_REGEX.containsMatchIn(releaseTag)) {
                    continue
                }
                if (!releaseHasVersionAsset(release)) {
                    continue
                }

                val manifest =
                    manifestFromRelease(release, token, tokenWasProvided).getOrNull()
                        ?: continue
                if (manifest.normalizedChannel == UpdateChannel.NIGHTLY.storageValue() &&
                    manifest.versionCode > installedVersionCode
                ) {
                    best = UpdateManifestSelector.pickBetter(best, manifest)
                }
            }

            if (pageReleases.size < RELEASES_PAGE_SIZE) {
                break
            }
            page++
        }

        return best
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
            val manifest = json.decodeFromString(UpdateManifest.serializer(), body)
            if (manifest.releaseTag.isNullOrBlank()) {
                manifest.withReleaseTag(releaseTag)
            } else {
                manifest
            }
        }
    }

    private fun releaseHasVersionAsset(release: JsonObject): Boolean {
        val assets = release["assets"]?.jsonArray ?: JsonArray(emptyList())
        return assets.any { asset ->
            asset.jsonObject["name"]?.jsonPrimitive?.contentOrNull == VERSION_JSON_NAME
        }
    }

    private fun listReleasesPage(
        token: String?,
        tokenWasProvided: Boolean,
        page: Int,
    ): Result<List<JsonObject>> {
        val request =
            authorizedRequest(token)
                .url("$apiBaseUrl/repos/$owner/$repo/releases?per_page=$RELEASES_PAGE_SIZE&page=$page")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

        return executeJsonArray(request, tokenWasProvided).map { array ->
            array.map { it.jsonObject }
        }
    }

    private fun fetchReleaseByTag(
        releaseTag: String,
        token: String?,
        tokenWasProvided: Boolean,
    ): Result<JsonObject> {
        val normalizedTag = normalizeReleaseTagForApi(releaseTag)
        val request =
            authorizedRequest(token)
                .url("$apiBaseUrl/repos/$owner/$repo/releases/tags/$normalizedTag")
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

    private fun Throwable.isNotFound(): Boolean =
        this is GitHubRequestException && httpCode == 404

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
        const val ROLLING_NIGHTLY_TAG: String = "nightly"
        private const val DEFAULT_API_BASE_URL: String = "https://api.github.com"
        private const val RELEASES_PAGE_SIZE: Int = 100
        private const val MAX_SCAN_PAGES: Int = 2
        private val IMMUTABLE_NIGHTLY_TAG_REGEX = Regex("^v[0-9].*-nightly-")

        internal fun normalizeReleaseTagForApi(releaseTag: String): String {
            val trimmed = releaseTag.trim()
            if (trimmed == ROLLING_NIGHTLY_TAG) {
                return trimmed
            }
            return if (trimmed.startsWith("v")) trimmed else "v$trimmed"
        }

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
