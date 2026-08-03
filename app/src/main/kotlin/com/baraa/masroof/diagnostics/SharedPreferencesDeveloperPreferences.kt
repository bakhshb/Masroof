package com.baraa.masroof.diagnostics

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences-backed implementation of [DeveloperPreferences].
 * Defaults: [showDevDetails] = false, [testDataMode] = false.
 *
 * Test mode is **never** persisted as `true` across process restarts —
 * the user must enable it explicitly each session.
 */
class SharedPreferencesDeveloperPreferences(context: Context) : DeveloperPreferences {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var showDevDetails: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DEV, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_DEV, value).apply()

    override var testDataMode: Boolean
        get() = prefs.getBoolean(KEY_TEST_DATA, false)
        set(value) = prefs.edit().putBoolean(KEY_TEST_DATA, value).apply()

    companion object {
        const val PREFS_NAME: String = "masroof_dev_prefs"
        const val KEY_SHOW_DEV: String = "show_dev_details"
        const val KEY_TEST_DATA: String = "test_data_mode"
    }
}