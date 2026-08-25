package com.baraa.masroof.application.update

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class GitHubReleaseClientManifestTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitHubReleaseClient
    private lateinit var apiBaseUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        apiBaseUrl = server.url("/").toString().removeSuffix("/")
        client =
            GitHubReleaseClient(
                httpClient = OkHttpClient.Builder().build(),
                owner = "o",
                repo = "r",
                apiBaseUrl = apiBaseUrl,
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun stableChannel_fetchesLatestReleaseViaApi() {
        enqueueRelease(
            tagName = "v0.2.12",
            versionCode = 12,
            versionName = "0.2.12",
            channel = "stable",
            releaseTag = "v0.2.12",
        )

        val result = client.findBestManifest(UpdateChannel.STABLE, installedVersionCode = 11, token = "ghp_test")

        assertTrue(result.isSuccess)
        assertEquals(12, result.getOrThrow()?.versionCode)
        assertEquals("/repos/o/r/releases/latest", server.takeRequest().path)
    }

    @Test
    fun nightlyChannel_fetchesRollingNightlyAndLatestStable() {
        enqueueRelease(
            tagName = "nightly",
            versionCode = 13,
            versionName = "0.2.12-nightly-1",
            channel = "nightly",
            releaseTag = "nightly",
        )
        enqueueRelease(
            tagName = "v0.2.12",
            versionCode = 12,
            versionName = "0.2.12",
            channel = "stable",
            releaseTag = "v0.2.12",
        )

        val result = client.findBestManifest(UpdateChannel.NIGHTLY, installedVersionCode = 11, token = "ghp_test")

        assertTrue(result.isSuccess)
        assertEquals(13, result.getOrThrow()?.versionCode)
        val paths = (0 until server.requestCount).map { server.takeRequest().path }
        assertTrue(paths.contains("/repos/o/r/releases/tags/nightly"))
        assertTrue(paths.contains("/repos/o/r/releases/latest"))
    }

    @Test
    fun nightlyChannel_prefersStableWhenVersionCodeTies() {
        enqueueRelease(
            tagName = "nightly",
            versionCode = 12,
            versionName = "0.2.12-nightly-2",
            channel = "nightly",
            releaseTag = "nightly",
        )
        enqueueRelease(
            tagName = "v0.2.12",
            versionCode = 12,
            versionName = "0.2.12",
            channel = "stable",
            releaseTag = "v0.2.12",
        )

        val result = client.findBestManifest(UpdateChannel.NIGHTLY, installedVersionCode = 11, token = "ghp_test")

        assertEquals("stable", result.getOrThrow()?.normalizedChannel)
    }

    @Test
    fun stableChannel_failsWhenLatestReleaseMissing() {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = client.findBestManifest(UpdateChannel.STABLE, installedVersionCode = 1, token = "ghp_test")

        assertTrue(result.isFailure)
    }

    @Test
    fun nightlyChannel_continuesWhenLatestStableMissing() {
        enqueueRelease(
            tagName = "nightly",
            versionCode = 13,
            versionName = "0.2.12-nightly-1",
            channel = "nightly",
            releaseTag = "nightly",
        )
        server.enqueue(MockResponse().setResponseCode(404))

        val result = client.findBestManifest(UpdateChannel.NIGHTLY, installedVersionCode = 11, token = "ghp_test")

        assertTrue(result.isSuccess)
        assertEquals(13, result.getOrThrow()?.versionCode)
    }

    @Test
    fun nightlyChannel_fallsBackToImmutableNightlyWhenRollingMissing() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    [
                      {
                        "tag_name": "v0.2.12-nightly-3",
                        "assets": [
                          { "name": "version.json", "url": "$apiBaseUrl/version-asset" }
                        ]
                      }
                    ]
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "application/json"),
        )
        enqueueManifestBody(
            versionCode = 15,
            versionName = "0.2.12-nightly-3",
            channel = "nightly",
            releaseTag = null,
        )

        val result = client.findBestManifest(UpdateChannel.NIGHTLY, installedVersionCode = 11, token = "ghp_test")

        assertTrue(result.isSuccess)
        assertEquals(15, result.getOrThrow()?.versionCode)
        assertEquals("v0.2.12-nightly-3", result.getOrThrow()?.resolvedReleaseTag())
    }

    @Test
    fun nightlyChannel_returnsUpToDateWhenNoNewerManifestsFound() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(
            MockResponse()
                .setBody("[]")
                .addHeader("Content-Type", "application/json"),
        )

        val result = client.findBestManifest(UpdateChannel.NIGHTLY, installedVersionCode = 20, token = "ghp_test")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun legacyManifestWithoutReleaseTag_usesReleaseTagNameForDownload() {
        enqueueRelease(
            tagName = "v0.2.10",
            versionCode = 10,
            versionName = "0.2.10",
            channel = "stable",
            releaseTag = null,
        )

        val result = client.findBestManifest(UpdateChannel.STABLE, installedVersionCode = 9, token = "ghp_test")

        assertEquals("v0.2.10", result.getOrThrow()?.resolvedReleaseTag())
    }

    @Test
    fun returnsNullWhenAlreadyUpToDate() {
        enqueueRelease(
            tagName = "v0.2.10",
            versionCode = 10,
            versionName = "0.2.10",
            channel = "stable",
            releaseTag = "v0.2.10",
        )

        val result = client.findBestManifest(UpdateChannel.STABLE, installedVersionCode = 10, token = "ghp_test")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun normalizeReleaseTagForApi_preservesRollingNightlyTag() {
        assertEquals("nightly", GitHubReleaseClient.normalizeReleaseTagForApi("nightly"))
        assertEquals("v0.2.12", GitHubReleaseClient.normalizeReleaseTagForApi("0.2.12"))
        assertEquals("v0.2.12-nightly-1", GitHubReleaseClient.normalizeReleaseTagForApi("v0.2.12-nightly-1"))
    }

    @Test
    fun downloadReleaseAsset_usesNightlyTagWithoutVPrefix() {
        val manifest =
            UpdateManifest(
                versionCode = 13,
                versionName = "0.2.12-nightly-1",
                apkFileName = "masroof-0.2.12-nightly-1.apk",
                sha256 = "abc",
                channel = "nightly",
                releaseTag = "nightly",
            )
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "tag_name": "nightly",
                      "assets": [
                        { "name": "masroof-0.2.12-nightly-1.apk", "url": "$apiBaseUrl/apk-asset" }
                      ]
                    }
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "application/json"),
        )
        server.enqueue(MockResponse().setBody("apk-bytes").setResponseCode(200))

        val destination = File.createTempFile("masroof", ".apk")
        val result = client.downloadReleaseAsset("ghp_test", manifest, destination)

        assertTrue(result.isSuccess)
        assertEquals("/repos/o/r/releases/tags/nightly", server.takeRequest().path)
        assertEquals("/apk-asset", server.takeRequest().path)
        destination.delete()
    }

    private fun enqueueRelease(
        tagName: String,
        versionCode: Int,
        versionName: String,
        channel: String,
        releaseTag: String?,
    ) {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "tag_name": "$tagName",
                      "assets": [
                        { "name": "version.json", "url": "$apiBaseUrl/manifest-asset" }
                      ]
                    }
                    """.trimIndent(),
                )
                .addHeader("Content-Type", "application/json"),
        )
        enqueueManifestBody(versionCode, versionName, channel, releaseTag)
    }

    private fun enqueueManifestBody(
        versionCode: Int,
        versionName: String,
        channel: String,
        releaseTag: String?,
    ) {
        val releaseTagField =
            if (releaseTag == null) {
                ""
            } else {
                ""","releaseTag": "$releaseTag""""
            }
        val body =
            """
            {
              "versionCode": $versionCode,
              "versionName": "$versionName",
              "apkFileName": "masroof-$versionName.apk",
              "sha256": "abc",
              "channel": "$channel"$releaseTagField
            }
            """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))
    }
}
