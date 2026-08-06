package com.baraa.masroof.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferenceRepositoryTest {

    @Test
    fun defaultsToSystem() {
        val repo = InMemoryThemePreferenceRepository()
        assertEquals(ThemePreference.SYSTEM, repo.snapshot())
    }

    @Test
    fun setPersistsAndEmits() {
        val repo = InMemoryThemePreferenceRepository()
        repo.set(ThemePreference.DARK)
        assertEquals(ThemePreference.DARK, repo.snapshot())
        repo.set(ThemePreference.LIGHT)
        assertEquals(ThemePreference.LIGHT, repo.snapshot())
        repo.set(ThemePreference.SYSTEM)
        assertEquals(ThemePreference.SYSTEM, repo.snapshot())
    }
}

/** JVM-friendly fake mirroring SharedPreferencesThemePreferenceRepository. */
internal class InMemoryThemePreferenceRepository(
    initial: ThemePreference = ThemePreference.SYSTEM,
) : ThemePreferenceRepository {
    private var value = initial
    private val flow = kotlinx.coroutines.flow.MutableStateFlow(initial)

    override fun observe() = flow
    override fun snapshot() = value
    override fun set(preference: ThemePreference) {
        value = preference
        flow.value = preference
    }
}
