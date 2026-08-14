package com.baraa.masroof.application.update

interface GitHubTokenRepository {
    fun getToken(): String?

    fun setToken(token: String)

    fun clearToken()

    fun hasToken(): Boolean
}
