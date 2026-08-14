package com.baraa.masroof.presentation.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.application.update.ApkInstaller
import com.baraa.masroof.application.update.AppUpdateService
import com.baraa.masroof.application.update.GitHubReleaseClient
import com.baraa.masroof.application.update.GitHubTokenRepository
import com.baraa.masroof.application.update.UpdateChecker
import okhttp3.OkHttpClient

internal object SettingsViewModelTestFixtures {
    const val APP_VERSION: String = "0.2.0"

    fun appUpdateService(
        tokenConfigured: Boolean = false,
        token: String? = null,
    ): AppUpdateService {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tokenRepository =
            object : GitHubTokenRepository {
                private var storedToken: String? = token

                override fun getToken(): String? = storedToken

                override fun setToken(value: String) {
                    storedToken = value
                }

                override fun clearToken() {
                    storedToken = null
                }

                override fun hasToken(): Boolean =
                    if (tokenConfigured) true else storedToken != null
            }
        return AppUpdateService(
            context = context,
            tokenRepository = tokenRepository,
            releaseClient = GitHubReleaseClient(OkHttpClient(), "bakhshb", "Masroof"),
            updateChecker = UpdateChecker(installedVersionCode = 3),
        )
    }

    fun apkInstaller(): ApkInstaller =
        ApkInstaller(ApplicationProvider.getApplicationContext())
}
