package com.baraa.masroof.application.update

import android.content.Context
import java.io.File

class AppUpdateService(
    private val context: Context,
    private val tokenRepository: GitHubTokenRepository,
    private val releaseClient: GitHubReleaseClient,
    private val updateChecker: UpdateChecker,
) {
    fun hasConfiguredToken(): Boolean = tokenRepository.hasToken()

    fun saveToken(token: String) {
        tokenRepository.setToken(token)
    }

    fun clearToken() {
        tokenRepository.clearToken()
    }

    fun checkForUpdate(): Result<UpdateCheckResult> {
        val token = tokenRepository.getToken()
            ?: return Result.failure(MissingGitHubTokenException())

        return releaseClient.fetchLatestManifest(token).map { manifest ->
            when (val availability = updateChecker.evaluate(manifest)) {
                UpdateAvailability.UpToDate -> UpdateCheckResult.UpToDate
                is UpdateAvailability.Available -> UpdateCheckResult.UpdateAvailable(availability.manifest)
            }
        }
    }

    fun downloadUpdate(
        manifest: UpdateManifest,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Result<File> {
        val token = tokenRepository.getToken()
            ?: return Result.failure(MissingGitHubTokenException())

        val destination = updateApkFile(manifest)
        return releaseClient
            .downloadReleaseAsset(token, manifest, destination, onProgress)
            .mapCatching { downloaded ->
                if (!ApkIntegrityVerifier.matches(downloaded, manifest.sha256)) {
                    downloaded.delete()
                    throw IllegalStateException("Downloaded APK checksum mismatch")
                }
                downloaded
            }
    }

    fun updateApkFile(manifest: UpdateManifest): File {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        return File(updatesDir, manifest.apkFileName)
    }

    fun clearDownloadedApk(manifest: UpdateManifest) {
        updateApkFile(manifest).delete()
    }
}

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult

    data class UpdateAvailable(val manifest: UpdateManifest) : UpdateCheckResult
}

class MissingGitHubTokenException : Exception("GitHub token is not configured")
