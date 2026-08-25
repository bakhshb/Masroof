package com.baraa.masroof.application.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateManifestTest {
    @Test
    fun resolvedReleaseTag_usesExplicitTagWhenPresent() {
        val manifest =
            UpdateManifest(
                versionCode = 10,
                versionName = "0.2.10",
                apkFileName = "masroof-0.2.10.apk",
                sha256 = "abc",
                releaseTag = "v0.2.10-nightly-1",
            )

        assertEquals("v0.2.10-nightly-1", manifest.resolvedReleaseTag())
    }

    @Test
    fun resolvedReleaseTag_fallsBackToVersionName() {
        val manifest =
            UpdateManifest(
                versionCode = 10,
                versionName = "0.2.10",
                apkFileName = "masroof-0.2.10.apk",
                sha256 = "abc",
            )

        assertEquals("v0.2.10", manifest.resolvedReleaseTag())
    }

    @Test
    fun resolvedReleaseTag_preservesLeadingVInVersionName() {
        val manifest =
            UpdateManifest(
                versionCode = 10,
                versionName = "v0.2.10",
                apkFileName = "masroof-0.2.10.apk",
                sha256 = "abc",
            )

        assertEquals("v0.2.10", manifest.resolvedReleaseTag())
    }
}
