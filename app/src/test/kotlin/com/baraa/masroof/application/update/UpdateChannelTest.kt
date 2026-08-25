package com.baraa.masroof.application.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateChannelTest {
    @Test
    fun fromStorage_defaultsToStable() {
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromStorage(null))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromStorage("stable"))
        assertEquals(UpdateChannel.NIGHTLY, UpdateChannel.fromStorage("nightly"))
    }

    @Test
    fun stableChannel_acceptsOnlyStableManifests() {
        assertTrue(UpdateChannel.STABLE.acceptsManifestChannel("stable"))
        assertFalse(UpdateChannel.STABLE.acceptsManifestChannel("nightly"))
    }

    @Test
    fun nightlyChannel_acceptsStableAndNightlyManifests() {
        assertTrue(UpdateChannel.NIGHTLY.acceptsManifestChannel("stable"))
        assertTrue(UpdateChannel.NIGHTLY.acceptsManifestChannel("nightly"))
    }
}
