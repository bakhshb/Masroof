package com.baraa.masroof.application.theme

import com.baraa.masroof.data.preferences.SharedPrefsThemePreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import android.content.Context

@RunWith(RobolectricTestRunner::class)
class ThemePreferencesRepositoryTest {
    @Test
    fun defaultsToSystem() {
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("theme_test_default", Context.MODE_PRIVATE)
        val repo = SharedPrefsThemePreferencesRepository(prefs)
        assertEquals(ThemeMode.SYSTEM, repo.getThemeMode())
    }

    @Test
    fun persistsDarkMode() {
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("theme_test_dark", Context.MODE_PRIVATE)
        val repo = SharedPrefsThemePreferencesRepository(prefs)
        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.getThemeMode())
    }

    @Test
    fun fromStorage_fallsBackToDefault() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("nope"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorage("LIGHT"))
    }
}
