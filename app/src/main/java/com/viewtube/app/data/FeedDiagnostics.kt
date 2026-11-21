package com.viewtube.app.data

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-only feed telemetry. Captures enough detail to identify which Home/Shorts
 * source failed without collecting account data, video history, or credentials.
 */
object FeedDiagnostics {
    private const val PREFS = "viewtube_feed_diagnostics"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 100
    private val lock = Any()

    fun record(
        feed: String,
        stage: String,
        outcome: String,
        durationMs: Long? = null,
        itemCount: Int? = null,
        error: Throwable? = null,
        detail: String? = null,
    ) {
        runCatching {
            val prefs = Graph.appContext.getSharedPreferences(PREFS, 0)
            synchronized(lock) {
                val events = runCatching { JSONArray(prefs.getString(KEY_EVENTS, "[]")) }.getOrDefault(JSONArray())
                val event = JSONObject()
                    .put("time", System.currentTimeMillis())
                    .put("feed", feed.take(24))
                    .put("stage", stage.take(80))
                    .put("outcome", outcome.take(32))
                durationMs?.let { event.put("durationMs", it.coerceAtLeast(0)) }
                itemCount?.let { event.put("itemCount", it.coerceAtLeast(0)) }
                detail?.let { event.put("detail", sanitize(it).take(300)) }
                error?.let {
                    event.put("exception", it.javaClass.name)
                    event.put("message", sanitize(it.message.orEmpty()).take(500))
                    val stack = it.stackTraceToString().lineSequence().take(14).joinToString("\n")
                    event.put("stack", sanitize(stack).take(3500))
                }
                events.put(event)
                val trimmed = JSONArray()
                for (i in (events.length() - MAX_EVENTS).coerceAtLeast(0) until events.length()) {
                    trimmed.put(events.getJSONObject(i))
                }
                prefs.edit().putString(KEY_EVENTS, trimmed.toString()).apply()
            }
        }
    }

    fun report(): String {
        val prefs = Graph.appContext.getSharedPreferences(PREFS, 0)
        val events = runCatching { JSONArray(prefs.getString(KEY_EVENTS, "[]")) }.getOrDefault(JSONArray())
        val version = runCatching {
            @Suppress("DEPRECATION")
            Graph.appContext.packageManager.getPackageInfo(Graph.appContext.packageName, 0).versionName ?: "unknown"
        }.getOrDefault("unknown")
        val out = StringBuilder()
            .appendLine("VIEWTUBE HOME & SHORTS FEED DIAGNOSTIC REPORT")
            .appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())}")
            .appendLine("App version: $version")
            .appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            .appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            .appendLine("Events recorded: ${events.length()}")
            .appendLine("Data is stored locally; this report is copied only when requested.")
            .appendLine()
        for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            val time = runCatching {
                SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(e.optLong("time")))
            }.getOrDefault("?")
            out.append("[$time] ${e.optString("feed")} / ${e.optString("stage")}: ${e.optString("outcome")}")
            if (e.has("durationMs")) out.append(" · ${e.optLong("durationMs")} ms")
            if (e.has("itemCount")) out.append(" · ${e.optInt("itemCount")} items")
            out.appendLine()
            if (e.has("detail")) out.appendLine("  Detail: ${e.optString("detail")}")
            if (e.has("exception")) {
                out.appendLine("  Exception: ${e.optString("exception")}: ${e.optString("message")}")
                out.appendLine(e.optString("stack"))
            }
        }
        if (events.length() == 0) out.appendLine("No feed diagnostics recorded yet. Open Home and Shorts to capture a fresh attempt.")
        return out.toString()
    }

    fun clear() {
        Graph.appContext.getSharedPreferences(PREFS, 0).edit().remove(KEY_EVENTS).apply()
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("(?i)(authorization|cookie|token|api[_-]?key|password)=?[^\\s,;]*"), "${'
        .replace(Regex("https?://[^\\s]+"), "[URL]")
}
}1=[REDACTED]")
        .replace(Regex("https?://[^\\s]+"), "[URL]")
}
