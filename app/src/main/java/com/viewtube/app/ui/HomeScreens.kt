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
import androidx.compose.material3.TextButton
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
import com.viewtube.app.data.FeedMixer
import com.viewtube.app.data.Item
import com.viewtube.app.data.Paged
import com.viewtube.app.data.VideoItem
import com.viewtube.app.data.errorText
import kotlinx.coroutines.CancellationException
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
    val listState = rememberLazyListState()
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var canLoadMore by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val mixer = remember(reload) {
        FeedMixer(
            listOf("popular", "music", "technology", "gaming", "science", "news", "education"),
            Graph.repo,
        ) { true }
    }

    LaunchedEffect(reload, subs.map { it.id }) {
        loading = true
        error = null
        pageError = null
        canLoadMore = false

        val trendingResult = runCatching { Graph.repo.trending() }
        val subscriptionResult = runCatching {
            if (subs.isEmpty()) emptyList() else Graph.repo.feed(subs.map { it.id }, 3).take(20)
        }
        val discovery = mixer.more(12)
        videos = (subscriptionResult.getOrDefault(emptyList()) +
            trendingResult.getOrDefault(emptyList()) + discovery).distinctBy { it.id }

        if (videos.isEmpty()) {
            error = listOfNotNull(
                trendingResult.exceptionOrNull()?.let(::errorText),
                subscriptionResult.exceptionOrNull()?.let(::errorText),
                mixer.lastError?.let(::errorText),
            ).firstOrNull() ?: "No videos are available right now."
        } else {
            canLoadMore = !mixer.exhausted && mixer.lastError == null
            pageError = mixer.lastError?.let(::errorText)
        }
        loading = false
        refreshing = false
    }

    LoadMoreEffect(listState, canLoadMore && !loadingMore) {
        loadingMore = true
        scope.launch {
            val page = mixer.more(12)
            if (page.isNotEmpty()) {
                val existing = videos.mapTo(HashSet()) { it.id }
                videos = videos + page.filter { existing.add(it.id) }
            }
            pageError = mixer.lastError?.let(::errorText)
            canLoadMore = !mixer.exhausted && mixer.lastError == null
            loadingMore = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(onSearch, onAccount)
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; reload++ },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                loading && videos.isEmpty() -> Loading()
                error != null && videos.isEmpty() -> Message("Couldn't load videos", error, "Try again") { reload++ }
                else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(videos, key = { it.id }) { video ->
                        VideoCard(video, { onVideo(video) }, onChannel)
                    }
                    if (loadingMore) {
                        item { Loading(Modifier.fillMaxWidth().height(64.dp)) }
                    } else if (pageError != null) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(pageError.orEmpty(), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { pageError = null; canLoadMore = true }) { Text("Retry") }
                            }
                        }
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
    var pageError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState()

    fun run(q: String) {
        if (q.isBlank()) return
        query = q
        submitted = q
        suggestions = emptyList()
        results = emptyList()
        next = null
        pageError = null
        reload++
    }

    LaunchedEffect(submitted, reload) {
        if (submitted.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        try {
            val page = Graph.repo.search(submitted)
            results = page.items
            next = page.next
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = errorText(failure)
            results = emptyList()
            next = null
        } finally {
            loading = false
        }
    }

    LaunchedEffect(query) {
        val requested = query
        if (requested == submitted || requested.isBlank()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(250)
        try {
            val found = Graph.repo.suggestions(requested).take(8)
            if (query == requested && submitted != requested) suggestions = found
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (query == requested) suggestions = emptyList()
        }
    }

    LoadMoreEffect(list, next != null && !loadingMore && pageError == null) {
        val n = next ?: return@LoadMoreEffect
        loadingMore = true
        scope.launch {
            try {
                val page = n()
                results = (results + page.items).distinctBy { item ->
                    when (item) {
                        is VideoItem -> "v:\${item.id}"
                        is ChannelItem -> "c:\${item.id}"
                    }
                }
                next = page.next
                pageError = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                pageError = errorText(failure)
            } finally {
                loadingMore = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SearchBar(query, onChange = { query = it }, onSubmit = { run(query) }, onBack = onBack)
        when {
            suggestions.isNotEmpty() -> LazyColumn {
                items(suggestions) { suggestion ->
                    Row(Modifier.fillMaxWidth().clickable { run(suggestion) }.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Text(suggestion, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
            loading -> Loading()
            error != null -> Message("Search failed", error, "Try again") { run(submitted) }
            submitted.isNotBlank() && results.isEmpty() -> Message("No results for \"$submitted\"")
            else -> LazyColumn(Modifier.fillMaxSize(), state = list) {
                items(results.size) { index ->
                    when (val resultItem = results[index]) {
                        is VideoItem -> VideoRow(resultItem) { onVideo(resultItem) }
                        is ChannelItem -> ChannelRow(resultItem) { onChannel(resultItem.url) }
                    }
                }
                if (loadingMore) item { Loading(Modifier.fillMaxWidth().height(64.dp)) }
                pageError?.let { message ->
                    item {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { pageError = null }) { Text("Retry") }
                        }
                    }
                }
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

    LaunchedEffect(subs.map { it.id }, reload) {
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
