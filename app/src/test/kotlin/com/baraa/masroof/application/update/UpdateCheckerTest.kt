package com.baraa.masroof.application.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private val checker = UpdateChecker(installedVersionCode = 4)

    @Test
    fun newerVersionCode_isAvailable() {
        val manifest =
            UpdateManifest(
                versionCode = 5,
                versionName = "0.2.1",
                apkFileName = "masroof-0.3.0.apk",
                sha256 = "abc",
            )
        assertTrue(checker.isUpdateAvailable(manifest))
        val availability = checker.evaluate(manifest)
        assertTrue(availability is UpdateAvailability.Available)
    }

    @Test
    fun sameVersionCode_isUpToDate() {
        val manifest =
            UpdateManifest(
                versionCode = 4,
                versionName = "0.2.1",
                apkFileName = "masroof-0.2.1.apk",
                sha256 = "abc",
            )
        assertFalse(checker.isUpdateAvailable(manifest))
        assertTrue(checker.evaluate(manifest) is UpdateAvailability.UpToDate)
    }
}
