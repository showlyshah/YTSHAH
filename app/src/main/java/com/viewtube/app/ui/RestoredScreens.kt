package com.viewtube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.viewtube.app.data.ChannelItem
import com.viewtube.app.data.FILTER_CHANNELS
import com.viewtube.app.data.LocationRecommendations
import com.viewtube.app.data.FeedDiagnostics
import com.viewtube.app.data.Graph
import com.viewtube.app.data.Paged
import com.viewtube.app.data.RuntimeDiagnostics
import com.viewtube.app.data.VideoItem
import com.viewtube.app.player.PlayerHolder
import com.viewtube.app.player.ShortsPlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal object FeedSessionCache {
    var homeCategory: String = "All"
    var homeVideos: List<VideoItem> = emptyList()
    var homeMixer: com.viewtube.app.data.FeedMixer? = null
    var homeLoaded: Boolean = false
    var shortsVideos: List<VideoItem> = emptyList()
    var shortsNext: (suspend () -> Paged<VideoItem>)? = null
    var shortsLoaded: Boolean = false
    var shortsPage: Int = 0
}

@Composable
fun MiniPlayer(onOpen: (String) -> Unit) {
    val current by PlayerHolder.current.collectAsStateCompat()
    val playing by PlayerHolder.playing.collectAsStateCompat()
    val details = current ?: return
    Row(Modifier.fillMaxWidth().clickable { onOpen(details.video.id) }.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = YtRed)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(details.video.title, maxLines = 1, fontWeight = FontWeight.Medium)
            Text(details.channel.name, maxLines = 1, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { PlayerHolder.toggle() }, modifier = Modifier.size(36.dp)) {
            Text(if (playing) "Ⅱ" else "▶", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        IconButton(onClick = { PlayerHolder.stop() }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close player", modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun AppMiniPlayer(onOpenVideo: (String) -> Unit, onOpenShorts: () -> Unit) {
    val shortsPlayer = remember { ShortsPlayerController.get() }
    val shortsMinimized by shortsPlayer.minimized.collectAsStateCompat()
    if (shortsMinimized) {
        ShortsMiniPlayer(onOpenShorts)
    } else {
        MiniPlayer(onOpenVideo)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ShortsMiniPlayer(onOpen: () -> Unit) {
    val shortsPlayer = remember { ShortsPlayerController.get() }
    val video by shortsPlayer.currentVideo.collectAsStateCompat()
    val playing by shortsPlayer.playing.collectAsStateCompat()
    val minimized by shortsPlayer.minimized.collectAsStateCompat()
    if (video == null || !minimized) return
    val current = video ?: return
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        coil.compose.AsyncImage(
            model = current.thumbnail,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(width = 58.dp, height = 42.dp)
        )
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(current.title, maxLines = 1, fontWeight = FontWeight.Medium)
            Text(current.channelName.ifBlank { "Shorts" }, maxLines = 1, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { shortsPlayer.toggleCurrentPlayback() }, modifier = Modifier.size(36.dp)) {
            Text(if (playing) "Ⅱ" else "▶", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        IconButton(onClick = { shortsPlayer.stop() }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close Shorts mini-player", modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(UnstableApi::class)
@Suppress("UNUSED_PARAMETER")
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ShortsScreen(onChannel: (String) -> Unit, onVideo: (VideoItem) -> Unit, onMinimize: () -> Unit) {
    val scope = rememberCoroutineScope()
    val shortsPlayer = remember { ShortsPlayerController.get() }
    val loadingSlots by shortsPlayer.loading.collectAsStateCompat()
    val failed by shortsPlayer.failed.collectAsStateCompat()
    val shortsPlaying by shortsPlayer.playing.collectAsStateCompat()
    var videos by remember { mutableStateOf(FeedSessionCache.shortsVideos) }
    var nextPage by remember { mutableStateOf(FeedSessionCache.shortsNext) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = FeedSessionCache.shortsPage, pageCount = { videos.size })

    LaunchedEffect(reload) {
        if (reload == 0 && FeedSessionCache.shortsLoaded) {
            videos = FeedSessionCache.shortsVideos
            nextPage = FeedSessionCache.shortsNext
            loading = false
            error = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        videos = emptyList()
        nextPage = null
        val started = android.os.SystemClock.elapsedRealtime()
        runCatching {
            kotlinx.coroutines.withTimeout(30_000) {
                val localChannels = LocationRecommendations.area?.let { area ->
                    runCatching {
                        Graph.repo.search("shorts creators $area", FILTER_CHANNELS).items
                            .filterIsInstance<ChannelItem>().map { it.url }.take(5)
                    }.getOrDefault(emptyList())
                }.orEmpty()
                Graph.repo.shorts(localChannels + Graph.store.subs.value.map { it.url })
            }
        }.onSuccess { page ->
            videos = page.items.distinctBy { it.id }
            nextPage = page.next
            FeedSessionCache.shortsVideos = videos
            FeedSessionCache.shortsNext = nextPage
            FeedSessionCache.shortsLoaded = true
            FeedSessionCache.shortsPage = 0
            FeedDiagnostics.record("shorts", "initial-feed", if (videos.isEmpty()) "empty" else "success",
                android.os.SystemClock.elapsedRealtime() - started, videos.size,
                detail = "source=channel-shorts-tabs, three-slot-player=true, continuation=${page.next != null}")
            if (videos.isEmpty()) error = "No Shorts were found in the available channel Shorts tabs."
        }.onFailure {
            if (it is CancellationException) throw it
            error = it.message ?: "Could not load Shorts. Check your connection and retry."
            FeedDiagnostics.record("shorts", "initial-feed", "failure",
                android.os.SystemClock.elapsedRealtime() - started, error = it)
        }
        loading = false
    }

    fun loadMore() {
        val continuation = nextPage ?: return
        if (loadingMore) return
        loadingMore = true
        scope.launch {
            runCatching { continuation() }
                .onSuccess { page ->
                    val known = videos.mapTo(HashSet()) { it.id }
                    videos = videos + page.items.filter { known.add(it.id) }
                    nextPage = page.next
                    FeedSessionCache.shortsVideos = videos
                    FeedSessionCache.shortsNext = nextPage
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    error = it.message ?: "Could not load more Shorts."
                }
            loadingMore = false
        }
    }

    LaunchedEffect(pagerState.currentPage, videos.size) {
        if (videos.isNotEmpty()) FeedSessionCache.shortsPage = pagerState.currentPage
        if (videos.isNotEmpty() && pagerState.currentPage >= videos.size - 3) loadMore()
    }

    LaunchedEffect(pagerState.currentPage, videos) {
        if (videos.isNotEmpty()) shortsPlayer.focus(pagerState.currentPage, videos)
    }

    DisposableEffect(shortsPlayer) {
        // A Short has its own isolated player; pause the regular player while the Shorts feed is open.
        PlayerHolder.player.playWhenReady = false
        onDispose {
            // Keep the isolated Shorts players alive only when the user explicitly minimized them.
            if (!shortsPlayer.minimized.value) shortsPlayer.stop()
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .pointerInput(onMinimize) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var downwardDistance = 0f
                    var minimizedThisGesture = false
                    while (!minimizedThisGesture) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        downwardDistance += change.positionChange().y.coerceAtLeast(0f)
                        if (downwardDistance >= 112.dp.toPx()) {
                            change.consume()
                            minimizedThisGesture = true
                            onMinimize()
                        }
                    }
                }
            }
    ) {
        when {
            loading && videos.isEmpty() -> Loading()
            videos.isEmpty() -> Message(
                "Couldn't load Shorts",
                error ?: "Subscribe to channels with Shorts or try again.",
                "Try again"
            ) { reload++ }
            else -> androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)
            ) {
                androidx.compose.foundation.pager.VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { page -> videos[page].id }
                ) { page ->
                    val video = videos[page]
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                        if (page == pagerState.currentPage) {
                            AndroidView(
                                factory = { context ->
                                    PlayerView(context).apply {
                                        player = shortsPlayer.playerFor(page)
                                        keepScreenOn = shortsPlaying
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        useController = false
                                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                                    }
                                },
                                update = { view ->
                                    if (view.player !== shortsPlayer.playerFor(page)) {
                                        view.player = shortsPlayer.playerFor(page)
                                    }
                                    view.keepScreenOn = shortsPlaying
                                },
                                modifier = Modifier.fillMaxSize().clickable {
                                    shortsPlayer.togglePlayPause(page)
                                }
                            )
                            if (loadingSlots.contains(page.mod(3))) {
                                Text(
                                    "Loading Short…",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            if (video.id in failed) {
                                Text(
                                    "This Short couldn't be played. Swipe for the next one.",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                                )
                            }
                        } else {
                            coil.compose.AsyncImage(
                                model = video.thumbnail,
                                contentDescription = video.title,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column(
                            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.58f))
                                .padding(horizontal = 18.dp, vertical = 20.dp)
                        ) {
                            Text(
                                video.channelName.ifBlank { "Shorts" },
                                color = androidx.compose.ui.graphics.Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.clickable(enabled = !video.channelUrl.isNullOrBlank()) {
                                    video.channelUrl?.let(onChannel)
                                }
                            )
                            Text(
                                video.title,
                                color = androidx.compose.ui.graphics.Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 6.dp),
                                maxLines = 3
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 142.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { shortsPlayer.togglePlayPause(pagerState.currentPage) },
                        modifier = Modifier.background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.CircleShape)
                    ) {
                        Icon(
                            if (shortsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (shortsPlaying) "Pause Short" else "Play Short",
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val next = pagerState.currentPage + 1
                                if (next < videos.size) pagerState.animateScrollToPage(next)
                                else loadMore()
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.CircleShape)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next Short", tint = androidx.compose.ui.graphics.Color.White)
                    }
                }
                if (loadingMore) {
                    Text("Loading more Shorts…", color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.align(Alignment.TopCenter).padding(12.dp))
                }
                if (error != null && videos.isNotEmpty()) {
                    Text(error!!, color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.align(Alignment.TopCenter).padding(12.dp))
                }
            }
        }
    }
}

@Composable
fun CreatorsScreen(onSearch: () -> Unit, onAccount: () -> Unit, onChannel: (String) -> Unit) {
    val channels by Graph.store.subs.collectAsStateCompat()
    Column(Modifier.fillMaxSize()) {
        TopBar(onSearch, onAccount)
        Text("Your creators", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
        if (channels.isEmpty()) Message("No creators yet", "Subscribe to channels while watching videos to see them here.")
        else LazyColumn(Modifier.fillMaxSize()) { items(channels, key = { it.id }) { channel -> ChannelRow(channel) { onChannel(channel.url) } } }
    }
}

@Composable
fun YouScreen(onVideo: (VideoItem) -> Unit, onHistory: () -> Unit, onWatchLater: () -> Unit, onSubscriptions: () -> Unit, onSettings: () -> Unit) {
    val name by Graph.store.profileName.collectAsStateCompat()
    val history by Graph.store.history.collectAsStateCompat()
    val later by Graph.store.later.collectAsStateCompat()
    val subs by Graph.store.subs.collectAsStateCompat()
    Column(Modifier.fillMaxSize()) {
        Text("You", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
        Text(name.ifBlank { "Your ViewTube" }, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 20.dp))
        Text("${subs.size} subscriptions  •  ${history.size} watched  •  ${later.size} saved", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 16.dp))
        HubAction("History", history.size.toString(), Icons.Default.History, onHistory)
        HubAction("Watch later", later.size.toString(), Icons.Default.VideoLibrary, onWatchLater)
        HubAction("Subscriptions", subs.size.toString(), Icons.Default.Subscriptions, onSubscriptions)
        HubAction("Settings & account", null, Icons.Default.Settings, onSettings)
        if (history.isNotEmpty()) {
            Text("Recently watched", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
            history.take(5).forEach { video -> VideoRow(video) { onVideo(video) } }
        }
    }
}

@Composable
private fun HubAction(label: String, detail: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = YtRed)
        Text(label, modifier = Modifier.weight(1f).padding(start = 16.dp), fontWeight = FontWeight.Medium)
        if (detail != null) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun VideoListScreen(title: String, history: Boolean, onBack: () -> Unit, onVideo: (VideoItem) -> Unit) {
    val videos by (if (history) Graph.store.history else Graph.store.later).collectAsStateCompat()
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        if (videos.isEmpty()) Message(if (history) "No watch history" else "Nothing saved yet")
        else LazyColumn(Modifier.fillMaxSize()) { items(videos, key = { it.id }) { video -> VideoRow(video) { onVideo(video) } } }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) { AccountScreen(onBack) }
