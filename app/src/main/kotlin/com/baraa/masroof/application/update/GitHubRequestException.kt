package com.baraa.masroof.application.update

class GitHubRequestException(
    val httpCode: Int,
    val tokenWasProvided: Boolean,
    message: String,
) : Exception(message) {
    val requiresToken: Boolean
        get() = !tokenWasProvided && httpCode == 404

    companion object {
        private const val AUTH_REQUIRED_CODE = 404
    }
}

class PrivateRepoRequiresTokenException :
    Exception("GitHub token is required for private repositories")
