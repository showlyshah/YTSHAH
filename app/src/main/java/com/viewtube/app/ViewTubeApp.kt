package com.viewtube.app

import android.app.Application
import com.viewtube.app.data.Graph
import com.viewtube.app.data.RuntimeDiagnostics

class ViewTubeApp : Application() {
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val runtime = Runtime.getRuntime()
        RuntimeDiagnostics.record("APP", "memory-trim", "warning",
            "level=$level, usedMb=${(runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024}, maxMb=${runtime.maxMemory() / 1024 / 1024}")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        RuntimeDiagnostics.record("APP", "low-memory", "warning")
    }

    override fun onCreate() {
        super.onCreate()
        RuntimeDiagnostics.init(this)
        RuntimeDiagnostics.record("APP", "application-onCreate", "started")
        CrashReportRecorder.install(this)
        try {
            Graph.init(this)
            RuntimeDiagnostics.record("APP", "application-onCreate", "complete")
        } catch (t: Throwable) {
            RuntimeDiagnostics.record("APP", "application-init-failed", "fatal", error = t)
            throw t
        }
    }
}
