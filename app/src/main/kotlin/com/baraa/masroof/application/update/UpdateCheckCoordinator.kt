package com.baraa.masroof.application.update

import com.baraa.masroof.application.logging.AppLogCategories
import com.baraa.masroof.application.logging.AppLogFormatting
import com.baraa.masroof.application.logging.AppLogService

class UpdateCheckCoordinator(
    private val appUpdateService: AppUpdateService,
    private val pendingUpdateStore: PendingUpdateStore,
    private val preferencesRepository: UpdateCheckPreferencesRepository,
    private val appLogService: AppLogService,
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
) {
    fun shouldCheckNow(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        val lastCheck = preferencesRepository.getLastCheckEpochMs()
        return lastCheck == 0L || nowEpochMs - lastCheck >= minIntervalMs
    }

    fun checkForUpdate(source: String): Result<UpdateCheckResult> {
        appLogService.info(AppLogCategories.UPDATE, "Update check started ($source)")
        val result = appUpdateService.checkForUpdate()
        result.onSuccess { outcome ->
            preferencesRepository.setLastCheckEpochMs(System.currentTimeMillis())
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

    companion object {
        const val DEFAULT_MIN_INTERVAL_MS: Long = 6L * 60L * 60L * 1000L
    }
}
