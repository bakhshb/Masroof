package com.baraa.masroof.application.update

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
        appLogService.info(CATEGORY, "Update check started ($source)")
        val result = appUpdateService.checkForUpdate()
        preferencesRepository.setLastCheckEpochMs(System.currentTimeMillis())
        result.onSuccess { outcome ->
            when (outcome) {
                UpdateCheckResult.UpToDate -> {
                    pendingUpdateStore.clear()
                    appLogService.info(CATEGORY, "Update check ($source): up to date")
                }
                is UpdateCheckResult.UpdateAvailable -> {
                    pendingUpdateStore.saveAvailable(outcome.manifest)
                    appLogService.info(
                        CATEGORY,
                        "Update check ($source): ${outcome.manifest.versionName} available",
                    )
                }
            }
        }.onFailure { error ->
            appLogService.error(
                CATEGORY,
                "Update check ($source) failed: ${error.message ?: error::class.java.simpleName}",
            )
        }
        return result
    }

    fun restorePendingUpdate(): UpdateManifest? = pendingUpdateStore.readAvailable()

    companion object {
        const val CATEGORY: String = "update"
        const val DEFAULT_MIN_INTERVAL_MS: Long = 6L * 60L * 60L * 1000L
    }
}
