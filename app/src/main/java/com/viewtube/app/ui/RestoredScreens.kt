package com.viewtube.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.viewinterop.AndroidView
import com.viewtube.app.data.Graph
import com.viewtube.app.data.FeedDiagnostics
import com.viewtube.app.data.RuntimeDiagnostics
import com.viewtube.app.data.Paged
import com.viewtube.app.data.VideoItem
import com.viewtube.app.player.PlayerHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
        IconButton(onClick = { PlayerHolder.playNext { nextId -> onOpen(nextId) } }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.SkipNext, contentDescription = "Play next recommended video", modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { PlayerHolder.stop() }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close player", modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ShortsScreen(onChannel: (String) -> Unit, onVideo: (VideoItem) -> Unit) {
    DisposableEffect(Unit) {
        RuntimeDiagnostics.record("SHORTS_UI", "screen-enter", "ok")
        onDispose { RuntimeDiagnostics.record("SHORTS_UI", "screen-dispose", "ok") }
    }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var nextPage by remember { mutableStateOf<(suspend () -> Paged<VideoItem>)?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { videos.size })

    LaunchedEffect(reload) {
        RuntimeDiagnostics.record("SHORTS_UI", "initial-load", "started", "reload=$reload subscriptions=${Graph.store.subs.value.size}")
        loading = true
        error = null
        videos = emptyList()
        nextPage = null
        val started = android.os.SystemClock.elapsedRealtime()
        runCatching {
            kotlinx.coroutines.withTimeout(30_000) {
                Graph.repo.shorts(Graph.store.subs.value.map { it.url })
            }
        }.onSuccess { page ->
            videos = page.items.distinctBy { it.id }
            nextPage = page.next
            RuntimeDiagnostics.record("SHORTS_UI", "initial-load-results", if (videos.isEmpty()) "empty" else "success", "videos=${videos.size}, hasContinuation=${page.next != null}")
            FeedDiagnostics.record("shorts", "initial-feed", if (videos.isEmpty()) "empty" else "success",
                android.os.SystemClock.elapsedRealtime() - started, videos.size,
                detail = "verticalPager=true, inlinePlayer=true, hasContinuation=${page.next != null}")
        }.onFailure {
            if (it is CancellationException) throw it
            FeedDiagnostics.record("shorts", "initial-feed", "failure",
                android.os.SystemClock.elapsedRealtime() - started, error = it)
            RuntimeDiagnostics.record("SHORTS_UI", "initial-load", "failure", error = it)
            error = it.message ?: "Could not load Shorts. Check your connection and retry."
        }
        loading = false
        RuntimeDiagnostics.record("SHORTS_UI", "initial-load-finished", if (videos.isEmpty()) "empty" else "success", "videos=${videos.size}, errorPresent=${error != null}")
    }

    fun loadMore() {
        val fetch = nextPage ?: return
        if (loadingMore) return
        scope.launch {
            RuntimeDiagnostics.record("SHORTS_UI", "pagination-request", "started", "currentVideos=${videos.size}")
            loadingMore = true
            val started = android.os.SystemClock.elapsedRealtime()
            runCatching { fetch() }
                .onSuccess { page ->
                    val before = videos.size
                    videos = (videos + page.items).distinctBy { it.id }
                    nextPage = page.next
                    RuntimeDiagnostics.record("SHORTS_UI", "pagination-results", if (page.items.isEmpty()) "empty" else "success", "newItems=${videos.size - before}, total=${videos.size}, hasContinuation=${page.next != null}")
                    FeedDiagnostics.record("shorts", "next-page", if (page.items.isEmpty()) "empty" else "success",
                        android.os.SystemClock.elapsedRealtime() - started, page.items.size,
                        detail = "total=${videos.size}, hasContinuation=${page.next != null}")
                    error = null
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    FeedDiagnostics.record("shorts", "next-page", "failure",
                        android.os.SystemClock.elapsedRealtime() - started, error = it)
                    error = it.message ?: "Could not load more Shorts."
                }
            loadingMore = false
            RuntimeDiagnostics.record("SHORTS_UI", "pagination-finished", if (error == null) "complete" else "failure", "totalVideos=${videos.size}")
        }
    }

    LaunchedEffect(pagerState.currentPage, videos.size) {
        if (videos.isNotEmpty() && pagerState.currentPage >= videos.size - 3) loadMore()
    }

    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        when {
            loading && videos.isEmpty() -> Loading()
            videos.isEmpty() -> Message(
                if (error != null) "Couldn't load Shorts" else "No Shorts found",
                error ?: "The source did not return any videos identified as Shorts.",
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
                            LaunchedEffect(video.id) {
                                RuntimeDiagnostics.record("SHORTS_UI", "inline-play-request", "started", "videoId=${video.id}")
                                runCatching { Graph.repo.details(video.id) }
                                    .onSuccess { details ->
                                        Graph.store.addHistory(details.video)
                                        runCatching { PlayerHolder.play(details) }
                                            .onSuccess {
                                                RuntimeDiagnostics.record("SHORTS_UI", "inline-play", "success", "videoId=${video.id}")
                                            }
                                            .onFailure {
                                                error = it.message ?: "This Short could not be played."
                                                RuntimeDiagnostics.record("SHORTS_UI", "inline-play", "failure", "videoId=${video.id}", it)
                                            }
                                    }
                                    .onFailure {
                                        error = it.message ?: "Could not load this Short."
                                        RuntimeDiagnostics.record("SHORTS_UI", "inline-details", "failure", "videoId=${video.id}", it)
                                    }
                            }
                            AndroidView(
                                factory = { context ->
                                    PlayerView(context).apply {
                                        player = PlayerHolder.player
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        useController = true
                                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                                    }
                                },
                                update = { view ->
                                    if (view.player !== PlayerHolder.player) view.player = PlayerHolder.player
                                },
                                modifier = Modifier.fillMaxSize()
                            )
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
                                modifier = Modifier.clickable(enabled = !video.channelUrl.isNullOrBlank()) { video.channelUrl?.let(onChannel) }
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
                if (loadingMore) {
                    Text("Loading more Shorts…", color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.align(Alignment.TopCenter).padding(12.dp))
                }
                error?.let {
                    Text(it, color = androidx.compose.ui.graphics.Color.White,
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
