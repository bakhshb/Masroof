package com.baraa.masroof.application.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateManifestSelectorTest {
    private val stableManifest =
        UpdateManifest(
            versionCode = 10,
            versionName = "0.2.10",
            apkFileName = "masroof-0.2.10.apk",
            sha256 = "abc",
            channel = "stable",
            releaseTag = "v0.2.10",
        )

    private val nightlyManifest =
        UpdateManifest(
            versionCode = 11,
            versionName = "0.2.10-nightly-1",
            apkFileName = "masroof-0.2.10-nightly-1.apk",
            sha256 = "def",
            channel = "nightly",
            releaseTag = "v0.2.10-nightly-1",
        )

    @Test
    fun stableChannel_ignoresNightlyBuilds() {
        val selected =
            UpdateManifestSelector.bestForChannel(
                channel = UpdateChannel.STABLE,
                installedVersionCode = 9,
                manifests = listOf(stableManifest, nightlyManifest),
            )

        assertEquals(stableManifest, selected)
    }

    @Test
    fun nightlyChannel_picksHighestVersionCode() {
        val selected =
            UpdateManifestSelector.bestForChannel(
                channel = UpdateChannel.NIGHTLY,
                installedVersionCode = 9,
                manifests = listOf(stableManifest, nightlyManifest),
            )

        assertEquals(nightlyManifest, selected)
    }

    @Test
    fun returnsNullWhenInstalledVersionIsCurrent() {
        val selected =
            UpdateManifestSelector.bestForChannel(
                channel = UpdateChannel.NIGHTLY,
                installedVersionCode = 11,
                manifests = listOf(stableManifest, nightlyManifest),
            )

        assertNull(selected)
    }

    @Test
    fun legacyManifestWithoutChannel_isTreatedAsStable() {
        val legacy =
            UpdateManifest(
                versionCode = 12,
                versionName = "0.2.12",
                apkFileName = "masroof-0.2.12.apk",
                sha256 = "ghi",
            )

        val selected =
            UpdateManifestSelector.bestForChannel(
                channel = UpdateChannel.STABLE,
                installedVersionCode = 11,
                manifests = listOf(legacy),
            )

        assertEquals(legacy, selected)
    }
}
