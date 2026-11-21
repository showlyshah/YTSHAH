package com.viewtube.app.data

import android.content.Context
import android.os.SystemClock
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Simple app-wide holder for the long-lived objects. */
object Graph {
    lateinit var appContext: Context; private set
    lateinit var http: OkHttpClient; private set
    lateinit var repo: YouTubeRepo; private set
    lateinit var store: LocalStore; private set
    lateinit var sponsor: SponsorBlock; private set
    lateinit var secure: SecureStore; private set
    lateinit var piped: PipedClient; private set
    lateinit var diagnostics: DiagnosticJournal; private set
    lateinit var ai: HybridAiCoordinator; private set

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        RuntimeDiagnostics.record("GRAPH", "initialisation", "started")
        http = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val requestId = System.nanoTime().toString(16)
                val started = SystemClock.elapsedRealtime()
                RuntimeDiagnostics.record(
                    "HTTP",
                    "request-start",
                    "started",
                    "id=$requestId method=${request.method} url=${RuntimeDiagnostics.safeUrl(request.url.toString())}"
                )
                try {
                    val response = chain.proceed(request)
                    RuntimeDiagnostics.record(
                        "HTTP",
                        "response",
                        if (response.isSuccessful) "success" else "http-error",
                        "id=$requestId code=${response.code} durationMs=${SystemClock.elapsedRealtime() - started} " +
                            "method=${request.method} url=${RuntimeDiagnostics.safeUrl(request.url.toString())}"
                    )
                    response
                } catch (t: Throwable) {
                    RuntimeDiagnostics.record(
                        "HTTP",
                        "request-failed",
                        "failure",
                        "id=$requestId durationMs=${SystemClock.elapsedRealtime() - started} " +
                            "method=${request.method} url=${RuntimeDiagnostics.safeUrl(request.url.toString())}",
                        t
                    )
                    throw t
                }
            }
            .build()

        RuntimeDiagnostics.record("GRAPH", "http-client", "ready", "connectTimeoutMs=20000 readTimeoutMs=30000")
        val locale = Locale.getDefault()
        NewPipe.init(
            DownloaderImpl(http),
            Localization.fromLocale(locale),
            ContentCountry(locale.country.ifBlank { "US" })
        )
        RuntimeDiagnostics.record("GRAPH", "newpipe-extractor", "ready", "locale=$locale")

        store = LocalStore(app)
        RuntimeDiagnostics.record("GRAPH", "local-store", "ready")
        diagnostics = DiagnosticJournal(app.getSharedPreferences("viewtube_diagnostics", Context.MODE_PRIVATE))
        secure = SecureStore(app)
        repo = YouTubeRepo(http)
        sponsor = SponsorBlock(http)
        piped = PipedClient(http, secure, store)
        ai = HybridAiCoordinator(
            local = BasicLocalAiEngine(),
            cloudProvider = { OpenAiCompatibleProvider(http, secure) },
            cloudEnabled = { store.cloudAiEnabled.value },
        )
        com.viewtube.app.player.PlayerHolder.init(app)
        RuntimeDiagnostics.record("GRAPH", "player-holder", "ready")
        RuntimeDiagnostics.record("GRAPH", "initialisation", "complete")
    }
}
