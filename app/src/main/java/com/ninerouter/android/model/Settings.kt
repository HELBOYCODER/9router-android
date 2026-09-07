package com.ninerouter.android.model

/** User-configurable settings for the local instance. */
data class Settings(
    val host: String = "127.0.0.1",
    val port: Int = 20128,
    val lanMode: Boolean = false,
    val requireApiKey: Boolean = false,
    val autoRestart: Boolean = true,
    val maxRestartAttempts: Int = 5,
    val requestLogEnabled: Boolean = false,
    val openDashboardInExternalBrowser: Boolean = false,
) {
    val bindHost: String get() = if (lanMode) "0.0.0.0" else "127.0.0.1"
}
