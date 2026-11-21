package com.viewtube.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Pause
import coil.compose.AsyncImage
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viewtube.app.data.Graph
import com.viewtube.app.data.Paged
import com.viewtube.app.data.VideoItem
import com.viewtube.app.player.PlayerHolder
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
        IconButton(onClick = { PlayerHolder.stop() }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close player", modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen(onChannel: (String) -> Unit, onVideo: (VideoItem) -> Unit) {
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var nextPage by remember { mutableStateOf<(suspend () -> Paged<VideoItem>)?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var activeLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { videos.size })
    val activeVideo = videos.getOrNull(pagerState.currentPage)
    val playing by PlayerHolder.playing.collectAsStateCompat()

    LaunchedEffect(Unit) {
        runCatching { Graph.repo.shorts() }
            .onSuccess { page ->
                videos = page.items.filter { it.isShort }.distinctBy { it.id }
                nextPage = page.next
                error = null
            }
            .onFailure { error = it.message ?: "Could not load Shorts." }
        loading = false
    }

    fun loadMore() {
        val fetch = nextPage ?: return
        if (loadingMore) return
        scope.launch {
            loadingMore = true
            runCatching { fetch() }
                .onSuccess { page ->
                    videos = (videos + page.items.filter { it.isShort }).distinctBy { it.id }
                    nextPage = page.next
                    error = null
                }
                .onFailure { error = it.message ?: "Could not load more Shorts." }
            loadingMore = false
        }
    }

    LaunchedEffect(activeVideo?.id) {
        val selected = activeVideo ?: return@LaunchedEffect
        activeLoading = true
        activeError = null
        try {
            val details = Graph.repo.details(selected.id)
            if (activeVideo?.id == selected.id) PlayerHolder.play(details)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            activeError = t.message ?: "This Short could not be played."
        } finally {
            activeLoading = false
        }
    }

    LaunchedEffect(pagerState.currentPage, videos.size) {
        if (videos.isNotEmpty() && pagerState.currentPage >= videos.lastIndex - 1) loadMore()
    }

    when {
        loading -> Loading()
        videos.isEmpty() -> Message("No Shorts found", error ?: "The source did not return any videos identified as Shorts.")
        else -> Box(Modifier.fillMaxSize().background(Color.Black)) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { index -> videos[index].id }
            ) { page ->
                val video = videos[page]
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (page == pagerState.currentPage) {
                        AndroidView(
                            factory = { context ->
                                PlayerView(context).apply {
                                    player = PlayerHolder.player
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                }
                            },
                            update = { it.player = PlayerHolder.player },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = video.thumbnail,
                            contentDescription = video.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Column(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .background(Color(0x99000000)).padding(start = 18.dp, end = 18.dp, top = 48.dp, bottom = 26.dp)
                    ) {
                        Text(video.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 3)
                        Text(
                            video.channelName,
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp).clickable {
                                video.channelUrl?.let(onChannel)
                            }
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (activeLoading && page == pagerState.currentPage) "Loading Short…" else activeError ?: "",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { PlayerHolder.toggle() }) {
                                Icon(
                                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    tint = Color.White
                                )
                            }
                            Button(onClick = { onVideo(video) }) { Text("Details") }
                        }
                    }
                    if (page == 0) {
                        Text(
                            "SHORTS  ·  Swipe up for next",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.TopStart).padding(18.dp)
                                .background(Color(0x77000000), RoundedCornerShape(18.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            if (loadingMore) Text(
                "Loading more…",
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
            )
            error?.let {
                Text(it, color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(12.dp))
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
