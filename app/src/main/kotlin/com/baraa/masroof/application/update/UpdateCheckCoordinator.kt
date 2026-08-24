package com.baraa.masroof.application.update

import com.baraa.masroof.application.logging.AppLogCategories
import com.baraa.masroof.application.logging.AppLogService

class UpdateCheckCoordinator(
    private val appUpdateService: AppUpdateService,
    private val pendingUpdateStore: PendingUpdateStore,
    private val preferencesRepository: UpdateCheckPreferencesRepository,
    private val appLogService: AppLogService,
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val performUpdateCheck: () -> Result<UpdateCheckResult> = { appUpdateService.checkForUpdate() },
) {
    fun shouldCheckNow(nowEpochMs: Long = clock()): Boolean {
        val lastAttempt = preferencesRepository.getLastAttemptEpochMs()
            .takeIf { it != 0L }
            ?: preferencesRepository.getLastCheckEpochMs()
        return lastAttempt == 0L || nowEpochMs - lastAttempt >= minIntervalMs
    }

    fun checkForUpdate(source: String): Result<UpdateCheckResult> {
        val now = clock()
        preferencesRepository.setLastAttemptEpochMs(now)
        appLogService.info(AppLogCategories.UPDATE, "Update check started ($source)")
        val result = performUpdateCheck()
        result.onSuccess { outcome ->
            preferencesRepository.setLastCheckEpochMs(now)
            when (outcome) {
                UpdateCheckResult.UpToDate -> {
                    pendingUpdateStore.clear()
                    appLogService.info(AppLogCategories.UPDATE, "Update check ($source): up to date")
                }
                is UpdateCheckResult.UpdateAvailable -> {
                    pendingUpdateStore.saveAvailable(outcome.manifest)
                    appLogService.info(
                        AppLogCategories.UPDATE,
                        "Update check ($source): ${outcome.manifest.versionName} available",
                    )
                }
            }
        }.onFailure { error ->
            appLogService.error(
                AppLogCategories.UPDATE,
                "Update check ($source) failed: ${error.message ?: error::class.java.simpleName}",
            )
        }
        return result
    }

    fun restorePendingUpdate(): UpdateManifest? = pendingUpdateStore.readAvailable()

    fun clearPendingUpdate() {
        pendingUpdateStore.clear()
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MS: Long = 6L * 60L * 60L * 1000L
    }
}
