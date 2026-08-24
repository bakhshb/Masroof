package com.baraa.masroof.application.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubRequestExceptionTest {
    @Test
    fun requiresToken_whenUnauthorizedWithoutToken() {
        val error = GitHubRequestException(
            httpCode = 401,
            tokenWasProvided = false,
            message = "auth failed",
        )
        assertTrue(error.requiresToken)
    }

    @Test
    fun doesNotRequireToken_whenUnauthorizedWithToken() {
        val error = GitHubRequestException(
            httpCode = 401,
            tokenWasProvided = true,
            message = "auth failed",
        )
        assertFalse(error.requiresToken)
    }

    @Test
    fun requiresToken_whenNotFoundWithoutToken() {
        val error = GitHubRequestException(
            httpCode = 404,
            tokenWasProvided = false,
            message = "not found",
        )
        assertTrue(error.requiresToken)
    }
}
