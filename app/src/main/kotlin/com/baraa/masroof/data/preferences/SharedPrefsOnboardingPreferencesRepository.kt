package com.baraa.masroof.data.preferences

import android.content.SharedPreferences
import com.baraa.masroof.application.onboarding.OnboardingPreferencesRepository

class SharedPrefsOnboardingPreferencesRepository(
    private val prefs: SharedPreferences,
) : OnboardingPreferencesRepository {
    override fun isOnboardingStarted(): Boolean =
        prefs.getBoolean(KEY_ONBOARDING_STARTED, false)

    override fun setOnboardingStarted(started: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_STARTED, started).apply()
    }

    override fun isOnboardingCompleted(): Boolean =
        prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    override fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    override fun getHistoricalImportStartEpochMillis(): Long? =
        if (!prefs.contains(KEY_IMPORT_START_EPOCH_MILLIS)) {
            null
        } else {
            prefs.getLong(KEY_IMPORT_START_EPOCH_MILLIS, 0L)
        }

    override fun setHistoricalImportStartEpochMillis(epochMillis: Long?) {
        prefs.edit().apply {
            if (epochMillis == null) {
                remove(KEY_IMPORT_START_EPOCH_MILLIS)
            } else {
                putLong(KEY_IMPORT_START_EPOCH_MILLIS, epochMillis)
            }
        }.apply()
    }

    override fun isHistoricalImportCompleted(): Boolean =
        prefs.getBoolean(KEY_IMPORT_COMPLETED, false)

    override fun setHistoricalImportCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_IMPORT_COMPLETED, completed).apply()
    }

    companion object {
        const val PREFS_NAME: String = "onboarding_prefs"
        private const val KEY_ONBOARDING_STARTED = "onboarding_started"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_IMPORT_START_EPOCH_MILLIS = "historical_import_start_epoch_millis"
        private const val KEY_IMPORT_COMPLETED = "historical_import_completed"
    }
}
