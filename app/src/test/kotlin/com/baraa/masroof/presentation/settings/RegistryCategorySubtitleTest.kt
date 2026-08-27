package com.baraa.masroof.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class RegistryCategorySubtitleTest {
    @Test
    fun stoppedOnly_whenNoFollowedOrUnregistered() {
        val subtitle = registryCategorySubtitle(
            followed = 0,
            unregistered = 0,
            stopped = 2,
            emptyLabel = "empty",
            followedUnregisteredLabel = { _, _ -> "unregistered" },
            followedOnlyLabel = { "followed $it" },
            stoppedOnlyLabel = { "stopped $it" },
            followedStoppedLabel = { followed, stopped -> "$followed+$stopped" },
            stoppedSuffix = { " +$it" },
        )

        assertEquals("stopped 2", subtitle)
    }

    @Test
    fun followedAndStopped_whenBothPresent() {
        val subtitle = registryCategorySubtitle(
            followed = 2,
            unregistered = 0,
            stopped = 1,
            emptyLabel = "empty",
            followedUnregisteredLabel = { _, _ -> "unregistered" },
            followedOnlyLabel = { "followed $it" },
            stoppedOnlyLabel = { "stopped $it" },
            followedStoppedLabel = { followed, stopped -> "$followed+$stopped" },
            stoppedSuffix = { " +$it" },
        )

        assertEquals("2+1", subtitle)
    }

    @Test
    fun appendsStoppedSuffix_whenUnregisteredAndStopped() {
        val subtitle = registryCategorySubtitle(
            followed = 1,
            unregistered = 1,
            stopped = 2,
            emptyLabel = "empty",
            followedUnregisteredLabel = { followed, unregistered -> "$followed/$unregistered" },
            followedOnlyLabel = { "followed $it" },
            stoppedOnlyLabel = { "stopped $it" },
            followedStoppedLabel = { followed, stopped -> "$followed+$stopped" },
            stoppedSuffix = { " +$it" },
        )

        assertEquals("1/1 +2", subtitle)
    }
}
