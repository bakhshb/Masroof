package com.baraa.masroof.application.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateManifestSelectorTest {
    @Test
    fun highestVersionCode_picksHighestAcrossStableAndNightly() {
        val stable = UpdateManifest(
            versionCode = 70,
            versionName = "0.3.17",
            apkFileName = "masroof-0.3.17.apk",
            sha256 = "a",
        )
        val nightly = UpdateManifest(
            versionCode = 72,
            versionName = "0.3.18-nightly.2",
            apkFileName = "masroof-0.3.18-nightly.2.apk",
            sha256 = "b",
        )

        val selected = UpdateManifestSelector.highestVersionCode(listOf(stable, nightly))

        assertEquals(nightly, selected)
    }

    @Test
    fun highestVersionCode_returnsNullForEmptyInput() {
        assertNull(UpdateManifestSelector.highestVersionCode(emptyList()))
    }
}
