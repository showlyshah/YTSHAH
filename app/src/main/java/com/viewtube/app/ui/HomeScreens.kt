package com.viewtube.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viewtube.app.data.ChannelItem
import com.viewtube.app.data.Graph
import com.viewtube.app.data.Item
import com.viewtube.app.data.LocalRecommendationEngine
import com.viewtube.app.data.Paged
import com.viewtube.app.data.VideoItem
import com.viewtube.app.data.errorText
import kotlinx.coroutines.launch

/** Red play-button logo + name, like the YouTube app bar. */
@Composable
fun TopBar(onSearch: () -> Unit, onAccount: () -> Unit) {
    val name by Graph.store.profileName.collectAsStateCompat()
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(YtRed).padding(horizontal = 6.dp, vertical = 3.dp)) {
            Text("▶", color = Color.White, fontSize = 12.sp)
        }
        Text("ViewTube", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(start = 6.dp).weight(1f))
        IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "Search") }
        IconButton(onClick = onAccount) {
            Avatar(null, 30)
        }
        if (name.isNotBlank()) Spacer(Modifier.width(0.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onAccount: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onChannel: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val subs by Graph.store.subs.collectAsStateCompat()
    val personalized by Graph.store.personalizedRecommendations.collectAsStateCompat()
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload, subs.size, personalized) {
        error = null
        if (videos.isEmpty()) loading = true
        runCatching {
            val trending = Graph.repo.trending()
            val fromSubs = if (subs.isEmpty()) emptyList() else Graph.repo.feed(subs.map { it.id }, 3).take(20)
            (fromSubs + trending).distinctBy { it.id }
        }.onSuccess { videos = if (personalized) LocalRecommendationEngine.rank(it, Graph.store.history.value, subs) else it }.onFailure { if (videos.isEmpty()) error = errorText(it) }
        loading = false
        refreshing = false
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(onSearch, onAccount)
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; reload++ }, modifier = Modifier.fillMaxSize()) {
            when {
                loading -> Loading()
                error != null -> Message("Couldn't load videos", error, "Try again") { reload++ }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(videos, key = { it.id }) { v -> VideoCard(v, { onVideo(v) }, onChannel) }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(initialQuery: String, onBack: () -> Unit, onVideo: (VideoItem) -> Unit, onChannel: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    var submitted by remember { mutableStateOf(initialQuery) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var results by remember { mutableStateOf<List<Item>>(emptyList()) }
    var next by remember { mutableStateOf<(suspend () -> Paged<Item>)?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()

    fun run(q: String) {
        if (q.isBlank()) return
        submitted = q; query = q; suggestions = emptyList()
        scope.launch {
            loading = true; error = null
            runCatching { Graph.repo.search(q) }
                .onSuccess { results = it.items; next = it.next }
                .onFailure { error = errorText(it); results = emptyList(); next = null }
            loading = false
        }
    }

    LaunchedEffect(Unit) { if (initialQuery.isNotBlank()) run(initialQuery) }
    LaunchedEffect(query) {
        if (query == submitted || query.isBlank()) { suggestions = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(250)
        suggestions = Graph.repo.suggestions(query).take(8)
    }

    LoadMoreEffect(list, next != null && !loadingMore) {
        val n = next ?: return@LoadMoreEffect
        loadingMore = true
        scope.launch {
            runCatching { n() }.onSuccess { results = results + it.items; next = it.next }.onFailure { next = null }
            loadingMore = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        SearchBar(query, onChange = { query = it }, onSubmit = { run(query) }, onBack = onBack)
        when {
            suggestions.isNotEmpty() -> LazyColumn {
                items(suggestions) { s ->
                    Row(Modifier.fillMaxWidth().clickable { run(s) }.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Text(s, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
            loading -> Loading()
            error != null -> Message("Search failed", error, "Try again") { run(submitted) }
            submitted.isNotBlank() && results.isEmpty() -> Message("No results for \"$submitted\"")
            else -> LazyColumn(Modifier.fillMaxSize(), state = list) {
                items(results.size) { i ->
                    when (val it = results[i]) {
                        is VideoItem -> VideoRow(it) { onVideo(it) }
                        is ChannelItem -> ChannelRow(it) { onChannel(it.url) }
                    }
                }
                if (loadingMore) item { Loading(Modifier.fillMaxWidth().height(64.dp)) }
            }
        }
    }
}

@Composable
fun SubscriptionsScreen(onSearch: () -> Unit, onAccount: () -> Unit, onVideo: (VideoItem) -> Unit, onChannel: (String) -> Unit) {
    val subs by Graph.store.subs.collectAsStateCompat()
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(subs.size, reload) {
        loading = subs.isNotEmpty()
        videos = if (subs.isEmpty()) emptyList() else Graph.repo.feed(subs.map { it.id })
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(onSearch, onAccount)
        if (subs.isEmpty()) {
            Message(
                "Nothing here yet",
                "Subscribe to channels, or import your subscriptions from Google Takeout in Account."
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                        items(subs, key = { it.id }) { c ->
                            Column(Modifier.width(64.dp).clickable { onChannel(c.url) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Avatar(c.avatar, 52)
                                Text(c.name, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                if (loading) item { Loading(Modifier.fillMaxWidth().height(200.dp)) }
                items(videos, key = { it.id }) { v -> VideoCard(v, { onVideo(v) }, onChannel) }
            }
        }
    }
}
