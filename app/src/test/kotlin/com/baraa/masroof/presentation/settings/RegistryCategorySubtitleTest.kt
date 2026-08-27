package com.baraa.masroof.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryCategorySubtitleTest {
    @Test
    fun stoppedOnly_whenNoFollowedOrUnregistered() {
        val state = resolveRegistryCategorySubtitle(
            followed = 0,
            unregistered = 0,
            stopped = 2,
        )

        assertTrue(state is RegistryCategorySubtitleState.StoppedOnly)
        assertEquals(2, (state as RegistryCategorySubtitleState.StoppedOnly).stopped)
    }

    @Test
    fun followedAndStopped_whenBothPresent() {
        val state = resolveRegistryCategorySubtitle(
            followed = 2,
            unregistered = 0,
            stopped = 1,
        )

        assertEquals(
            RegistryCategorySubtitleState.FollowedStopped(followed = 2, stopped = 1),
            state,
        )
    }

    @Test
    fun includesStopped_whenUnregisteredAndStopped() {
        val state = resolveRegistryCategorySubtitle(
            followed = 1,
            unregistered = 1,
            stopped = 2,
        )

        assertEquals(
            RegistryCategorySubtitleState.FollowedUnregistered(
                followed = 1,
                unregistered = 1,
                stopped = 2,
            ),
            state,
        )
    }
}
