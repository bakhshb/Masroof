package com.baraa.masroof.application.update

class GitHubRequestException(
    val httpCode: Int,
    val tokenWasProvided: Boolean,
    message: String,
) : Exception(message) {
    val requiresToken: Boolean
        get() = !tokenWasProvided && httpCode in AUTH_REQUIRED_CODES

    companion object {
        private val AUTH_REQUIRED_CODES = setOf(401, 403, 404)
    }
}

class PrivateRepoRequiresTokenException :
    Exception("GitHub token is required for private repositories")
