package com.viewtube.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.viewtube.app.data.ChannelItem
import com.viewtube.app.data.FILTER_CHANNELS
import com.viewtube.app.data.Graph
import com.viewtube.app.data.Item
import com.viewtube.app.data.Paged
import com.viewtube.app.data.errorText
import kotlinx.coroutines.launch

private val TOPICS = listOf("Popular", "Music", "Gaming", "Tech", "Comedy", "Education", "Cooking", "Sports", "News", "Fitness", "Travel", "Art")

/** Discover channels by topic and subscribe in one tap. */
@Composable
fun CreatorsScreen(onSearch: () -> Unit, onAccount: () -> Unit, onChannel: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val subs by Graph.store.subs.collectAsStateCompat()
    var topic by remember { mutableStateOf("Popular") }
    var creators by remember { mutableStateOf<List<ChannelItem>>(emptyList()) }
    var next by remember { mutableStateOf<(suspend () -> Paged<Item>)?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(topic, reload) {
        loading = true; error = null; creators = emptyList(); next = null
        val q = if (topic == "Popular") "top creators" else "$topic creators"
        runCatching { Graph.repo.search(q, FILTER_CHANNELS) }
            .onSuccess { p -> creators = p.items.filterIsInstance<ChannelItem>(); next = p.next }
            .onFailure { error = errorText(it) }
        loading = false
    }

    LoadMoreEffect(list, next != null && !loadingMore && !loading) {
        val n = next ?: return@LoadMoreEffect
        loadingMore = true
        scope.launch {
            runCatching { n() }
                .onSuccess { p ->
                    val known = creators.map { it.id }.toSet()
                    creators = creators + p.items.filterIsInstance<ChannelItem>().filter { it.id !in known }
                    next = p.next
                }
                .onFailure { next = null }
            loadingMore = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(onSearch, onAccount)
        CategoryChips(topic, { topic = it }, TOPICS)
        when {
            loading -> Loading()
            error != null -> Message("Couldn't load creators", error, "Try again") { reload++ }
            creators.isEmpty() -> Message("No creators found", "Try another topic.")
            else -> LazyColumn(Modifier.fillMaxSize(), state = list) {
                items(creators, key = { it.id }) { c ->
                    Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { ChannelRow(c) { onChannel(c.url) } }
                        SubscribeButton(c, subs.any { it.id == c.id })
                    }
                }
                if (loadingMore) item { Loading(Modifier.fillMaxWidth().height(64.dp)) }
            }
        }
    }
}
