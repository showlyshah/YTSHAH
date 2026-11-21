package com.viewtube.app.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong

/**
 * Chronological, local-only runtime trace. Events are kept in SharedPreferences (not a
 * log file) and are exported as plain text only when the user taps Copy.
 */
object RuntimeDiagnostics {
    private const val PREFS = "viewtube_runtime_trace"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 600
    private const val MAX_DETAIL = 1800
    private val lock = Any()
    private val sequence = AtomicLong(0)
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = runCatching { JSONArray(prefs.getString(KEY_EVENTS, "[]")) }.getOrDefault(JSONArray())
        sequence.set(existing.optJSONObject(existing.length() - 1)?.optLong("seq") ?: 0L)
        record("APP", "process-start", "ok",
            "pid=${Process.myPid()}, process=${if (Build.VERSION.SDK_INT >= 28) android.app.Application.getProcessName() else "unknown"}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                manager.getHistoricalProcessExitReasons(context.packageName, 0, 5).forEach { exit ->
                    val reason = when (exit.reason) {
                        ApplicationExitInfo.REASON_ANR -> "ANR"
                        ApplicationExitInfo.REASON_CRASH -> "CRASH"
                        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
                        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
                        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "RESOURCE_LIMIT"
                        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
                        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
                        else -> "OTHER:${exit.reason}"
                    }
                    record("OS_EXIT_HISTORY", "previous-process-exit", reason,
                        "timestamp=${exit.timestamp}, status=${exit.status}, importance=${exit.importance}, pssKb=${exit.pss}, rssKb=${exit.rss}, description=${exit.description.orEmpty()}")
                }
            }
        }
    }

    fun record(
        component: String,
        event: String,
        outcome: String = "info",
        detail: String? = null,
        error: Throwable? = null,
    ) {
        val context = appContext ?: return
        runCatching {
            synchronized(lock) {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val events = runCatching { JSONArray(prefs.getString(KEY_EVENTS, "[]")) }.getOrDefault(JSONArray())
                val item = JSONObject()
                    .put("wall", System.currentTimeMillis())
                    .put("elapsed", SystemClock.elapsedRealtime())
                    .put("seq", sequence.incrementAndGet())
                    .put("component", component.take(48))
                    .put("event", event.take(100))
                    .put("outcome", outcome.take(32))
                    .put("thread", Thread.currentThread().name.take(64))
                if (!detail.isNullOrBlank()) item.put("detail", sanitize(detail).take(MAX_DETAIL))
                if (error != null) {
                    item.put("exception", error.javaClass.name)
                    item.put("message", sanitize(error.message.orEmpty()).take(600))
                    item.put("stack", sanitize(error.stackTraceToString().lineSequence().take(14).joinToString("\n")).take(2400))
                }
                events.put(item)
                val trimmed = JSONArray()
                val first = (events.length() - MAX_EVENTS).coerceAtLeast(0)
                for (i in first until events.length()) trimmed.put(events.getJSONObject(i))
                // commit() is intentional: a crash immediately after this event should not
                // silently lose the most recent diagnostic entries.
                prefs.edit().putString(KEY_EVENTS, trimmed.toString()).apply()
            }
        }
    }

    fun report(context: Context): String {
        val app = context.applicationContext
        val events = runCatching {
            JSONArray(app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_EVENTS, "[]"))
        }.getOrDefault(JSONArray())
        val version = runCatching {
            @Suppress("DEPRECATION")
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown"
        }.getOrDefault("unknown")
        val memory = Runtime.getRuntime()
        val connectivity = runCatching {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
            when {
                caps == null -> "offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
                else -> "connected/other"
            } + ", metered=${cm.isActiveNetworkMetered}"
        }.getOrDefault("unknown")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)
        dateFormat.timeZone = TimeZone.getDefault()
        val out = StringBuilder()
            .appendLine("VIEWTUBE COMPLETE RUNTIME DIAGNOSTIC REPORT")
            .appendLine("Generated: ${dateFormat.format(Date())}")
            .appendLine("App: ${app.packageName} | version=$version")
            .appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            .appendLine("Android: ${Build.VERSION.RELEASE} | SDK=${Build.VERSION.SDK_INT} | build=${Build.DISPLAY}")
            .appendLine("Process: pid=${Process.myPid()} | locale=${Locale.getDefault()} | timezone=${TimeZone.getDefault().id}")
            .appendLine("Network now: $connectivity")
            .appendLine("Memory now: used=${(memory.totalMemory() - memory.freeMemory()) / 1024 / 1024}MB, heapMax=${memory.maxMemory() / 1024 / 1024}MB")
            .appendLine("Events retained: ${events.length()} (newest $MAX_EVENTS)")
            .appendLine("Storage: local SharedPreferences only; no log file and no automatic upload.")
            .appendLine("Privacy: request URLs are reduced to host/path; query strings and common credentials are redacted.")
            .appendLine()
            .appendLine("===== CHRONOLOGICAL EVENT TRACE (oldest to newest) =====")
        for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            val time = runCatching { dateFormat.format(Date(e.optLong("wall"))) }.getOrDefault("?")
            out.append("[${e.optLong("seq")}] $time +${e.optLong("elapsed")}ms")
                .append(" [${e.optString("thread")}] ")
                .append("${e.optString("component")}/${e.optString("event")} => ${e.optString("outcome")}")
                .appendLine()
            if (e.has("detail")) out.appendLine("    detail: ${e.optString("detail")}")
            if (e.has("exception")) {
                out.appendLine("    exception: ${e.optString("exception")}: ${e.optString("message")}")
                out.appendLine(e.optString("stack"))
            }
        }
        if (events.length() == 0) out.appendLine("No runtime events recorded.")
        out.appendLine()
            .appendLine("===== DETAILED HOME & SHORTS FEED DIAGNOSTICS =====")
            .appendLine(runCatching { FeedDiagnostics.report() }.getOrElse { "Feed diagnostic report unavailable: ${it.message}" })
            .appendLine("===== END OF REPORT =====")
        return out.toString()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_EVENTS).commit()
        sequence.set(0)
        record("DIAGNOSTICS", "history-cleared", "ok")
    }

    /** Remove credentials and query strings while retaining endpoint and failure context. */
    fun safeUrl(raw: String): String = runCatching {
        val uri = android.net.Uri.parse(raw)
        "${uri.scheme}://${uri.host}${uri.encodedPath.orEmpty()}"
    }.getOrDefault("[unparseable-url]")

    private fun sanitize(value: String): String = value
        .replace(Regex("(?i)(authorization|cookie|set-cookie|token|api[_-]?key|password|secret)(\\s*[:=]\\s*)[^\\s,;]+"), "$1$2[REDACTED]")
        .replace(Regex("https?://[^\\s]+")) { safeUrl(it.value) }
}
