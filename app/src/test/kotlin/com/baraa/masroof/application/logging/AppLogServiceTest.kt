package com.baraa.masroof.application.logging

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class AppLogServiceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppLogService(context).clear()
    }

    @Test
    fun prune_removesOldestWhenOverMaxEntries() {
        val now = 1_700_000_000_000L
        val service = AppLogService(
            context = context,
            maxEntries = 3,
            retentionDays = 14,
            clock = { now },
        )
        service.clear()

        service.info("test", "one")
        service.info("test", "two")
        service.info("test", "three")
        service.info("test", "four")

        val messages = service.readAll().map { it.message }
        assertEquals(listOf("two", "three", "four"), messages)
    }

    @Test
    fun prune_removesEntriesOlderThanRetention() {
        val now = 1_700_000_000_000L
        val service = AppLogService(
            context = context,
            maxEntries = 500,
            retentionDays = 14,
            clock = { now },
        )
        service.clear()

        val oldService = AppLogService(
            context = context,
            maxEntries = 500,
            retentionDays = 14,
            clock = { now - TimeUnit.DAYS.toMillis(20) },
        )
        oldService.clear()
        oldService.info("test", "stale")

        service.info("test", "fresh")

        val messages = service.readAll().map { it.message }
        assertEquals(listOf("fresh"), messages)
    }

    @Test
    fun serialize_escapesEmbeddedNewlinesAndTabs() {
        val service = AppLogService(context, maxEntries = 10, retentionDays = 14)
        service.clear()
        service.info("test", "line1\nline2\tfield")

        val entry = service.readAll().single()
        assertEquals("line1\nline2\tfield", entry.message)
    }

    @Test
    fun clear_removesDiskFile() {
        val service = AppLogService(context)
        service.info("test", "entry")
        assertTrue(service.readAll().isNotEmpty())
        service.clear()
        assertTrue(service.readAll().isEmpty())
    }
}
