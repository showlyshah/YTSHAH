package com.viewtube.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import com.viewtube.app.data.ChannelItem
import com.viewtube.app.data.FeedMixer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.viewtube.app.data.Graph
import com.viewtube.app.data.VideoItem
import com.viewtube.app.player.PlayerHolder

@androidx.annotation.OptIn(UnstableApi::class)
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
        IconButton(onClick = { PlayerHolder.toggle() }, modifier = Modifier.size(40.dp)) {
            Text(if (playing) "Ⅱ" else "▶", fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = { PlayerHolder.stop() }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close mini player")
        }
    }
}

@Composable
fun ShortsScreen(onSearch: () -> Unit, onAccount: () -> Unit, onChannel: (String) -> Unit, onVideo: (VideoItem) -> Unit) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var canLoadMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val mixer = remember(reload) {
        FeedMixer(
            listOf("shorts", "popular shorts", "viral shorts", "music shorts", "gaming shorts", "funny shorts", "science shorts", "Malayalam shorts"),
            Graph.repo,
        ) { it.isShort }
    }

    LaunchedEffect(reload) {
        loading = true
        error = null
        loadingMore = false
        videos = emptyList()
        try {
            videos = mixer.more(20)
            canLoadMore = !mixer.exhausted && mixer.lastError == null
            error = mixer.lastError?.message
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Could not load Shorts."
            canLoadMore = false
        } finally {
            loading = false
        }
    }

    LoadMoreEffect(listState, canLoadMore && !loadingMore) {
        loadingMore = true
        scope.launch {
            try {
                val page = mixer.more(12)
                if (page.isNotEmpty()) videos = (videos + page).distinctBy { it.id }
                canLoadMore = !mixer.exhausted && mixer.lastError == null
                error = mixer.lastError?.message
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Could not load more Shorts."
                canLoadMore = false
            } finally {
                loadingMore = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(onSearch = onSearch, onAccount = onAccount)
        when {
            loading && videos.isEmpty() -> Loading()
            videos.isEmpty() -> Message("No Shorts available", error ?: "Try again in a moment.", "Try again") { reload++ }
            else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(videos, key = { it.id }) { video -> VideoCard(video, { onVideo(video) }, onChannel) }
                if (loadingMore) item { Loading() }
                if (error != null) item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            }
        }
    }
}

@Composable
fun CreatorsScreen(onSearch: () -> Unit, onAccount: () -> Unit, onChannel: (String) -> Unit) {
    val channels by Graph.store.subs.collectAsStateCompat()
    var discovered by remember { mutableStateOf<List<ChannelItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        val topics = listOf("popular YouTube creators", "technology channels", "science channels", "music channels")
        val found = mutableListOf<ChannelItem>()
        topics.forEach { topic ->
            try {
                found += Graph.repo.search(topic).items.filterIsInstance<ChannelItem>()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep results from other creator categories if one search fails.
            }
        }
        discovered = found.distinctBy { it.id }.filterNot { channel -> channels.any { it.id == channel.id } }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(onSearch, onAccount)
        LazyColumn(Modifier.fillMaxSize()) {
            item { Text("Your creators", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp)) }
            if (channels.isEmpty()) item { Message("No creators yet", "Subscribe to channels while watching videos to see them here.") }
            else items(channels, key = { "sub-" + it.id }) { channel -> ChannelRow(channel) { onChannel(channel.url) } }
            item { Text("Discover creators", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp)) }
            if (loading) item { Loading() }
            else if (discovered.isEmpty()) item { Message("No creators found", "Try searching for a creator or topic.") }
            else items(discovered, key = { "discover-" + it.id }) { channel -> ChannelRow(channel) { onChannel(channel.url) } }
        }
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
