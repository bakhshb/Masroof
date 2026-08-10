package com.baraa.masroof

import android.app.Application
import com.baraa.masroof.application.AppContainer

/**
 * Application entry and composition root holder for Masroof.
 */
class MasroofApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTerminate() {
        if (::container.isInitialized) {
            container.close()
        }
        super.onTerminate()
    }
}
