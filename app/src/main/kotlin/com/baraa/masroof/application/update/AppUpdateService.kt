package com.baraa.masroof.application.update

import android.content.Context
import com.baraa.masroof.application.logging.AppLogCategories
import com.baraa.masroof.application.logging.AppLogService
import java.io.File

class AppUpdateService(
    private val context: Context,
    private val tokenRepository: GitHubTokenRepository,
    private val releaseClient: GitHubReleaseClient,
    private val updateChecker: UpdateChecker,
    private val appLogService: AppLogService,
) {
    fun hasConfiguredToken(): Boolean = tokenRepository.hasToken()

    fun saveToken(token: String) {
        tokenRepository.setToken(token)
        appLogService.info(AppLogCategories.UPDATE, "GitHub token saved")
    }

    fun clearToken() {
        tokenRepository.clearToken()
        appLogService.info(AppLogCategories.UPDATE, "GitHub token cleared")
    }

    fun checkForUpdate(): Result<UpdateCheckResult> {
        val token = tokenRepository.getToken()
        return releaseClient.fetchLatestManifest(token).fold(
            onSuccess = { manifest ->
                Result.success(
                    when (val availability = updateChecker.evaluate(manifest)) {
                        UpdateAvailability.UpToDate -> UpdateCheckResult.UpToDate
                        is UpdateAvailability.Available -> UpdateCheckResult.UpdateAvailable(availability.manifest)
                    },
                )
            },
            onFailure = { error -> Result.failure(mapGitHubError(error, token)) },
        )
    }

    fun downloadUpdate(
        manifest: UpdateManifest,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Result<File> {
        val token = tokenRepository.getToken()
        val destination = updateApkFile(manifest)
        return releaseClient
            .downloadReleaseAsset(token, manifest, destination, onProgress)
            .fold(
                onSuccess = { downloaded ->
                    if (!ApkIntegrityVerifier.matches(downloaded, manifest.sha256)) {
                        downloaded.delete()
                        Result.failure(IllegalStateException("Downloaded APK checksum mismatch"))
                    } else {
                        Result.success(downloaded)
                    }
                },
                onFailure = { error -> Result.failure(mapGitHubError(error, token)) },
            )
    }

    fun updateApkFile(manifest: UpdateManifest): File {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        return File(updatesDir, manifest.apkFileName)
    }

    fun clearDownloadedApk(manifest: UpdateManifest) {
        updateApkFile(manifest).delete()
    }

    fun clearDownloadCache() {
        File(context.cacheDir, "updates").deleteRecursively()
    }

    fun isUpdateStillNeeded(manifest: UpdateManifest): Boolean =
        updateChecker.isUpdateAvailable(manifest)

    private fun mapGitHubError(error: Throwable, @Suppress("UNUSED_PARAMETER") token: String?): Throwable =
        when {
            error is GitHubRequestException && error.requiresToken ->
                PrivateRepoRequiresTokenException()
            else -> error
        }
}

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult

    data class UpdateAvailable(val manifest: UpdateManifest) : UpdateCheckResult
}
