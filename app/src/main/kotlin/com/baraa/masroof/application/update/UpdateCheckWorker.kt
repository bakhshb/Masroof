package com.baraa.masroof.application.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baraa.masroof.MasroofApplication

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MasroofApplication).container
        if (!container.onboardingPreferencesRepository.isOnboardingCompleted()) {
            return Result.success()
        }

        return when (
            container.updateCheckCoordinator.checkForUpdate("periodic").getOrNull()
        ) {
            null -> Result.retry()
            UpdateCheckResult.UpToDate -> Result.success()
            is UpdateCheckResult.UpdateAvailable -> Result.success()
        }
    }
}
