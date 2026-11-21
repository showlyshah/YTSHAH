package com.viewtube.app.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil.imageLoader
import coil.request.ImageRequest
import com.viewtube.app.data.Graph
import com.viewtube.app.data.RuntimeDiagnostics
import com.viewtube.app.data.VideoItem
import com.viewtube.app.data.VideoDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Three isolated players in a ring, following the preloading design used by YouFlow's ShortsPlayer.
 * ViewTube's extractor and stream resolver remain the source of media data.
 */
@UnstableApi
class ShortsPlayerController private constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private class Slot(val player: ExoPlayer) {
        var videoId: String? = null
        var job: Job? = null
    }

    private val slots = List(SLOT_COUNT) { Slot(PlayerHolder.createShortsPlayer()) }
    private var focusedSlot = -1
    // Small LRU of extracted stream metadata; keeps the next swipe from repeating extraction.
    private val detailsCache = LinkedHashMap<String, VideoDetails>(5, 0.75f, true)
    private val _loading = MutableStateFlow<Set<Int>>(emptySet())
    val loading: StateFlow<Set<Int>> = _loading.asStateFlow()
    private val _failed = MutableStateFlow<Set<String>>(emptySet())
    val failed: StateFlow<Set<String>> = _failed.asStateFlow()
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    init {
        slots.forEachIndexed { index, slot ->
            slot.player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (index == focusedSlot) _playing.value = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _loading.value = _loading.value - index
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    _loading.value = _loading.value - index
                    slot.videoId?.let { id ->
                        _failed.value = _failed.value + id
                        RuntimeDiagnostics.record("SHORTS_PLAYER", "playback-error", "failure", "videoId=$id", error)
                    }
                }
            })
        }
    }

    private fun slotOf(page: Int) = page.mod(SLOT_COUNT)

    fun playerFor(page: Int): ExoPlayer = slots[slotOf(page)].player

    /** Focus the current short and prepare the adjacent pages before the next swipe. */
    fun focus(page: Int, videos: List<VideoItem>) {
        if (page !in videos.indices) return
        listOf(page, page + 1, page - 1).forEach { index ->
            videos.getOrNull(index)?.let { ensureLoaded(index, it) }
        }
        val activeSlot = slotOf(page)
        focusedSlot = activeSlot
        slots.forEachIndexed { index, slot ->
            slot.player.playWhenReady = index == activeSlot && slot.videoId == videos[page].id
        }
        _playing.value = slots[activeSlot].player.isPlaying
        // Warm metadata and thumbnails a little ahead, without keeping an unbounded queue in RAM.
        videos.getOrNull(page + 2)?.let { next ->
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val details = Graph.repo.details(next.id)
                    synchronized(detailsCache) {
                        detailsCache[next.id] = details
                        while (detailsCache.size > 5) detailsCache.remove(detailsCache.keys.first())
                    }
                }
            }
        }
        videos.drop(page + 1).take(3).mapNotNull { it.thumbnail }.distinct().forEach { url ->
            Graph.appContext.imageLoader.enqueue(
                ImageRequest.Builder(Graph.appContext).data(url).size(480, 480).build()
            )
        }
    }

    private fun ensureLoaded(page: Int, video: VideoItem) {
        val index = slotOf(page)
        val slot = slots[index]
        if (slot.videoId == video.id && video.id !in _failed.value) return
        slot.job?.cancel()
        slot.player.playWhenReady = false
        slot.player.stop()
        slot.player.clearMediaItems()
        slot.videoId = video.id
        _failed.value = _failed.value - video.id
        _loading.value = _loading.value + index
        slot.job = scope.launch {
            try {
                val details = synchronized(detailsCache) { detailsCache[video.id] }
                    ?: withContext(Dispatchers.IO) { Graph.repo.details(video.id) }.also { fetched ->
                        synchronized(detailsCache) {
                            detailsCache[video.id] = fetched
                            while (detailsCache.size > 5) detailsCache.remove(detailsCache.keys.first())
                        }
                    }
                val source = PlayerHolder.shortsMediaSource(details)
                slot.player.setMediaSource(source)
                slot.player.prepare()
                RuntimeDiagnostics.record("SHORTS_PLAYER", "prepared", "success", "videoId=${video.id}, slot=$index")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _loading.value = _loading.value - index
                _failed.value = _failed.value + video.id
                slot.videoId = null
                RuntimeDiagnostics.record("SHORTS_PLAYER", "prepare", "failure", "videoId=${video.id}", e)
            }
        }
    }

    fun togglePlayPause(page: Int) {
        val player = playerFor(page)
        if (player.playWhenReady) player.pause() else player.play()
        _playing.value = player.playWhenReady
    }

    fun stop() {
        slots.forEach { slot ->
            slot.job?.cancel()
            slot.job = null
            slot.player.playWhenReady = false
            slot.player.stop()
            slot.player.clearMediaItems()
            slot.videoId = null
        }
        _loading.value = emptySet()
        synchronized(detailsCache) { detailsCache.clear() }
        focusedSlot = -1
        _playing.value = false
    }

    companion object {
        private const val SLOT_COUNT = 3
        @Volatile private var instance: ShortsPlayerController? = null

        fun get(): ShortsPlayerController = instance ?: synchronized(this) {
            instance ?: ShortsPlayerController().also { instance = it }
        }
    }
}
