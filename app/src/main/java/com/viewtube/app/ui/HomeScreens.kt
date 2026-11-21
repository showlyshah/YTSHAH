package com.viewtube.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.viewtube.app.data.LocationRecommendations
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viewtube.app.data.ChannelItem
import com.viewtube.app.data.FILTER_VIDEOS
import com.viewtube.app.data.FeedDiagnostics
import com.viewtube.app.data.FeedMixer
import com.viewtube.app.data.Graph
import com.viewtube.app.data.Item
import com.viewtube.app.data.LocalRecommendationEngine
import com.viewtube.app.data.Paged
import com.viewtube.app.data.RuntimeDiagnostics
import com.viewtube.app.data.VideoItem
import com.viewtube.app.data.errorText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Red play-button logo + name, like the YouTube app bar. */
@Composable
fun TopBar(onSearch: () -> Unit, onAccount: () -> Unit) {
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
    }
}

private val CATEGORIES = listOf("All", "Music", "Gaming", "Live", "News", "Podcasts", "Movies", "Sports", "Cooking", "Learning", "Comedy")

private fun topicsFor(category: String, area: String? = null): List<String> {
    if (category != "All") return listOf(category, "$category new", "$category popular", "best $category") + area?.let { listOf("$category $it", "$category events $it") }.orEmpty()
    val store = Graph.store
    val personal = (store.subs.value.shuffled().take(5).map { it.name } +
        store.history.value.take(8).map { it.channelName }.distinct().take(4) +
        store.searchHistory.value.take(3)).filter { it.isNotBlank() }
    val local = area?.let { listOf("trending videos $it", "local news $it", "events and culture $it") }.orEmpty()
    return personal + local + listOf("trending", "music", "gaming", "news today", "technology", "science", "movies trailers", "sports highlights")
}

@Composable
fun CategoryChips(selected: String, onSelect: (String) -> Unit, items: List<String> = CATEGORIES) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        items(items) { c ->
            val on = c == selected
            Text(
                c, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                color = if (on) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (on) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(c) }.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
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
    val context = LocalContext.current
    var localArea by remember { mutableStateOf<String?>(null) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { localArea = LocationRecommendations.refresh(context) }
    }
    val list = rememberLazyListState()
    var category by remember { mutableStateOf("All") }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var mixer by remember { mutableStateOf<FeedMixer?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) localArea = LocationRecommendations.refresh(context)
    }

    LaunchedEffect(category, reload, localArea) {
        error = null
        if (!refreshing) { loading = true; videos = emptyList() }
        val m = FeedMixer(topicsFor(category, localArea), Graph.repo) { !it.isShort && it.durationSec != 0L }
        mixer = m
        val subs = Graph.store.subs.value
        val fromSubs = if (category == "All" && subs.isNotEmpty())
            runCatching { Graph.repo.feed(subs.map { it.id }.shuffled().take(25), 2).take(12) }.getOrDefault(emptyList())
        else emptyList()
        val first = runCatching { m.more(12) }.getOrDefault(emptyList())
        val all = (fromSubs + first).distinctBy { it.id }
        if (all.isEmpty()) error = "No videos came back. Check your connection and try again."
        videos = all
        loading = false; refreshing = false
    }

    LoadMoreEffect(list, !loading && !loadingMore && mixer?.exhausted == false) {
        val m = mixer ?: return@LoadMoreEffect
        loadingMore = true
        scope.launch {
            val more = runCatching { m.more(10) }.getOrDefault(emptyList())
            val known = videos.map { it.id }.toSet()
            videos = videos + more.filter { it.id !in known }
            loadingMore = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(onSearch, onAccount)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (granted) scope.launch { localArea = LocationRecommendations.refresh(context) }
                else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }) { Text(if (localArea == null) "Use my area for recommendations" else "Nearby: $localArea") }
        }
        CategoryChips(category, { selected -> category = selected; scope.launch { list.scrollToItem(0) } })
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; reload++ }, modifier = Modifier.fillMaxSize()) {
            when {
                loading -> Loading()
                error != null -> Message("Couldn't load videos", error, "Try again") { reload++ }
                else -> LazyColumn(Modifier.fillMaxSize(), state = list) {
                    items(videos, key = { it.id }) { v -> VideoCard(v, { onVideo(v) }, onChannel) }
                    if (loadingMore) item { Loading(Modifier.fillMaxWidth().height(72.dp)) }
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
