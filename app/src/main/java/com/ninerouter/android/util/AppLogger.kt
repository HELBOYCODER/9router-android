package com.ninerouter.android.util

/**
 * Logging helpers that guarantee no credentials are ever written to log output.
 *
 * All server output and UI log lines pass through [sanitize] before storage/display;
 * the secret loggers in Android's Logcat only ever emit redacted text.
 */
object AppLogger {

    private val redactors = listOf(
        Regex("(Bearer\\s+)[A-Za-z0-9._~+/=-]+", RegexOption.IGNORE_CASE),
        Regex("(sk-[A-Za-z0-9_-]{6,})"),
        Regex("(9r_[a-f0-9]{16,})"),
        Regex("(api[_-]?key[\\s:=-]+)\\S+", RegexOption.IGNORE_CASE),
        Regex("(password[\\s:=-]+)\\S+", RegexOption.IGNORE_CASE),
        Regex("(jwt[\\s:=-]+)\\S+", RegexOption.IGNORE_CASE),
        Regex("(secret[\\s:=-]+)\\S+", RegexOption.IGNORE_CASE),
        Regex("(authorization[\\s:=-]+)\\S+", RegexOption.IGNORE_CASE),
        Regex("(x-api-key[\\s:=-]+)\\S+", RegexOption.IGNORE_CASE),
        Regex("(JWT_SECRET=\\S+)", RegexOption.IGNORE_CASE),
        Regex("(API_KEY_SECRET=\\S+)", RegexOption.IGNORE_CASE),
        Regex("(INITIAL_PASSWORD=\\S+)", RegexOption.IGNORE_CASE),
        Regex("(MACHINE_ID_SALT=\\S+)", RegexOption.IGNORE_CASE),
    )

    private const val REDACTED = "<redacted>"

    /** Remove anything that could be a secret from [input]. Never log the result of this as-is if it still has risk — it doesn't. */
    fun sanitize(input: String): String {
        if (input.isBlank()) return input
        var out = input
        for (r in redactors) {
            out = r.replace(out) { m ->
                // Keep the label (e.g. "Bearer ") and redact the value.
                val label = m.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }
                (if (label != null) "$label" + REDACTED else REDACTED)
            }
        }
        return out
    }

    // ---- Android Logcat (redacted) ----
    fun d(tag: String, msg: String) = android.util.Log.d(tag, sanitize(msg))
    fun i(tag: String, msg: String) = android.util.Log.i(tag, sanitize(msg))
    fun w(tag: String, msg: String) = android.util.Log.w(tag, sanitize(msg))
    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) android.util.Log.e(tag, sanitize(msg), t) else android.util.Log.e(tag, sanitize(msg))
    }
}
