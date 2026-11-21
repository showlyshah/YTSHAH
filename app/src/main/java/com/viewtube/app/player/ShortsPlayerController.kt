package com.viewtube.app.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.viewtube.app.data.Graph
import com.viewtube.app.data.RuntimeDiagnostics
import com.viewtube.app.data.VideoItem
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
    private val _loading = MutableStateFlow<Set<Int>>(emptySet())
    val loading: StateFlow<Set<Int>> = _loading.asStateFlow()
    private val _failed = MutableStateFlow<Set<String>>(emptySet())
    val failed: StateFlow<Set<String>> = _failed.asStateFlow()

    init {
        slots.forEachIndexed { index, slot ->
            slot.player.addListener(object : Player.Listener {
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
        slots.forEachIndexed { index, slot ->
            slot.player.playWhenReady = index == activeSlot && slot.videoId == videos[page].id
        }
        // Resolve the next stream details one page further ahead while the adjacent player buffers.
        videos.getOrNull(page + 2)?.let { next ->
            scope.launch(Dispatchers.IO) { runCatching { Graph.repo.details(next.id) } }
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
                val details = withContext(Dispatchers.IO) { Graph.repo.details(video.id) }
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
    }

    companion object {
        private const val SLOT_COUNT = 3
        @Volatile private var instance: ShortsPlayerController? = null

        fun get(): ShortsPlayerController = instance ?: synchronized(this) {
            instance ?: ShortsPlayerController().also { instance = it }
        }
    }
}
