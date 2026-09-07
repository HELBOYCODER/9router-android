package com.ninerouter.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ninerouter.android.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * DataStore-backed repository for non-secret runtime settings. Secrets (API keys,
 * passwords, tokens) are *never* stored here — they live in [com.ninerouter.android.security.SecureStorage].
 */
class SettingsRepository private constructor(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "router_settings")

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val LAN_MODE = booleanPreferencesKey("lan_mode")
        val REQUIRE_API_KEY = booleanPreferencesKey("require_api_key")
        val AUTO_RESTART = booleanPreferencesKey("auto_restart")
        val MAX_RESTART_ATTEMPTS = intPreferencesKey("max_restart_attempts")
        val REQUEST_LOG_ENABLED = booleanPreferencesKey("request_log_enabled")
        val EXTERNAL_BROWSER = booleanPreferencesKey("external_browser")
    }

    val settings: Flow<Settings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e }
        .map { p ->
            Settings(
                host = p[Keys.HOST] ?: "127.0.0.1",
                port = p[Keys.PORT] ?: 20128,
                lanMode = p[Keys.LAN_MODE] ?: false,
                requireApiKey = p[Keys.REQUIRE_API_KEY] ?: false,
                autoRestart = p[Keys.AUTO_RESTART] ?: true,
                maxRestartAttempts = p[Keys.MAX_RESTART_ATTEMPTS] ?: 5,
                requestLogEnabled = p[Keys.REQUEST_LOG_ENABLED] ?: false,
                openDashboardInExternalBrowser = p[Keys.EXTERNAL_BROWSER] ?: false,
            )
        }

    suspend fun save(settings: Settings) {
        context.dataStore.edit { p ->
            p[Keys.HOST] = settings.host
            p[Keys.PORT] = settings.port
            p[Keys.LAN_MODE] = settings.lanMode
            p[Keys.REQUIRE_API_KEY] = settings.requireApiKey
            p[Keys.AUTO_RESTART] = settings.autoRestart
            p[Keys.MAX_RESTART_ATTEMPTS] = settings.maxRestartAttempts
            p[Keys.REQUEST_LOG_ENABLED] = settings.requestLogEnabled
            p[Keys.EXTERNAL_BROWSER] = settings.openDashboardInExternalBrowser
        }
    }
}
