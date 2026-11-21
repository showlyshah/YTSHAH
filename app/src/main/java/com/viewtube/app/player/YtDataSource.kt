package com.viewtube.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.viewtube.app.data.DownloaderImpl
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper as Yt

/**
 * Wraps a normal HTTP data source and does two things YouTube's servers need:
 *  1. sends the User-Agent that matches the client the stream URL was issued for, and
 *  2. downloads big streams in 10 MB pieces (the `range` URL parameter). Without this YouTube
 *     throttles the connection and playback stutters.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class YtDataSource(private val upstream: DataSource) : DataSource {

    private var spec: DataSpec? = null
    private var chunking = false
    private var position = 0L
    private var end = 0L

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        val uri = dataSpec.uri
        val clen = uri.getQueryParameter("clen")?.toLongOrNull()
        chunking = clen != null &&
            uri.host?.endsWith("googlevideo.com") == true &&
            uri.path?.contains("videoplayback") == true &&
            uri.getQueryParameter("range") == null &&
            uri.getQueryParameter("ratebypass") != "yes"

        if (!chunking) return upstream.open(dataSpec.withAdditionalHeaders(headersFor(uri)))

        position = dataSpec.position
        end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            minOf(clen!!, dataSpec.position + dataSpec.length)
        } else clen!!
        if (position < end) openChunk()
        return maxOf(0L, end - dataSpec.position)
    }

    private fun openChunk() {
        val s = spec!!
        val chunkEnd = minOf(end, position + CHUNK)
        val uri = s.uri.buildUpon().appendQueryParameter("range", "$position-${chunkEnd - 1}").build()
        upstream.open(
            s.buildUpon()
                .setUri(uri)
                .setPosition(0)
                .setLength(C.LENGTH_UNSET.toLong())
                .build()
                .withAdditionalHeaders(headersFor(uri))
        )
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!chunking) return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        if (position >= end) return C.RESULT_END_OF_INPUT
        var n = upstream.read(buffer, offset, length)
        if (n == C.RESULT_END_OF_INPUT) {
            upstream.close()
            if (position >= end) return C.RESULT_END_OF_INPUT
            openChunk()
            n = upstream.read(buffer, offset, length)
        }
        if (n > 0) position += n
        return n
    }

    override fun getUri(): Uri? = spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        try {
            upstream.close()
        } finally {
            spec = null
        }
    }

    private fun headersFor(uri: Uri): Map<String, String> {
        val url = uri.toString()
        return when {
            Yt.isVisionOsStreamingUrl(url) -> mapOf("User-Agent" to Yt.getVisionOsUserAgent(null))
            Yt.isWebStreamingUrl(url) || uriHostIsYouTube(url) -> mapOf(
                "User-Agent" to DownloaderImpl.USER_AGENT,
                "Origin" to "https://www.youtube.com",
                "Referer" to "https://www.youtube.com/",
            )
            uriHostIsGoogleVideo(url) -> mapOf(
                "User-Agent" to DownloaderImpl.USER_AGENT,
                "Origin" to "https://www.youtube.com",
                "Referer" to "https://www.youtube.com",
            )
            else -> emptyMap()
        }
    }

    private fun uriHostIsYouTube(url: String): Boolean =
        runCatching {
            val host = Uri.parse(url).host.orEmpty()
            host == "youtube.com" || host.endsWith(".youtube.com") || host.endsWith(".youtube-nocookie.com")
        }.getOrDefault(false)

    private fun uriHostIsGoogleVideo(url: String): Boolean =
        runCatching { Uri.parse(url).host?.endsWith("googlevideo.com") == true }.getOrDefault(false)

    private companion object {
        const val CHUNK = 10L * 1024 * 1024
    }
}
