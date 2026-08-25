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

class GitHubReleaseClientManifestTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitHubReleaseClient
    private lateinit var releaseBaseUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        releaseBaseUrl = server.url("/").toString().removeSuffix("/")
        client =
            GitHubReleaseClient(
                httpClient = OkHttpClient.Builder().build(),
                owner = "o",
                repo = "r",
                releaseBaseUrl = releaseBaseUrl,
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun stableChannel_fetchesLatestManifestOnly() {
        enqueueManifest(
            versionCode = 12,
            versionName = "0.2.12",
            channel = "stable",
            releaseTag = "v0.2.12",
        )

        val result = client.findBestManifest(UpdateChannel.STABLE, installedVersionCode = 11, token = "ghp_test")

        assertTrue(result.isSuccess)
        assertEquals(12, result.getOrThrow()?.versionCode)
        assertEquals(1, server.requestCount)
        assertEquals(
            "/o/r/releases/latest/download/version.json",
            server.takeRequest().path,
        )
    }

    @Test
    fun nightlyChannel_fetchesRollingNightlyAndLatestStable() {
        enqueueManifest(
            versionCode = 13,
            versionName = "0.2.12-nightly-1",
            channel = "nightly",
            releaseTag = "nightly",
        )
        enqueueManifest(
            versionCode = 12,
            versionName = "0.2.12",
            channel = "stable",
            releaseTag = "v0.2.12",
        )

        val result = client.findBestManifest(UpdateChannel.NIGHTLY, installedVersionCode = 11, token = "ghp_test")

        assertTrue(result.isSuccess)
        assertEquals(13, result.getOrThrow()?.versionCode)
        assertEquals(2, server.requestCount)
        assertEquals(
            "/o/r/releases/download/nightly/version.json",
            server.takeRequest().path,
        )
        assertEquals(
            "/o/r/releases/latest/download/version.json",
            server.takeRequest().path,
        )
    }

    @Test
    fun nightlyChannel_prefersStableWhenVersionCodeTies() {
        enqueueManifest(
            versionCode = 12,
            versionName = "0.2.12-nightly-2",
            channel = "nightly",
            releaseTag = "nightly",
        )
        enqueueManifest(
            versionCode = 12,
            versionName = "0.2.12",
            channel = "stable",
            releaseTag = "v0.2.12",
        )

        val result = client.findBestManifest(UpdateChannel.NIGHTLY, installedVersionCode = 11, token = "ghp_test")

        assertEquals("stable", result.getOrThrow()?.normalizedChannel)
    }

    @Test
    fun stableChannel_failsWhenLatestManifestMissing() {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = client.findBestManifest(UpdateChannel.STABLE, installedVersionCode = 1, token = "ghp_test")

        assertTrue(result.isFailure)
    }

    @Test
    fun nightlyChannel_continuesWhenLatestStableMissing() {
        enqueueManifest(
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
    fun returnsNullWhenAlreadyUpToDate() {
        enqueueManifest(
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
    fun manifestUrls_useConfiguredBase() {
        assertEquals(
            "$releaseBaseUrl/o/r/releases/latest/download/version.json",
            GitHubReleaseClient.stableManifestDownloadUrl("o", "r", releaseBaseUrl),
        )
        assertEquals(
            "$releaseBaseUrl/o/r/releases/download/nightly/version.json",
            GitHubReleaseClient.nightlyManifestDownloadUrl("o", "r", releaseBaseUrl),
        )
    }

    private fun enqueueManifest(
        versionCode: Int,
        versionName: String,
        channel: String,
        releaseTag: String,
    ) {
        val body =
            """
            {
              "versionCode": $versionCode,
              "versionName": "$versionName",
              "apkFileName": "masroof-$versionName.apk",
              "sha256": "abc",
              "channel": "$channel",
              "releaseTag": "$releaseTag"
            }
            """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))
    }
}
