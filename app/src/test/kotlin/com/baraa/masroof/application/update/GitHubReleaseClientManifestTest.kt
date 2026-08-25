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
    private lateinit var apiBaseUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        releaseBaseUrl = server.url("/").toString().removeSuffix("/")
        apiBaseUrl = releaseBaseUrl
        client =
            GitHubReleaseClient(
                httpClient = OkHttpClient.Builder().build(),
                owner = "o",
                repo = "r",
                releaseBaseUrl = releaseBaseUrl,
                apiBaseUrl = apiBaseUrl,
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
        enqueueManifest(
            versionCode = 15,
            versionName = "0.2.12-nightly-3",
            channel = "nightly",
            releaseTag = "v0.2.12-nightly-3",
        )

        val result = client.findBestManifest(UpdateChannel.NIGHTLY, installedVersionCode = 11, token = "ghp_test")

        assertTrue(result.isSuccess)
        assertEquals(15, result.getOrThrow()?.versionCode)
        assertEquals("v0.2.12-nightly-3", result.getOrThrow()?.resolvedReleaseTag())
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

        val destination = createTempFile("masroof", ".apk")
        val result = client.downloadReleaseAsset("ghp_test", manifest, destination)

        assertTrue(result.isSuccess)
        assertEquals("/repos/o/r/releases/tags/nightly", server.takeRequest().path)
        assertEquals("/apk-asset", server.takeRequest().path)
        destination.delete()
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
