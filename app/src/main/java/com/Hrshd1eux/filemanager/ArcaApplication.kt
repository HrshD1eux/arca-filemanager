package com.Hrshd1eux.filemanager

import android.app.Application

/**
 * ARCA Application class.
 * Foundation entry point for application-level lifecycle and future dependency injection / setup.
 */
class ArcaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // No analytics, no telemetry, no tracking initializers.
    }
}
