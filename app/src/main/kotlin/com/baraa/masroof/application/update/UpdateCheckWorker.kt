package com.baraa.masroof.application.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baraa.masroof.MasroofApplication
import java.io.IOException

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MasroofApplication).container
        if (!container.onboardingPreferencesRepository.isOnboardingCompleted()) {
            return Result.success()
        }

        return container.updateCheckCoordinator.checkForUpdate("periodic").fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                if (error is IOException) Result.retry() else Result.success()
            },
        )
    }
}
