package com.ninerouter.android.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/** URL / network helpers for building and exposing the local router addresses. */
object NetworkUtils {

    const val DEFAULT_HOST = "127.0.0.1"
    const val DEFAULT_PORT = 20128

    fun apiBaseUrl(host: String, port: Int): String = "http://$host:$port/v1"
    fun dashboardUrl(host: String, port: Int): String = "http://$host:$port/dashboard"

    fun normalizeHost(host: String?): String = host?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_HOST

    /** True when the configured host binds all interfaces (LAN mode). */
    fun isLanMode(bindHost: String): Boolean = bindHost == "0.0.0.0" || bindHost == "::"

    /**
     * Best-effort IPv4 address of this device on the active network. Used to present
     * the LAN-accessible endpoint when LAN mode is enabled. Returns null when offline.
     */
    fun localIpv4Address(context: Context): String? {
        var fallback: String? = null
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in Collections.list(nif.inetAddresses)) {
                    if (addr is Inet4Address) {
                        val i = addr.hostAddress ?: continue
                        fallback = fallback ?: i
                        // Prefer wlan/eth addresses over other interfaces.
                        val name = nif.name.lowercase()
                        if (name.startsWith("wlan") || name.startsWith("eth") ||
                            name.startsWith("wifi")
                        ) {
                            return i
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore; fall back to null
        }
        return fallback
    }
}
