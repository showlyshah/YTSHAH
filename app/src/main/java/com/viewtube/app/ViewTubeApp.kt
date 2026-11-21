package com.viewtube.app

import android.app.Application
import com.viewtube.app.data.Graph

class ViewTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReportRecorder.install(this)
        Graph.init(this)
    }
}
