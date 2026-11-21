package com.viewtube.app

import android.app.Application
import com.viewtube.app.data.Graph
import com.viewtube.app.data.RuntimeDiagnostics

class ViewTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RuntimeDiagnostics.init(this)
        RuntimeDiagnostics.record("APP", "application-onCreate", "started")
        CrashReportRecorder.install(this)
        Graph.init(this)
        RuntimeDiagnostics.record("APP", "application-onCreate", "complete")
    }
}
