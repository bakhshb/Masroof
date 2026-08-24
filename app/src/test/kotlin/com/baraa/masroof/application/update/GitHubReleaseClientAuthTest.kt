package com.baraa.masroof.application.update

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubReleaseClientAuthTest {
    private val client = GitHubReleaseClient(owner = "o", repo = "r")

    @Test
    fun authorizationHeader_classicPat_usesTokenPrefix() {
        assertEquals("token ghp_abc123", client.authorizationHeader("ghp_abc123"))
    }

    @Test
    fun authorizationHeader_fineGrainedPat_usesBearerPrefix() {
        assertEquals("Bearer github_pat_abc123", client.authorizationHeader("github_pat_abc123"))
    }

    @Test
    fun authorizedRequest_withoutToken_omitsAuthorizationHeader() {
        val request = client.buildAuthorizedRequestForTest(null)
            .url("https://api.github.com/repos/o/r/releases/latest")
            .get()
            .build()

        assertEquals(null, request.header("Authorization"))
    }

    @Test
    fun authorizedRequest_withToken_includesAuthorizationHeader() {
        val request = client.buildAuthorizedRequestForTest("ghp_abc123")
            .url("https://api.github.com/repos/o/r/releases/latest")
            .get()
            .build()

        assertEquals("token ghp_abc123", request.header("Authorization"))
    }
}
