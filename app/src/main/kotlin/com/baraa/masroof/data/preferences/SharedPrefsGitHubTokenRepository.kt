package com.baraa.masroof.data.preferences

import android.content.SharedPreferences
import com.baraa.masroof.application.update.GitHubTokenRepository

class SharedPrefsGitHubTokenRepository(
    private val prefs: SharedPreferences,
) : GitHubTokenRepository {
    override fun getToken(): String? =
        prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    override fun setToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    override fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    override fun hasToken(): Boolean = getToken() != null

    companion object {
        const val PREFS_NAME: String = "github_token_prefs"
        const val KEY_TOKEN: String = "github_token"
    }
}
