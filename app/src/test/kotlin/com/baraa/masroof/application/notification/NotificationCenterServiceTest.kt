package com.baraa.masroof.application.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCenterServiceTest {
    @Test
    fun `build aggregates active notifications and counts unread`() {
        val prefs = FakeNotificationPreferencesRepository()
        val service = NotificationCenterService(prefs)
        val snapshot = NotificationCenterSnapshot(
            smsPermissionGranted = false,
            reviewRequiredCount = 2,
            unregisteredCardCount = 1,
            unregisteredAccountCount = 3,
            excludedForeignCurrencyCount = 4,
            periodLabel = "Aug 2026",
            rescanStatusName = "OK",
            updateAvailableVersion = "0.3.0",
        )

        val result = service.build(snapshot)

        assertEquals(7, result.items.size)
        assertEquals(7, result.unreadCount)
        assertTrue(result.items.any { it.type == NotificationType.SMS_PERMISSION })
        assertTrue(result.items.any { it.type == NotificationType.REVIEW_REQUIRED })
        assertTrue(result.items.any { it.type == NotificationType.APP_UPDATE_AVAILABLE })
    }

    @Test
    fun `markRead reduces unread count but keeps active notification`() {
        val prefs = FakeNotificationPreferencesRepository()
        val service = NotificationCenterService(prefs)
        val snapshot = NotificationCenterSnapshot(
            smsPermissionGranted = false,
            reviewRequiredCount = 1,
            unregisteredCardCount = 0,
            unregisteredAccountCount = 0,
            excludedForeignCurrencyCount = 0,
            periodLabel = "Aug 2026",
        )

        val initial = service.build(snapshot)
        service.markRead(NotificationCenterService.ID_REVIEW_REQUIRED)
        val afterRead = service.build(snapshot)

        assertEquals(2, initial.unreadCount)
        assertEquals(1, afterRead.unreadCount)
        assertTrue(afterRead.items.single { it.type == NotificationType.REVIEW_REQUIRED }.isRead)
        assertFalse(afterRead.items.single { it.type == NotificationType.SMS_PERMISSION }.isRead)
    }

    @Test
    fun `resolved conditions remove notifications from list`() {
        val prefs = FakeNotificationPreferencesRepository()
        val service = NotificationCenterService(prefs)
        prefs.markRead(NotificationCenterService.ID_SMS_PERMISSION)

        val result = service.build(
            NotificationCenterSnapshot(
                smsPermissionGranted = true,
                reviewRequiredCount = 0,
                unregisteredCardCount = 0,
                unregisteredAccountCount = 0,
                excludedForeignCurrencyCount = 0,
                periodLabel = "Aug 2026",
            ),
        )

        assertTrue(result.items.isEmpty())
        assertEquals(0, result.unreadCount)
        assertTrue(prefs.getReadIds().isEmpty())
    }

    private class FakeNotificationPreferencesRepository : NotificationPreferencesRepository {
        private var readIds: Set<String> = emptySet()

        override fun getReadIds(): Set<String> = readIds

        override fun markRead(id: String) {
            readIds = readIds + id
        }

        override fun clearRead(id: String) {
            readIds = readIds - id
        }

        override fun setReadIds(ids: Set<String>) {
            readIds = ids.toSet()
        }
    }
}
