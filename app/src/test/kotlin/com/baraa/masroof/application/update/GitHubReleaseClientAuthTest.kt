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
}
