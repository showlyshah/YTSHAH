package com.viewtube.app.player

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import com.viewtube.app.data.Graph
import com.viewtube.app.data.RuntimeDiagnostics
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
import java.io.File
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
    private const val MAX_CACHE_BYTES = 256L * 1024L * 1024L
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mediaCache: SimpleCache by lazy {
        SimpleCache(
            File(appContext.cacheDir, "viewtube_stream_cache"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(appContext)
        )
    }

    val audioSessionId: Int get() = runCatching { player.audioSessionId }.getOrDefault(C.AUDIO_SESSION_ID_UNSET)
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

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()
    private val _looping = MutableStateFlow(false)
    val looping: StateFlow<Boolean> = _looping.asStateFlow()
    private val _audioOnly = MutableStateFlow(false)
    val audioOnly: StateFlow<Boolean> = _audioOnly.asStateFlow()

    /** Fired with the video id when autoplay moved on to the next video by itself. */
    private val _advanced = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val advanced: SharedFlow<String> = _advanced.asSharedFlow()

    private val _skipNotice = MutableStateFlow<String?>(null)
    val skipNotice: StateFlow<String?> = _skipNotice.asStateFlow()

    private var segments: List<SponsorBlock.Segment> = emptyList()
    private var watcher: Job? = null
    private var saver: Job? = null
    private var sleepJob: Job? = null
    private val _sleepRemainingMs = MutableStateFlow<Long?>(null)
    val sleepRemainingMs: StateFlow<Long?> = _sleepRemainingMs.asStateFlow()
    private val _repeatMode = MutableStateFlow("continuous")
    val repeatMode: StateFlow<String> = _repeatMode.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        RuntimeDiagnostics.record("PLAYER", "holder-init", "ok")
    }

    private fun buildPlayer(): ExoPlayer {
        RuntimeDiagnostics.record("PLAYER", "engine-build", "started", "engine=Media3 ExoPlayer")
        val p = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(sourceFactory()))
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
                val label = when (state) {
                    Player.STATE_IDLE -> "idle"
                    Player.STATE_BUFFERING -> "buffering"
                    Player.STATE_READY -> "ready"
                    Player.STATE_ENDED -> "ended"
                    else -> "unknown:$state"
                }
                RuntimeDiagnostics.record("PLAYER", "playback-state", label,
                    "playWhenReady=${p.playWhenReady}, isPlaying=${p.isPlaying}, mediaId=${p.currentMediaItem?.mediaId ?: "none"}")
                if (state == Player.STATE_ENDED) onEnded()
            }
            override fun onPlayerError(error: PlaybackException) {
                RuntimeDiagnostics.record("PLAYER", "player-error", "failure",
                    "errorCode=${error.errorCode}, errorCodeName=${error.errorCodeName}, mediaId=${p.currentMediaItem?.mediaId ?: "none"}", error)
            }
        })
        RuntimeDiagnostics.record("PLAYER", "engine-build", "ready", "audioSessionId=${p.audioSessionId}")
        return p
    }

    /** Uses the user's queued item first, then the current video's recommendations. */
    fun playNext(onAdvanced: (String) -> Unit) {
        val current = _current.value ?: return
        advanceToNext(current, onAdvanced, "manual_next")
    }

    private fun advanceToNext(d: VideoDetails, onAdvanced: ((String) -> Unit)?, tag: String) {
        val queued = Graph.store.queue.value.firstOrNull { it.id != d.video.id }
        val recommendation = d.related.firstOrNull { it.id != d.video.id && !Graph.store.isWatched(it.id) }
            ?: d.related.firstOrNull { it.id != d.video.id }
        val nextId = queued?.id ?: recommendation?.id ?: return
        scope.launch {
            runCatching {
                val nextDetails = Graph.repo.details(nextId)
                if (queued != null) Graph.store.removeFromQueue(nextId)
                Graph.store.addHistory(nextDetails.video)
                play(nextDetails)
                if (onAdvanced == null) _advanced.emit(nextId) else onAdvanced(nextId)
            }.onFailure { Graph.diagnostics.record(tag, it) }
        }
    }

    private fun onEnded() {
        val d = _current.value ?: return
        if (loopRepeatEnabled || d.isLive || _repeatMode.value == "one") return
        if (_repeatMode.value == "off" || !Graph.store.autoplayNext.value) return
        // A user-curated queue always takes precedence over automatic related videos.
        advanceToNext(d, null, "autoplay")
    }

    private fun sourceFactory(): DataSource.Factory {
        val http = OkHttpDataSource.Factory(Graph.http)
        val upstream = DefaultDataSource.Factory(appContext) { YtDataSource(http.createDataSource()) }
        return CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun playbackCacheBytes(): Long = runCatching { mediaCache.cacheSpace }.getOrDefault(0L)

    /** Clears temporary playback cache only; never touches Watch later, history or downloads. */
    fun clearPlaybackCache() {
        runCatching { mediaCache.keys.toList().forEach { mediaCache.removeResource(it) } }
    }

    // ------------------------------------------------------------- stream pick

    /** Qualities the viewer can choose from, best first. */
    fun heights(d: VideoDetails): List<Int> =
        (d.videoOnly.map { it.height } + d.muxed.map { it.height })
            .filter { it > 0 }.distinct().sortedDescending()

    /** The user's preferred height, else 480p on mobile data and 720p on Wi-Fi. */
    fun defaultHeight(d: VideoDetails): Int {
        val all = heights(d)
        val pref = Graph.store.preferredHeight.value
        if (pref == -1) return all.firstOrNull() ?: 0 // Explicit Max available always wins over Data Saver.
        val target = if (pref > 0) pref else if (Graph.store.dataSaver.value || isMetered()) 360 else (all.firstOrNull() ?: 720)
        return all.firstOrNull { it <= target } ?: all.lastOrNull() ?: 0
    }

    private fun isMetered(): Boolean = runCatching {
        (appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).isActiveNetworkMetered
    }.getOrDefault(false)

    /** Audio languages / tracks on offer for this video: id to label. Empty when there is only one. */
    fun audioOptions(d: VideoDetails): List<Pair<String, String>> =
        d.audio.filter { it.audioTrackId != null }
            .distinctBy { it.audioTrackId }
            .map { (it.audioTrackId ?: "") to (it.audioTrackName ?: it.audioLocale?.displayName ?: it.audioTrackId ?: "Track") }
            .let { if (it.size > 1) it else emptyList() }

    private fun bestAudio(list: List<AudioStream>, trackId: String?): AudioStream? {
        val playable = list.filter { it.deliveryMethod != DeliveryMethod.TORRENT }
        val chosen = if (trackId != null) playable.filter { it.audioTrackId == trackId } else emptyList()
        val original = chosen.ifEmpty {
            playable.filter { it.audioTrackType == null || it.audioTrackType?.name == "ORIGINAL" }.ifEmpty { playable }
        }
        val preferred = Graph.store.preferredAudioBitrate.value
        val bitrateChoice = if (preferred <= 0) original.maxByOrNull { it.averageBitrate }
            else original.filter { it.averageBitrate <= preferred }.maxByOrNull { it.averageBitrate }
                ?: original.minByOrNull { it.averageBitrate }
        return bitrateChoice?.takeIf { it.format == MediaFormat.M4A }
            ?: if (bitrateChoice != null) bitrateChoice
            else original.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
                ?: original.maxByOrNull { it.averageBitrate }
    }

    private fun bestVideo(list: List<VideoStream>, height: Int): VideoStream? {
        val ok = list.filter { it.deliveryMethod != DeliveryMethod.TORRENT }
        val h = ok.map { it.height }.filter { it <= height }.maxOrNull() ?: return null
        val at = ok.filter { it.height == h }
        return at.firstOrNull { it.format == MediaFormat.MPEG_4 } ?: at.firstOrNull()
    }

    private fun mediaSource(stream: Stream, item: MediaItem): MediaSource {
        val builder = item.buildUpon().setUri(stream.content)
        when (stream.deliveryMethod) {
            DeliveryMethod.HLS -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            DeliveryMethod.DASH -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
            else -> Unit
        }
        return DefaultMediaSourceFactory(sourceFactory()).createMediaSource(builder.build())
    }

    /**
     * Creates an isolated player for the vertical Shorts feed. It deliberately does not share
     * the app's foreground playback session or mini-player state.
     */
    fun createShortsPlayer(): ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(sourceFactory()))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply { repeatMode = Player.REPEAT_MODE_ONE }

    /** Resolve a Shorts media source using ViewTube's normal quality and audio selection rules. */
    fun shortsMediaSource(d: VideoDetails): MediaSource {
        val metadata = MediaMetadata.Builder()
            .setTitle(d.video.title)
            .setArtist(d.channel.name)
            .setArtworkUri(d.video.thumbnail?.let { android.net.Uri.parse(it) })
            .build()
        val item = MediaItem.Builder()
            .setMediaId(d.video.id)
            .setMediaMetadata(metadata)
            .build()
        if (d.isLive && d.hlsUrl != null) {
            return HlsMediaSource.Factory(sourceFactory())
                .createMediaSource(item.buildUpon().setUri(d.hlsUrl).build())
        }
        val audio = bestAudio(d.audio, null)
        val wantedHeight = defaultHeight(d)
        val videoOnly = bestVideo(d.videoOnly, wantedHeight)
        val muxed = bestVideo(d.muxed, wantedHeight)
        val chosen: MediaSource? = when {
            videoOnly != null && audio != null ->
                MergingMediaSource(mediaSource(videoOnly, item), mediaSource(audio, item))
            muxed != null -> mediaSource(muxed, item)
            d.muxed.isNotEmpty() -> mediaSource(d.muxed.maxBy { it.height }, item)
            audio != null -> mediaSource(audio, item)
            else -> null
        }
        return requireNotNull(chosen) { "This Short has no playable streams." }
    }

    // -------------------------------------------------------------------- play

    private var loopRepeatEnabled = false

    fun play(
        d: VideoDetails,
        wantedHeight: Int = defaultHeight(d),
        startMs: Long = 0,
        audioTrackId: String? = _audioTrack.value,
        loop: Boolean = false,
    ) {
        RuntimeDiagnostics.record("PLAYER", "play-request", "started",
            "videoId=${d.video.id}, live=${d.isLive}, wantedHeight=$wantedHeight, startMs=$startMs, audioStreams=${d.audio.size}, muxedStreams=${d.muxed.size}, videoOnlyStreams=${d.videoOnly.size}")
        if (loop) _repeatMode.value = "one"
        loopRepeatEnabled = _repeatMode.value == "one"
        _looping.value = loopRepeatEnabled
        player.repeatMode = if (loopRepeatEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        _audioTrack.value = audioTrackId
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
            val audio = bestAudio(d.audio, audioTrackId)
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
        player.setPlaybackSpeed(_speed.value)
        player.playWhenReady = true

        if (!same) loadSegments(d.video.id)
        startWatching(d)
        RuntimeDiagnostics.record("PLAYER", "media-prepared", "ok", "videoId=${d.video.id}, selectedHeight=${_height.value}, resumeAt=$resumeAt")
        ContextCompat.startForegroundService(appContext, Intent(appContext, PlaybackService::class.java))
    }

    fun stop() {
        RuntimeDiagnostics.record("PLAYER", "stop-request", "started", "mediaId=${player.currentMediaItem?.mediaId ?: "none"}")
        saveProgress()
        watcher?.cancel(); saver?.cancel(); sleepJob?.cancel(); sleepJob = null; _sleepRemainingMs.value = null
        // Explicitly clear playWhenReady before clearing the playlist. MediaSessionService
        // observes the same player and must see a stopped session when the mini-player closes.
        player.playWhenReady = false
        player.pause()
        player.stop()
        player.clearMediaItems()
        _current.value = null
        _playing.value = false
        segments = emptyList()
        runCatching { appContext.stopService(Intent(appContext, PlaybackService::class.java)) }
        RuntimeDiagnostics.record("PLAYER", "stopped", "ok")
    }

    fun toggle() { if (player.playWhenReady) player.pause() else player.play() }

    fun setSpeed(s: Float) {
        val safe = s.coerceIn(0.25f, 3f)
        _speed.value = safe
        player.setPlaybackSpeed(safe)
    }

    fun seekBy(deltaMs: Long) {
        val duration = player.duration
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        player.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
    }

    fun setLooping(on: Boolean) { setRepeatMode(if (on) "one" else "continuous") }

    /** off = stop after this video, one = repeat current video, continuous = advance to next. */
    fun setRepeatMode(mode: String) {
        val safe = mode.takeIf { it in setOf("off", "one", "continuous") } ?: "continuous"
        _repeatMode.value = safe
        loopRepeatEnabled = safe == "one"
        _looping.value = safe == "one"
        player.repeatMode = if (safe == "one") Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        Graph.store.setAutoplayNext(safe == "continuous")
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) { _sleepRemainingMs.value = null; return }
        val duration = minutes.coerceIn(1, 240) * 60_000L
        _sleepRemainingMs.value = duration
        sleepJob = scope.launch {
            var left = duration
            while (left > 0L) {
                delay(1_000)
                left -= 1_000
                _sleepRemainingMs.value = left.coerceAtLeast(0L)
            }
            player.pause()
            _sleepRemainingMs.value = null
            sleepJob = null
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepRemainingMs.value = null
    }

    /** Enables or disables the native text renderer used by YouTube's original subtitle tracks. */
    fun setAudioOnly(on: Boolean) {
        _audioOnly.value = on
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, on)
            .build()
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
