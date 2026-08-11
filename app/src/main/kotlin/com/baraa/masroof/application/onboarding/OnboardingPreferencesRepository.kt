package com.baraa.masroof.application.onboarding

/**
 * Small persisted onboarding setup state.
 *
 * Runtime permission truth still comes from Android checks.
 */
interface OnboardingPreferencesRepository {
    fun isOnboardingCompleted(): Boolean

    fun setOnboardingCompleted(completed: Boolean)

    fun getHistoricalImportStartEpochMillis(): Long?

    fun setHistoricalImportStartEpochMillis(epochMillis: Long?)

    fun isHistoricalImportCompleted(): Boolean

    fun setHistoricalImportCompleted(completed: Boolean)
}
