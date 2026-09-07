package com.ninerouter.android

import android.app.Application
import com.ninerouter.android.router.RouterManager
import com.ninerouter.android.service.RouterForegroundService
import com.ninerouter.android.util.AppLogger

/**
 * Application entry point. Initializes the router manager and, if CONNECT was left
 * enabled when the process was last killed, attempts to recover the persistent
 * foreground service so the local instance comes back without user action.
 */
class NineRouterApp : Application() {

    override fun onCreate() {
        super.onCreate()
        RouterManager.initialize(this)

        if (RouterForegroundService.wasRunning(this)) {
            runCatching { RouterForegroundService.start(this) }
                .onFailure { AppLogger.w("NineRouterApp", "Could not auto-resume service: ${it.message}") }
        }
    }
}
