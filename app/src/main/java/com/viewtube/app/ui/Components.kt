package com.viewtube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.viewtube.app.data.ChannelItem
import com.viewtube.app.data.Graph
import com.viewtube.app.data.VideoItem
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow

fun formatCount(n: Long): String = when {
    n < 0 -> ""
    n < 1_000 -> "$n"
    n < 1_000_000 -> trim(n / 1_000.0) + "K"
    n < 1_000_000_000 -> trim(n / 1_000_000.0) + "M"
    else -> trim(n / 1_000_000_000.0) + "B"
}

private fun trim(d: Double): String = if (d >= 10) "%.0f".format(d) else "%.1f".format(d).removeSuffix(".0")

fun formatDuration(sec: Long): String {
    if (sec < 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun relativeTime(ms: Long): String {
    if (ms <= 0) return ""
    val mins = (System.currentTimeMillis() - ms) / 60_000
    return when {
        mins < 60 -> "${maxOf(mins, 1)} minutes ago"
        mins < 60 * 24 -> "${mins / 60} hours ago"
        mins < 60 * 24 * 30 -> "${mins / (60 * 24)} days ago"
        mins < 60 * 24 * 365 -> "${mins / (60 * 24 * 30)} months ago"
        else -> "${mins / (60 * 24 * 365)} years ago"
    }
}

fun VideoItem.metaLine(): String = listOfNotNull(
    channelName.ifBlank { null },
    if (views >= 0) "${formatCount(views)} views" else null,
    publishedText?.ifBlank { null } ?: relativeTime(publishedMs).ifBlank { null },
).joinToString(" • ")

/** The big video card used in every feed, laid out like the YouTube app. */
@Composable
fun VideoCard(video: VideoItem, onClick: () -> Unit, onOpenChannel: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val later by Graph.store.later.collectAsStateCompat()
    val saved = later.any { it.id == video.id }

    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 16.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (video.isLive) {
                Badge("LIVE", YtRed, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            } else if (video.durationSec > 0) {
                Badge(formatDuration(video.durationSec), Color(0xCC000000), Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
        }
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp)) {
            Avatar(video.channelAvatar, 36) { video.channelUrl?.let(onOpenChannel) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
                Text(video.metaLine(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (saved) "Remove from Watch later" else "Save to Watch later") },
                        onClick = { Graph.store.toggleLater(video); menu = false }
                    )
                }
            }
        }
    }
}

/** Compact row used in search results and "up next". */
@Composable
fun VideoRow(video: VideoItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Box(Modifier.width(168.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(model = video.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (video.isLive) Badge("LIVE", YtRed, Modifier.align(Alignment.BottomEnd).padding(4.dp))
            else if (video.durationSec > 0) Badge(formatDuration(video.durationSec), Color(0xCC000000), Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
            Text(video.metaLine(), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun ChannelRow(channel: ChannelItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(channel.avatar, 64) { onClick() }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(channel.name, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (channel.subscribers >= 0) {
                Text("${formatCount(channel.subscribers)} subscribers", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun Avatar(url: String?, size: Int, onClick: (() -> Unit)? = null) {
    val base = Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
    Box(if (onClick != null) base.clickable(onClick = onClick) else base, contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) {
            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size((size * 0.6f).dp))
        } else {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Badge(text: String, color: Color, modifier: Modifier) {
    Text(
        text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

@Composable
fun Loading(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = YtRed) }
}

@Composable
fun Message(title: String, body: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        if (body != null) Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        if (action != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 12.dp)) { Text(action) }
        }
    }
}

/** Calls [onEnd] when the list is scrolled near the bottom, to load the next page. */
@Composable
fun LoadMoreEffect(state: LazyListState, enabled: Boolean, onEnd: () -> Unit) {
    LaunchedEffect(state, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3 && info.totalItemsCount > 0
        }.collectLatest { if (it) onEnd() }
    }
}
