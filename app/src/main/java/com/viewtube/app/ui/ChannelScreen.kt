package com.viewtube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.viewtube.app.data.ChannelPage
import com.viewtube.app.data.Graph
import com.viewtube.app.data.Paged
import com.viewtube.app.data.VideoItem
import com.viewtube.app.data.errorText
import kotlinx.coroutines.launch

@Composable
fun ChannelScreen(url: String, onBack: () -> Unit, onVideo: (VideoItem) -> Unit, onChannel: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val subs by Graph.store.subs.collectAsStateCompat()
    var page by remember { mutableStateOf<ChannelPage?>(null) }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var next by remember { mutableStateOf<(suspend () -> Paged<VideoItem>)?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState()

    LaunchedEffect(url, attempt) {
        error = null
        runCatching { Graph.repo.channel(url) }
            .onSuccess { page = it; videos = it.videos.items; next = it.videos.next }
            .onFailure { error = errorText(it) }
    }
    LoadMoreEffect(list, next != null && !loadingMore) {
        val n = next ?: return@LoadMoreEffect
        loadingMore = true
        scope.launch {
            runCatching { n() }.onSuccess { videos = (videos + it.items).distinctBy { video -> video.id }; next = it.next }
            loadingMore = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(page?.channel?.name ?: "", fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        val p = page
        when {
            error != null && p == null -> Message("Couldn't open this channel", error, "Try again") { attempt++ }
            p == null -> Loading()
            else -> LazyColumn(Modifier.fillMaxSize(), state = list) {
                item {
                    if (p.channel.banner != null) {
                        AsyncImage(p.channel.banner, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(90.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                    }
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p.channel.avatar, 72)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(p.channel.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            if (p.channel.subscribers >= 0) Text("${formatCount(p.channel.subscribers)} subscribers", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        SubscribeButton(p.channel, subs.any { it.id == p.channel.id })
                    }
                    Text("Videos", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
                }
                items(videos, key = { it.id }) { v -> VideoCard(v, { onVideo(v) }, onChannel) }
                if (loadingMore) item { Loading(Modifier.fillMaxWidth().height(64.dp)) }
            }
        }
    }
}
