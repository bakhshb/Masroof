package com.baraa.masroof.application.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateCheckPreferencesRepositoryTest {
    private lateinit var repository: UpdateCheckPreferencesRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = UpdateCheckPreferencesRepository(
            context.getSharedPreferences("update_check_prefs_channel_test", Context.MODE_PRIVATE),
        )
    }

    @Test
    fun updateChannel_defaultsToStable() {
        assertEquals(UpdateChannel.STABLE, repository.getUpdateChannel())
    }

    @Test
    fun updateChannel_persistsSelection() {
        repository.setUpdateChannel(UpdateChannel.NIGHTLY)
        assertEquals(UpdateChannel.NIGHTLY, repository.getUpdateChannel())

        repository.setUpdateChannel(UpdateChannel.STABLE)
        assertEquals(UpdateChannel.STABLE, repository.getUpdateChannel())
    }
}
