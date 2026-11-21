package com.viewtube.app

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists the last fatal uncaught exception before Android's normal crash handler runs.
 * The report stays on-device until the user copies or clears it from Account & settings.
 */
object CrashReportRecorder {
    private const val REPORT_FILE = "viewtube-last-crash.txt"

    fun install(application: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(application, thread, throwable) }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    fun read(context: Context): String? = runCatching {
        File(context.filesDir, REPORT_FILE).takeIf { it.isFile }?.readText()
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, REPORT_FILE).delete() }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val version = packageInfo.versionName ?: "unknown"
        val stack = StringWriter().also { writer ->
            PrintWriter(writer).use { throwable.printStackTrace(it) }
        }.toString()
        // Avoid persisting common credential/token values if they appear in exception text.
        val safeStack = stack
            .replace(Regex("(?i)(authorization|api[_-]?key|access[_-]?token|refresh[_-]?token|password)(\\s*[:=]\\s*)[^\\s&]+"), "$1$2<redacted>")
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())
        val report = buildString {
            appendLine("VIEWTUBE AUTOMATIC CRASH REPORT")
            appendLine("Time: $timestamp")
            appendLine("App version: $version")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            appendLine("FATAL EXCEPTION")
            append(safeStack)
        }
        File(context.filesDir, REPORT_FILE).writeText(report)
    }
}
