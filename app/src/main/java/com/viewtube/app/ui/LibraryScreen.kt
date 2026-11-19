package com.viewtube.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.viewtube.app.data.Graph
import com.viewtube.app.data.VideoItem

@Composable
fun LibraryScreen(onSearch: () -> Unit, onAccount: () -> Unit, onVideo: (VideoItem) -> Unit) {
    val history by Graph.store.history.collectAsStateCompat()
    val later by Graph.store.later.collectAsStateCompat()
    var tab by remember { mutableIntStateOf(0) }
    val shown = if (tab == 0) history else later

    Column(Modifier.fillMaxSize()) {
        TopBar(onSearch, onAccount)
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("History") })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Watch later") })
        }
        if (tab == 0 && history.isNotEmpty()) {
            TextButton(onClick = { Graph.store.clearHistory() }, modifier = Modifier.padding(horizontal = 4.dp)) { Text("Clear all history") }
        }
        if (shown.isEmpty()) {
            Message(if (tab == 0) "No watch history" else "Nothing saved", if (tab == 0) "Videos you watch will show up here." else "Use the ⋮ menu on a video to save it for later.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { v -> VideoRow(v) { onVideo(v) } }
            }
        }
    }
}
