package com.baraa.masroof.application.maintenance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.application.logging.AppLogService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ParsedEventFactsBackfillCoordinatorTest {

    private lateinit var context: Context
    private lateinit var appLogService: AppLogService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        appLogService = AppLogService(context)
        context.getSharedPreferences(MaintenancePreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun runIfNeeded_reparsesOncePerSchemaVersion() = runBlocking {
        var reparseCount = 0
        val coordinator = coordinator { reparseCount++ }

        coordinator.runIfNeeded(currentSchemaVersion = 11)
        coordinator.runIfNeeded(currentSchemaVersion = 11)

        assertEquals(1, reparseCount)
    }

    @Test
    fun runIfNeeded_skipsWhenAlreadyAtCurrentVersion() = runBlocking {
        context.getSharedPreferences(MaintenancePreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(MaintenancePreferences.KEY_LAST_REPARSED_SCHEMA_VERSION, 11)
            .commit()

        var reparseCount = 0
        val coordinator = coordinator { reparseCount++ }

        coordinator.runIfNeeded(currentSchemaVersion = 11)

        assertEquals(0, reparseCount)
    }

    @Test
    fun runIfNeeded_runsAgainWhenSchemaAdvances() = runBlocking {
        var reparseCount = 0
        val coordinator = coordinator {
            reparseCount++
        }

        coordinator.runIfNeeded(currentSchemaVersion = 11)
        coordinator.runIfNeeded(currentSchemaVersion = 12)

        assertEquals(2, reparseCount)
    }

    @Test
    fun runIfNeeded_doesNotMarkCompleteWhenReparseFails() = runBlocking {
        val prefs = context.getSharedPreferences(MaintenancePreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val coordinator = ParsedEventFactsBackfillCoordinator(
            prefs = prefs,
            appLogService = appLogService,
            reparseAllStoredEvents = {
                ReparseAllStoredEventsResult(refreshedCount = 0, failedCount = 2)
            },
        )

        coordinator.runIfNeeded(currentSchemaVersion = 11)
        coordinator.runIfNeeded(currentSchemaVersion = 11)

        assertEquals(0, prefs.getInt(MaintenancePreferences.KEY_LAST_REPARSED_SCHEMA_VERSION, 0))
    }

    private fun coordinator(reparse: suspend () -> Unit): ParsedEventFactsBackfillCoordinator {
        val prefs = context.getSharedPreferences(MaintenancePreferences.PREFS_NAME, Context.MODE_PRIVATE)
        return ParsedEventFactsBackfillCoordinator(
            prefs = prefs,
            appLogService = appLogService,
            reparseAllStoredEvents = {
                reparse()
                successResult()
            },
        )
    }

    private fun successResult(refreshedCount: Int = 1) =
        ReparseAllStoredEventsResult(refreshedCount = refreshedCount, failedCount = 0)
}
