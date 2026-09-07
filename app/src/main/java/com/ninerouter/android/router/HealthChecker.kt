package com.ninerouter.android.router

import com.ninerouter.android.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Result of a single health probe. */
data class HealthResult(
    val healthy: Boolean,   // true when the HTTP server responded at all (listening)
    val ready: Boolean,     // true when /v1/models returned HTTP 200 (fully functional)
    val httpCode: Int,
    val latencyMs: Long,
    val error: String? = null,
)

/**
 * Polls the embedded instance to verify the server is up.
 *
 * 9Router exposes an OpenAI-compatible `/v1` API and a dashboard. We probe
 * `/v1/models`: any HTTP response proves the process is listening
 * (healthy), and a 2xx indicates it is fully wired up (ready).
 */
object HealthChecker {

    private const val TAG = "HealthChecker"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private suspend fun probe(baseUrl: String, apiKey: String): HealthResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val url = baseUrl.trimEnd('/') + "/models"
        val builder = Request.Builder().url(url).get()
            .header("Accept", "application/json")
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
            builder.header("X-Api-Key", apiKey)
        }
        try {
            client.newCall(builder.build()).execute().use { resp ->
                val code = resp.code
                val latency = System.currentTimeMillis() - start
                val healthy = code != 0
                val ready = code in 200..299
                if (!ready) AppLogger.d(TAG, "Probe $url -> HTTP $code (server alive)")
                HealthResult(healthy, ready, code, latency)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            AppLogger.d(TAG, "Probe $url failed: ${e.message}")
            HealthResult(false, false, 0, latency, e.message)
        }
    }

    /** Poll until healthy, up to [timeoutMs]. Returns the last result. */
    suspend fun waitUntilHealthy(
        baseUrl: String,
        apiKey: String,
        timeoutMs: Long = 30_000,
        intervalMs: Long = 700,
    ): HealthResult {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: HealthResult = HealthResult(false, false, 0, 0, "not checked")
        while (System.currentTimeMillis() < deadline) {
            delay(intervalMs)
            last = probe(baseUrl, apiKey)
            if (last.ready || last.healthy) return last
        }
        return last
    }

    /** Single synchronous probe used by re-verification. */
    suspend fun check(baseUrl: String, apiKey: String): HealthResult = probe(baseUrl, apiKey)

    /** Probe any absolute URL; falls back when the server is up but the API requires auth. */
    suspend fun check(url: String): HealthResult = probeUrl(url.trimEnd('/'))

    /** Poll until the server responds at the given URL. */
    suspend fun waitUntilHealthy(
        url: String,
        timeoutMs: Long,
        intervalMs: Long = 700,
    ): HealthResult {
        val u = url.trimEnd('/')
        val deadline = System.currentTimeMillis() + timeoutMs
        var last = check(u)
        while (System.currentTimeMillis() < deadline) {
            if (last.healthy) return last
            delay(intervalMs)
            last = check(u)
        }
        return last
    }

    private suspend fun probeUrl(url: String): HealthResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                val code = resp.code
                val latency = System.currentTimeMillis() - start
                AppLogger.d(TAG, "Probe $url -> HTTP $code")
                HealthResult(healthy = code != 0, ready = code in 200..299, httpCode = code, latencyMs = latency)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            AppLogger.d(TAG, "Probe $url failed: ${e.message}")
            HealthResult(false, false, 0, latency, e.message)
        }
    }
}
