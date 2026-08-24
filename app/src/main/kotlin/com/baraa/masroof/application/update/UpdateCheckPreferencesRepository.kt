package com.baraa.masroof.application.update

import android.content.SharedPreferences

class UpdateCheckPreferencesRepository(
    private val prefs: SharedPreferences,
) {
    fun getLastCheckEpochMs(): Long = prefs.getLong(KEY_LAST_CHECK_MS, 0L)

    fun setLastCheckEpochMs(epochMs: Long) {
        prefs.edit().putLong(KEY_LAST_CHECK_MS, epochMs).apply()
    }

    companion object {
        const val PREFS_NAME: String = "update_check_prefs"
        private const val KEY_LAST_CHECK_MS: String = "last_check_epoch_ms"
    }
}
