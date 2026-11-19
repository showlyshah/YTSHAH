package com.viewtube.app.player

import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.viewtube.app.data.Graph
import com.viewtube.app.data.SponsorBlock
import com.viewtube.app.data.VideoDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * One player for the whole app. The UI shows it, and [PlaybackService] puts it in a media
 * notification so audio keeps playing when the screen is off or the app is in the background.
 */
@UnstableApi
object PlayerHolder {
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val player: ExoPlayer by lazy { buildPlayer() }

    private val _current = MutableStateFlow<VideoDetails?>(null)
    val current: StateFlow<VideoDetails?> = _current.asStateFlow()

    /** Height of the quality in use (e.g. 720), or 0 for live / unknown. */
    private val _height = MutableStateFlow(0)
    val height: StateFlow<Int> = _height.asStateFlow()

    private val _skipNotice = MutableStateFlow<String?>(null)
    val skipNotice: StateFlow<String?> = _skipNotice.asStateFlow()

    private var segments: List<SponsorBlock.Segment> = emptyList()
    private var watcher: Job? = null
    private var saver: Job? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun buildPlayer(): ExoPlayer {
        val http = OkHttpDataSource.Factory(Graph.http)
        val factory = DefaultDataSource.Factory(appContext) { YtDataSource(http.createDataSource()) }
        return ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    private fun sourceFactory(): DefaultDataSource.Factory {
        val http = OkHttpDataSource.Factory(Graph.http)
        return DefaultDataSource.Factory(appContext) { YtDataSource(http.createDataSource()) }
    }

    // ------------------------------------------------------------- stream pick

    /** Qualities the viewer can choose from, best first. */
    fun heights(d: VideoDetails): List<Int> =
        (d.videoOnly.map { it.height } + d.muxed.map { it.height })
            .filter { it > 0 }.distinct().sortedDescending()

    /** 720p by default: sharp, but light on data. The user can pick higher. */
    fun defaultHeight(d: VideoDetails): Int {
        val all = heights(d)
        return all.firstOrNull { it <= 720 } ?: all.lastOrNull() ?: 0
    }

    private fun bestAudio(list: List<AudioStream>): AudioStream? {
        val playable = list.filter { it.deliveryMethod != DeliveryMethod.TORRENT }
        val original = playable.filter { it.audioTrackType == null || it.audioTrackType.name == "ORIGINAL" }
            .ifEmpty { playable }
        return original.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
            ?: original.maxByOrNull { it.averageBitrate }
    }

    private fun bestVideo(list: List<VideoStream>, height: Int): VideoStream? {
        val at = list.filter { it.height == height && it.deliveryMethod != DeliveryMethod.TORRENT }
        return at.firstOrNull { it.format == MediaFormat.MPEG_4 } ?: at.firstOrNull()
    }

    private fun mediaSource(stream: Stream, item: MediaItem): MediaSource {
        val factory = sourceFactory()
        val url = stream.content
        val mi = item.buildUpon().setUri(url).build()
        return when (stream.deliveryMethod) {
            DeliveryMethod.HLS -> HlsMediaSource.Factory(factory).createMediaSource(mi)
            DeliveryMethod.DASH -> DashMediaSource.Factory(factory).createMediaSource(mi)
            else -> ProgressiveMediaSource.Factory(factory).createMediaSource(mi)
        }
    }

    // -------------------------------------------------------------------- play

    fun play(d: VideoDetails, wantedHeight: Int = defaultHeight(d), startMs: Long = 0) {
        val same = _current.value?.video?.id == d.video.id
        val resumeAt = if (same && startMs == 0L) player.currentPosition else startMs

        val meta = MediaMetadata.Builder()
            .setTitle(d.video.title)
            .setArtist(d.channel.name)
            .setArtworkUri(d.video.thumbnail?.let { android.net.Uri.parse(it) })
            .build()
        val item = MediaItem.Builder().setMediaId(d.video.id).setMediaMetadata(meta).build()

        val source: MediaSource
        if (d.isLive && d.hlsUrl != null) {
            source = HlsMediaSource.Factory(sourceFactory())
                .createMediaSource(item.buildUpon().setUri(d.hlsUrl).build())
            _height.value = 0
        } else {
            val audio = bestAudio(d.audio)
            val videoOnly = bestVideo(d.videoOnly, wantedHeight)
            val muxed = bestVideo(d.muxed, wantedHeight)
            val chosen: MediaSource? = when {
                videoOnly != null && audio != null ->
                    MergingMediaSource(mediaSource(videoOnly, item), mediaSource(audio, item))
                muxed != null -> mediaSource(muxed, item)
                // Nothing at that exact height: use the best muxed stream, or audio only.
                d.muxed.isNotEmpty() -> mediaSource(d.muxed.maxBy { it.height }, item)
                audio != null -> mediaSource(audio, item)
                else -> null
            }
            requireNotNull(chosen) { "This video has no playable streams (it may be restricted)." }
            source = chosen
            _height.value = if (videoOnly != null && audio != null) videoOnly.height
            else muxed?.height ?: d.muxed.maxOfOrNull { it.height } ?: 0
        }

        _current.value = d
        player.setMediaSource(source)
        player.prepare()
        if (resumeAt > 0) player.seekTo(resumeAt)
        player.playWhenReady = true

        if (!same) loadSegments(d.video.id)
        startWatching(d)
        appContext.startService(Intent(appContext, PlaybackService::class.java))
    }

    fun stop() {
        saveProgress()
        watcher?.cancel(); saver?.cancel()
        player.stop()
        player.clearMediaItems()
        _current.value = null
        segments = emptyList()
    }

    // ---------------------------------------------------------- sponsor skipping

    private fun loadSegments(id: String) {
        segments = emptyList()
        if (!Graph.store.sponsorSkip.value) return
        scope.launch { segments = Graph.sponsor.segments(id) }
    }

    private fun startWatching(d: VideoDetails) {
        watcher?.cancel()
        watcher = scope.launch {
            while (true) {
                delay(400)
                if (!Graph.store.sponsorSkip.value || !player.isPlaying) continue
                val pos = player.currentPosition
                val seg = segments.firstOrNull { pos >= it.startMs && pos < it.endMs - 500 } ?: continue
                player.seekTo(seg.endMs)
                _skipNotice.value = "Skipped ${label(seg.category)}"
                scope.launch { delay(2500); _skipNotice.value = null }
            }
        }
        saver?.cancel()
        saver = scope.launch {
            while (true) {
                delay(5000)
                saveProgress()
            }
        }
    }

    private fun label(category: String) = when (category) {
        "sponsor" -> "sponsor"
        "selfpromo" -> "self-promotion"
        "interaction" -> "subscribe reminder"
        "intro" -> "intro"
        "outro" -> "outro"
        "preview" -> "recap"
        else -> "non-music part"
    }

    private fun saveProgress() {
        val d = _current.value ?: return
        if (d.isLive) return
        val dur = player.duration
        val pos = player.currentPosition
        // Forget the position once the video is basically finished.
        Graph.store.savePosition(d.video.id, if (dur > 0 && pos > dur - 10_000) 0 else pos)
    }

    fun addEndedListener(onEnded: () -> Unit): Player.Listener {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }
        }
        player.addListener(l)
        return l
    }

    fun removeListener(l: Player.Listener) = player.removeListener(l)
}
