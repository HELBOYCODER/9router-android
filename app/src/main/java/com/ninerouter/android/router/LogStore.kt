package com.ninerouter.android.router

import com.ninerouter.android.model.LogEntry
import com.ninerouter.android.model.LogLevel
import com.ninerouter.android.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Bounded, in-memory log buffer for the local instance. Every line is sanitized
 * ([AppLogger.sanitize]) before it is stored so passwords/API keys never appear
 * in the UI or exports.
 */
object LogStore {

    private const val MAX_ENTRIES = 500

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    private val idGen = AtomicLong(0)

    fun add(level: LogLevel, tag: String, rawMessage: String) {
        val safe = AppLogger.sanitize(rawMessage)
        val entry = LogEntry(idGen.incrementAndGet(), System.currentTimeMillis(), level, tag, safe)
        val next = (_entries.value + entry).takeLast(MAX_ENTRIES)
        _entries.value = next
    }

    fun info(tag: String, msg: String) = add(LogLevel.INFO, tag, msg)
    fun warn(tag: String, msg: String) = add(LogLevel.WARN, tag, msg)
    fun error(tag: String, msg: String) = add(LogLevel.ERROR, tag, msg)
    fun debug(tag: String, msg: String) = add(LogLevel.DEBUG, tag, msg)

    fun clear() {
        _entries.value = emptyList()
    }

    fun snapshot(): List<LogEntry> = _entries.value

    fun exportText(): String =
        _entries.value.joinToString("\n") { e ->
            val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(e.timestamp))
            "[$ts][${e.level.name}][${e.tag}] ${e.message}"
        }
}
