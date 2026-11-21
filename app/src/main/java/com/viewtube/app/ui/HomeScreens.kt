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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viewtube.app.data.ChannelItem
import com.viewtube.app.data.Graph
import com.viewtube.app.data.RuntimeDiagnostics
import com.viewtube.app.data.FILTER_VIDEOS
import com.viewtube.app.data.FeedDiagnostics
import com.viewtube.app.data.Item
import com.viewtube.app.data.LocalRecommendationEngine
import com.viewtube.app.data.Paged
import com.viewtube.app.data.VideoItem
import com.viewtube.app.data.errorText
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

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
    DisposableEffect(Unit) {
        RuntimeDiagnostics.record("HOME_UI", "screen-enter", "ok")
        onDispose { RuntimeDiagnostics.record("HOME_UI", "screen-dispose", "ok") }
    }
    val subs by Graph.store.subs.collectAsStateCompat()
    val personalized by Graph.store.personalizedRecommendations.collectAsStateCompat()
    val subIds = subs.map { it.id }
    val listState = rememberLazyListState()
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var loadMoreFailed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var mixer by remember { mutableStateOf<com.viewtube.app.data.FeedMixer?>(null) }

    LaunchedEffect(reload, subIds, personalized) {
        RuntimeDiagnostics.record("HOME_UI", "initial-load", "started", "reload=$reload subscriptions=${subIds.size} personalized=$personalized")
        loading = true
        loadingMore = false
        loadMoreFailed = false
        error = null
        videos = emptyList()
        listState.scrollToItem(0)
        val nextMixer = com.viewtube.app.data.FeedMixer(
            topics = listOf("popular", "music", "technology", "gaming", "science", "news", "education"),
            repo = Graph.repo,
            accept = { !it.isShort },
        )
        mixer = nextMixer
        // Publish the fast trending result before waiting for slower discovery sources.
        // Keep subscriptions and topic discovery independent so one stalled extractor
        // cannot leave the entire Home screen stuck in its loading state.
        var publishedAny = false
        fun publish(items: List<VideoItem>, source: String) {
            val merged = (videos + items).distinctBy { it.id }
            videos = if (personalized) LocalRecommendationEngine.rank(merged, Graph.store.history.value, subs) else merged
            if (videos.isNotEmpty()) {
                publishedAny = true
                error = null
                loading = false
                RuntimeDiagnostics.record("HOME_UI", "source-published", "success",
                    "source=$source, sourceItems=${items.size}, displayedVideos=${videos.size}")
            }
        }

        val trendingResult = runCatching {
            withTimeout(15_000) { Graph.repo.trending() }
        }
        trendingResult.onSuccess {
            FeedDiagnostics.record("home", "trending-search", if (it.isEmpty()) "empty" else "success",
                itemCount = it.size)
            publish(it, "trending")
        }.onFailure {
            if (it is kotlinx.coroutines.CancellationException) throw it
            FeedDiagnostics.record("home", "trending-search", "failure", error = it)
            RuntimeDiagnostics.record("HOME_UI", "trending-source", "failure", error = it)
        }

        // Subscriptions are a secondary source; do not block the initial trending batch.
        val subscriptionsJob = scope.launch {
            val result = runCatching {
                if (subIds.isEmpty()) emptyList()
                else withTimeout(15_000) { Graph.repo.feed(subIds, 3).take(24) }
            }
            result.onSuccess {
                FeedDiagnostics.record("home", "subscriptions-rss", if (it.isEmpty()) "empty" else "success",
                    itemCount = it.size, detail = "channels=${subIds.size}")
                publish(it, "subscriptions")
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                FeedDiagnostics.record("home", "subscriptions-rss", "failure", error = it)
                RuntimeDiagnostics.record("HOME_UI", "subscriptions-source", "failure", error = it)
            }
        }

        // Topic discovery runs separately and can enrich the feed after the first paint.
        val discoveryJob = scope.launch {
            val result = runCatching { withTimeout(25_000) { nextMixer.more(18) } }
            result.onSuccess {
                FeedDiagnostics.record("home", "topic-discovery", if (it.isEmpty()) "empty" else "success",
                    itemCount = it.size)
                publish(it, "topic-discovery")
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                FeedDiagnostics.record("home", "topic-discovery", "failure", error = it)
                RuntimeDiagnostics.record("HOME_UI", "topic-discovery", "failure", error = it)
            }
        }

        // If trending is unavailable, show the screen's retry/empty state immediately;
        // the secondary sources can still populate it when they finish.
        if (videos.isEmpty()) {
            loading = false
            error = "The feed is taking longer than expected. You can retry while other sources continue loading."
        }
        kotlinx.coroutines.coroutineScope {
            subscriptionsJob.join()
            discoveryJob.join()
        }
        FeedDiagnostics.record("home", "initial-feed", if (videos.isEmpty()) "empty" else "success",
            itemCount = videos.size, detail = "trendingFirst=true")
        RuntimeDiagnostics.record("HOME_UI", "initial-load-results", if (videos.isEmpty()) "empty" else "success",
            "videos=${videos.size}, mixerExhausted=${nextMixer.exhausted}, publishedAny=$publishedAny")
        hasMore = !nextMixer.exhausted
        if (videos.isNotEmpty()) error = null
        loading = false
        refreshing = false
        RuntimeDiagnostics.record("HOME_UI", "initial-load-finished", if (videos.isEmpty()) "empty" else "success", "displayedVideos=${videos.size}, hasMore=$hasMore, errorPresent=${error != null}")
    }

    fun loadNextPage() {
        if (loading || loadingMore || !hasMore) return
        val activeMixer = mixer ?: return
        scope.launch {
            RuntimeDiagnostics.record("HOME_UI", "pagination-request", "started", "currentVideos=${videos.size}")
            loadingMore = true
            loadMoreFailed = false
            val started = android.os.SystemClock.elapsedRealtime()
            val result = runCatching { activeMixer.more(14) }
            result.onSuccess { page ->
                FeedDiagnostics.record("home", "next-page", if (page.isEmpty()) "empty" else "success", android.os.SystemClock.elapsedRealtime() - started, page.size)
                if (page.isNotEmpty()) {
                    val known = videos.mapTo(HashSet()) { it.id }
                    val merged = videos + page.filter { known.add(it.id) }
                    videos = if (personalized) LocalRecommendationEngine.rank(merged, Graph.store.history.value, subs) else merged
                } else if (!activeMixer.exhausted) {
                    // Keep the continuation alive and expose a manual retry instead of ending the feed.
                    loadMoreFailed = true
                }
                hasMore = !activeMixer.exhausted
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                FeedDiagnostics.record("home", "next-page", "failure", android.os.SystemClock.elapsedRealtime() - started, error = it)
                RuntimeDiagnostics.record("HOME_UI", "pagination-request", "failure", error = it)
                loadMoreFailed = true
                error = errorText(it)
            }
            loadingMore = false
            RuntimeDiagnostics.record("HOME_UI", "pagination-finished", if (loadMoreFailed) "failure" else "complete", "displayedVideos=${videos.size}, hasMore=$hasMore")
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 5
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd) loadNextPage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(onSearch, onAccount)
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { if (!refreshing) { refreshing = true; reload++ } },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                loading -> Loading()
                error != null && videos.isEmpty() -> Message("Couldn't load videos", error, "Try again") { reload++ }
                else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(videos, key = { it.id }) { v -> VideoCard(v, { onVideo(v) }, onChannel) }
                    if (loadingMore) item(key = "home-loading") { Loading(Modifier.fillMaxWidth().height(72.dp)) }
                    if (loadMoreFailed && hasMore) item(key = "home-retry") {
                        Message("More videos couldn't load", "Your feed position is saved. Try loading the next page again.", "Retry") {
                            loadNextPage()
                        }
                    }
                    if (!hasMore && videos.isNotEmpty()) item(key = "home-end") {
                        Text("You're up to date. Pull down to refresh.", modifier = Modifier.fillMaxWidth().padding(20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
