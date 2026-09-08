package com.ninerouter.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ninerouter.android.data.SettingsRepository
import com.ninerouter.android.model.LogEntry
import com.ninerouter.android.model.RouterStatus
import com.ninerouter.android.model.Settings
import com.ninerouter.android.router.RouterManager
import com.ninerouter.android.service.RouterForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Bridges the UI to [RouterManager] / the foreground service. All connection
 * operations go through the service so the persistent notification stays in sync.
 */
class RouterViewModel(app: Application) : AndroidViewModel(app) {

    val state: StateFlow<RouterStatus> = RouterManager.state
    val logs: StateFlow<List<LogEntry>> = RouterManager.logs

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    private val repo by lazy { SettingsRepository.get(getApplication()) }

    init {
        viewModelScope.launch {
            repo.settings.collect { _settings.value = it }
        }
    }

    /** Snapshot of live settings (used before configuring a start). */
    fun settingsSnapshot(): Settings = _settings.value

    // ---- Connection controls (delegated to the foreground service) ----
    fun installPrerequisites() = RouterManager.installPrerequisites(getApplication())
    fun connect() = RouterForegroundService.start(getApplication())
    fun disconnect() = RouterForegroundService.stop(getApplication())
    fun restart() = RouterForegroundService.restart(getApplication())
    fun reinstall() = RouterManager.reinstall(getApplication())
    fun reinitialize() = RouterForegroundService.reinit(getApplication())

    /** Wipe all application-owned runtime data (instances + extracted runtime). */
    fun clearAllRuntimeData() = RouterManager.clearRuntimeData(getApplication())

    fun clearLogs() = RouterManager.clearLogs()

    fun saveSettings(newSettings: Settings) {
        _settings.value = newSettings
        viewModelScope.launch { repo.save(newSettings) }
    }

    fun copyText(text: String): String = text
}
