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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _playing = MutableStateFlow(false)
    /** True while the player is set to play (shows pause icon in the mini player). */
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _audioTrack = MutableStateFlow<String?>(null)
    val audioTrack: StateFlow<String?> = _audioTrack.asStateFlow()

    private val _audioBitrate = MutableStateFlow(0)
    val audioBitrate: StateFlow<Int> = _audioBitrate.asStateFlow()

    private val _audioOnly = MutableStateFlow(false)
    val audioOnly: StateFlow<Boolean> = _audioOnly.asStateFlow()

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    /** Fired with the video id when autoplay moved on to the next video by itself. */
    private val _advanced = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val advanced: SharedFlow<String> = _advanced.asSharedFlow()

    private val _skipNotice = MutableStateFlow<String?>(null)
    val skipNotice: StateFlow<String?> = _skipNotice.asStateFlow()
    private val _pendingSegment = MutableStateFlow<SponsorBlock.Segment?>(null)
    val pendingSegment: StateFlow<SponsorBlock.Segment?> = _pendingSegment.asStateFlow()
    private val _undoAvailable = MutableStateFlow(false)
    val undoAvailable: StateFlow<Boolean> = _undoAvailable.asStateFlow()

    private var segments: List<SponsorBlock.Segment> = emptyList()
    private var lastSkippedSegment: SponsorBlock.Segment? = null
    private var suppressedSegment: SponsorBlock.Segment? = null
    private var lastAutoSkipKey: String? = null
    private var watcher: Job? = null
    private var saver: Job? = null
    private var segmentJob: Job? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun buildPlayer(): ExoPlayer {
        val http = OkHttpDataSource.Factory(Graph.http)
        val factory = DefaultDataSource.Factory(appContext) { YtDataSource(http.createDataSource()) }
        val p = ExoPlayer.Builder(appContext)
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
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { _playing.value = p.playWhenReady && p.playbackState != Player.STATE_ENDED }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { _playing.value = playWhenReady && p.playbackState != Player.STATE_ENDED }
            override fun onPlaybackStateChanged(state: Int) {
                _playing.value = p.playWhenReady && state != Player.STATE_ENDED
                if (state == Player.STATE_ENDED) onEnded()
            }
        })
        return p
    }

    private fun onEnded() {
        val d = _current.value ?: return
        if (looping || d.isLive || !Graph.store.autoplayNext.value) return
        val next = d.related.firstOrNull { !Graph.store.isWatched(it.id) } ?: d.related.firstOrNull() ?: return
        scope.launch {
            runCatching {
                val nd = Graph.repo.details(next.id)
                Graph.store.addHistory(nd.video)
                play(nd)
                _advanced.emit(next.id)
            }
        }
    }

    private fun sourceFactory(): DefaultDataSource.Factory {
        val http = OkHttpDataSource.Factory(Graph.http)
        return DefaultDataSource.Factory(appContext) { YtDataSource(http.createDataSource()) }
    }

    // ------------------------------------------------------------- stream pick

    /** Qualities the viewer can choose from, best first. */
    fun heights(d: VideoDetails): List<Int> =
        (d.videoOnly.filter { it.deliveryMethod != DeliveryMethod.TORRENT }.map { it.height } + d.muxed.filter { it.deliveryMethod != DeliveryMethod.TORRENT }.map { it.height })
            .filter { it > 0 }.distinct().sortedDescending()

    /** Auto selects the highest stream exposed for this video; a saved preference still wins. */
    fun defaultHeight(d: VideoDetails): Int {
        val all = heights(d)
        val pref = Graph.store.preferredHeight.value
        return if (pref > 0) all.firstOrNull { it <= pref } ?: all.lastOrNull() ?: 0
        else all.firstOrNull() ?: 0
    }

    private fun isMetered(): Boolean = runCatching {
        (appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).isActiveNetworkMetered
    }.getOrDefault(false)

    /** Audio languages / tracks on offer for this video: id to label. Empty when there is only one. */
    fun audioOptions(d: VideoDetails): List<Pair<String, String>> =
        d.audio.filter { it.audioTrackId != null && it.deliveryMethod != DeliveryMethod.TORRENT }
            .distinctBy { it.audioTrackId }
            .map { (it.audioTrackId ?: "") to (it.audioTrackName ?: it.audioLocale?.displayName ?: it.audioTrackId ?: "Track") }
            .let { if (it.size > 1) it else emptyList() }

    /** Selects the best available audio stream, optionally constrained to a language and bitrate. */
    private fun bestAudio(list: List<AudioStream>, trackId: String?, bitrate: Int): AudioStream? {
        val playable = list.filter { it.deliveryMethod != DeliveryMethod.TORRENT }
        val chosen = if (trackId != null) playable.filter { it.audioTrackId == trackId } else emptyList()
        val original = chosen.ifEmpty {
            playable.filter { it.audioTrackType == null || it.audioTrackType?.name == "ORIGINAL" }.ifEmpty { playable }
        }
        val quality = if (bitrate > 0) original.firstOrNull { it.averageBitrate == bitrate } ?: original.maxByOrNull { it.averageBitrate } else null
        return quality ?: original.maxByOrNull { it.averageBitrate }
    }

    /** Distinct audio bitrates available for the selected language, highest first. */
    fun audioQualities(d: VideoDetails, trackId: String? = _audioTrack.value): List<Pair<Int, String>> {
        val streams = d.audio.filter { it.deliveryMethod != DeliveryMethod.TORRENT &&
            (trackId == null || it.audioTrackId == trackId) }
        return streams.map { it.averageBitrate }.filter { it > 0 }.distinct().sortedDescending()
            .map { it to "${(it + 500) / 1000} kbps" }
    }

    private fun bestVideo(list: List<VideoStream>, height: Int): VideoStream? {
        val ok = list.filter { it.deliveryMethod != DeliveryMethod.TORRENT }
        val h = ok.map { it.height }.filter { it <= height }.maxOrNull() ?: return null
        val at = ok.filter { it.height == h }
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

    private var looping = false

    fun play(
        d: VideoDetails,
        wantedHeight: Int = defaultHeight(d),
        startMs: Long = 0,
        audioTrackId: String? = _audioTrack.value,
        audioBitrate: Int = _audioBitrate.value,
        audioOnly: Boolean = _audioOnly.value,
        loop: Boolean = false,
    ) {
        looping = loop
        _audioBitrate.value = audioBitrate
        val audioOnlyEnabled = audioOnly && !d.isLive
        _audioOnly.value = audioOnlyEnabled
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        _audioTrack.value = audioTrackId
        val same = _current.value?.video?.id == d.video.id
        val resumeAt = if (same && startMs == 0L) player.currentPosition else startMs
        if (!same) saveProgress()

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
            val audio = bestAudio(d.audio, audioTrackId, audioBitrate)
            val videoOnly = bestVideo(d.videoOnly, wantedHeight)
            val muxed = bestVideo(d.muxed, wantedHeight)
            val playableMuxed = d.muxed.filter { it.deliveryMethod != DeliveryMethod.TORRENT }
            val chosen: MediaSource? = if (audioOnlyEnabled) {
                audio?.let { mediaSource(it, item) }
            } else when {
                videoOnly != null && audio != null ->
                    MergingMediaSource(mediaSource(videoOnly, item), mediaSource(audio, item))
                muxed != null -> mediaSource(muxed, item)
                // Nothing at that exact height: use the best muxed stream, or audio only.
                playableMuxed.isNotEmpty() -> mediaSource(playableMuxed.maxBy { it.height }, item)
                audio != null -> mediaSource(audio, item)
                else -> null
            }
            requireNotNull(chosen) { "This video has no playable streams (it may be restricted)." }
            source = chosen
            _height.value = if (audioOnlyEnabled) 0 else if (videoOnly != null && audio != null) videoOnly.height
            else muxed?.height ?: playableMuxed.maxOfOrNull { it.height } ?: 0
        }

        _current.value = d
        player.setMediaSource(source)
        player.prepare()
        if (resumeAt > 0) player.seekTo(resumeAt)
        player.setPlaybackSpeed(_speed.value)
        player.playWhenReady = true

        if (!same) loadSegments(d.video.id)
        startWatching(d)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            appContext.startForegroundService(Intent(appContext, PlaybackService::class.java))
        } else {
            appContext.startService(Intent(appContext, PlaybackService::class.java))
        }
    }

    fun stop() {
        saveProgress()
        watcher?.cancel(); saver?.cancel(); segmentJob?.cancel(); segmentJob = null
        // stop() alone does not clear playWhenReady; some controllers can therefore
        // keep presenting the session as active. Pause first, then clear the media item.
        player.pause()
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        _current.value = null
        _height.value = 0
        _audioTrack.value = null
        _audioBitrate.value = 0
        _audioOnly.value = false
        _skipNotice.value = null
        _pendingSegment.value = null
        _undoAvailable.value = false
        lastSkippedSegment = null
        suppressedSegment = null
        lastAutoSkipKey = null
        _playing.value = false
        segments = emptyList()
        runCatching { appContext.stopService(Intent(appContext, PlaybackService::class.java)) }
    }

    fun toggle() { if (player.playWhenReady) player.pause() else player.play() }

    fun setSpeed(s: Float) { _speed.value = s; player.setPlaybackSpeed(s) }

    fun setLooping(on: Boolean) { looping = on; player.repeatMode = if (on) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }

    // ---------------------------------------------------------- sponsor skipping

    private fun loadSegments(id: String) {
        segmentJob?.cancel()
        segments = emptyList()
        if (!Graph.store.sponsorSkip.value) return
        segmentJob = scope.launch {
            val loaded = Graph.sponsor.segments(id)
            if (_current.value?.video?.id == id) segments = loaded
        }
    }

    private fun startWatching(d: VideoDetails) {
        watcher?.cancel()
        watcher = scope.launch {
            while (true) {
                delay(350)
                if (!Graph.store.sponsorSkip.value || !player.isPlaying) {
                    _pendingSegment.value = null
                    continue
                }
                val pos = player.currentPosition
                suppressedSegment?.let { suppressed ->
                    if (pos < suppressed.startMs - 1_000 || pos >= suppressed.endMs) suppressedSegment = null
                }
                val seg = segments.firstOrNull {
                    it.category in Graph.store.sponsorCategories.value &&
                        pos >= it.startMs && pos < it.endMs - 350
                }
                if (seg == null) {
                    _pendingSegment.value = null
                    if (lastAutoSkipKey != null && segments.none { segmentKey(it) == lastAutoSkipKey && pos in it.startMs until it.endMs }) {
                        lastAutoSkipKey = null
                    }
                    continue
                }
                if (suppressedSegment == seg) {
                    _pendingSegment.value = null
                    continue
                }
                if (Graph.store.sponsorAutoSkip.value) {
                    _pendingSegment.value = null
                    val key = segmentKey(seg)
                    if (lastAutoSkipKey == key) continue
                    lastAutoSkipKey = key
                    lastSkippedSegment = seg
                    _undoAvailable.value = true
                    player.seekTo(seg.endMs)
                    _skipNotice.value = "Skipped ${label(seg.category)}"
                    scope.launch { delay(2500); if (_skipNotice.value == "Skipped ${label(seg.category)}") _skipNotice.value = null }
                } else {
                    _pendingSegment.value = seg
                }
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

    private fun segmentKey(segment: SponsorBlock.Segment) = "${segment.category}:${segment.startMs}:${segment.endMs}"

    fun skipPendingSegment() {
        val seg = _pendingSegment.value ?: return
        player.seekTo(seg.endMs)
        lastSkippedSegment = seg
        _undoAvailable.value = true
        _pendingSegment.value = null
        _skipNotice.value = "Skipped ${label(seg.category)}"
    }

    fun undoLastSkip() {
        val seg = lastSkippedSegment ?: return
        suppressedSegment = seg
        lastSkippedSegment = null
        _undoAvailable.value = false
        _pendingSegment.value = null
        player.seekTo(seg.startMs)
        _skipNotice.value = "Skip undone"
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
