package com.baraa.masroof

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import com.baraa.masroof.application.AppContainer
import com.baraa.masroof.application.update.UpdateCheckScheduler
import com.baraa.masroof.presentation.locale.AppLocaleContext

/**
 * Application entry and composition root holder for Masroof.
 */
class MasroofApplication : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(
            AppLocaleContext.wrap(base, AppLocaleContext.readStoredLanguageTag(base)),
        )
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.runStartupMaintenance()
        UpdateCheckScheduler.schedule(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onTerminate() {
        if (::container.isInitialized) {
            container.close()
        }
        super.onTerminate()
    }
}
