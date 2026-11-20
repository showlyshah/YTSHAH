package com.viewtube.app.data

import android.content.Context
import androidx.media3.common.util.UnstableApi
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Simple app-wide holder for the long-lived objects. */
@androidx.annotation.OptIn(UnstableApi::class)
object Graph {
    lateinit var appContext: Context; private set
    lateinit var http: OkHttpClient; private set
    lateinit var repo: YouTubeRepo; private set
    lateinit var store: LocalStore; private set
    lateinit var sponsor: SponsorBlock; private set
    lateinit var secure: SecureStore; private set
    lateinit var piped: PipedClient; private set

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        http = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val locale = Locale.getDefault()
        NewPipe.init(
            DownloaderImpl(http),
            Localization.fromLocale(locale),
            ContentCountry(locale.country.ifBlank { "US" })
        )

        store = LocalStore(app)
        secure = SecureStore(app)
        repo = YouTubeRepo(http)
        sponsor = SponsorBlock(http)
        piped = PipedClient(http, secure, store)
        com.viewtube.app.player.PlayerHolder.init(app)
    }
}
