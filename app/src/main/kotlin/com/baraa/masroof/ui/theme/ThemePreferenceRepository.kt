package com.baraa.masroof.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists and exposes the user's appearance preference.
 *
 * Mirrors the SharedPreferences pattern used by onboarding and developer
 * preferences so tests can substitute a fake without Android Keystore.
 */
interface ThemePreferenceRepository {
    fun observe(): Flow<ThemePreference>
    fun snapshot(): ThemePreference
    fun set(preference: ThemePreference)
}

class SharedPreferencesThemePreferenceRepository(
    context: Context,
) : ThemePreferenceRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val state = MutableStateFlow(read())

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_THEME || key == null) {
            state.value = read()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun observe(): Flow<ThemePreference> = state.asStateFlow()

    override fun snapshot(): ThemePreference = state.value

    override fun set(preference: ThemePreference) {
        prefs.edit().putString(KEY_THEME, preference.name).apply()
        state.value = preference
    }

    private fun read(): ThemePreference {
        val raw = prefs.getString(KEY_THEME, ThemePreference.SYSTEM.name)
            ?: ThemePreference.SYSTEM.name
        return runCatching { ThemePreference.valueOf(raw) }
            .getOrDefault(ThemePreference.SYSTEM)
    }

    companion object {
        private const val PREFS_NAME = "masroof_appearance"
        private const val KEY_THEME = "theme_preference"
    }
}
