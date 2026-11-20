package com.viewtube.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import kotlin.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.viewtube.app.data.Comment
import com.viewtube.app.data.Graph
import com.viewtube.app.data.Paged
import com.viewtube.app.data.VideoDetails
import com.viewtube.app.data.errorText
import com.viewtube.app.data.watchUrl
import com.viewtube.app.player.PlayerHolder
import kotlinx.coroutines.launch

private fun Context.activity(): Activity? {
    var c = this
    while (c is android.content.ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    videoId: String,
    onBack: () -> Unit,
    onVideo: (String) -> Unit,
    onChannel: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf<VideoDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var fullscreen by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf<Paged<Comment>?>(null) }
    var commentList by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var loadingComments by remember { mutableStateOf(false) }
    var showDesc by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    val audioSel by PlayerHolder.audioTrack.collectAsStateCompat()
    val speed by PlayerHolder.speed.collectAsStateCompat()
    val prefHeight by Graph.store.preferredHeight.collectAsStateCompat()
    val height by PlayerHolder.height.collectAsStateCompat()
    val notice by PlayerHolder.skipNotice.collectAsStateCompat()
    val subs by Graph.store.subs.collectAsStateCompat()
    val later by Graph.store.later.collectAsStateCompat()

    LaunchedEffect(videoId, attempt) {
        error = null
        comments = null; commentList = emptyList(); showDesc = false
        // Re-opening the video that is already playing (from the mini player): keep it going.
        val cur = PlayerHolder.current.value
        if (cur != null && cur.video.id == videoId) { details = cur; return@LaunchedEffect }
        details = null
        runCatching { Graph.repo.details(videoId) }
            .onSuccess { d ->
                details = d
                Graph.store.addHistory(d.video)
                runCatching { PlayerHolder.play(d, startMs = Graph.store.positionOf(videoId)) }
                    .onFailure { error = errorText(it) }
            }
            .onFailure { error = errorText(it) }
    }

    // Autoplay is handled by PlayerHolder; when it moves on, follow it.
    LaunchedEffect(videoId) {
        PlayerHolder.advanced.collect { id -> if (id != videoId) onVideo(id) }
    }

    // Fullscreen = landscape + hidden system bars.
    DisposableEffect(fullscreen) {
        val act = ctx.activity()
        if (act != null) {
            val ctl = WindowInsetsControllerCompat(act.window, act.window.decorView)
            if (fullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                ctl.hide(WindowInsetsCompat.Type.systemBars())
                ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                ctl.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            ctx.activity()?.let {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowInsetsControllerCompat(it.window, it.window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    androidx.activity.compose.BackHandler(enabled = fullscreen) { fullscreen = false }

    val playerBox: @Composable (Modifier) -> Unit = { mod ->
        Box(mod.background(Color.Black)) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        player = PlayerHolder.player
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowSubtitleButton(false)
                        setFullscreenButtonClickListener { fullscreen = it }
                    }
                },
                update = { it.player = PlayerHolder.player },
                modifier = Modifier.fillMaxSize()
            )
            if (!fullscreen) IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White)
            }
            IconButton(
                onClick = { fullscreen = !fullscreen },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (fullscreen) "Exit fullscreen" else "Enter fullscreen",
                    tint = Color.White,
                )
            }
            IconButton(
                onClick = { sheet = true },
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Playback settings", tint = Color.White)
            }
            notice?.let {
                Text(
                    it, color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                        .clip(RoundedCornerShape(16.dp)).background(Color(0xCC000000)).padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }

    if (fullscreen) {
        playerBox(Modifier.fillMaxSize())
    } else {
    Column(Modifier.fillMaxSize()) {
        playerBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        val d = details
        when {
            error != null && d == null -> Message("Can't play this video", error, "Try again") { attempt++ }
            d == null -> Loading()
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.padding(12.dp)) {
                        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                        Text(d.video.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = if (showDesc) 6 else 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                if (d.video.views >= 0) "${formatCount(d.video.views)} views" else null,
                                d.video.publishedText?.ifBlank { null },
                                if (d.likes >= 0) "${formatCount(d.likes)} likes" else null,
                            ).joinToString(" • "),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp).clickable { showDesc = !showDesc }
                        )
                        if (showDesc && d.description.isNotBlank()) {
                            Text(d.description, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            Text("...more", fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { showDesc = true })
                        }

                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(d.channel.avatar, 40) { onChannel(d.channel.url) }
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp).clickable { onChannel(d.channel.url) }) {
                                Text(d.channel.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (d.channel.subscribers >= 0) Text("${formatCount(d.channel.subscribers)} subscribers", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SubscribeButton(d.channel, subs.any { it.id == d.channel.id })
                        }

                        Row(Modifier.padding(top = 12.dp)) {
                            val saved = later.any { it.id == d.video.id }
                            AssistChip(onClick = { Graph.store.toggleLater(d.video) }, label = { Text(if (saved) "Saved" else "Watch later") })
                            Spacer(Modifier.width(8.dp))
                            AssistChip(onClick = {
                                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, watchUrl(d.video.id))
                                }, "Share"))
                            }, label = { Text("Share") })
                            Spacer(Modifier.width(8.dp))
                            if (!d.isLive) AssistChip(
                                onClick = { sheet = true },
                                label = { Text(if (height > 0) "${height}p" else "Quality") }
                            )
                        }
                    }
                }

                item {
                    CommentsSection(
                        commentList, loadingComments, comments == null,
                        hasMore = comments?.next != null,
                        onLoad = {
                            scope.launch {
                                loadingComments = true
                                runCatching { Graph.repo.comments(d.video.id) }
                                    .onSuccess { p -> comments = p; commentList = p.items }
                                loadingComments = false
                            }
                        },
                        onMore = {
                            val n = comments?.next ?: return@CommentsSection
                            scope.launch {
                                loadingComments = true
                                runCatching { n() }.onSuccess { p -> comments = p; commentList = (commentList + p.items).distinctBy { it.author + "|" + it.text } }
                                loadingComments = false
                            }
                        }
                    )
                }

                item { Text("Up next", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp)) }
                items(d.related, key = { it.id }) { v -> VideoRow(v) { onVideo(v.id) } }
            }
        }
    }
    }
    val dd = details
    if (sheet && dd != null) ModalBottomSheet(onDismissRequest = { sheet = false }) {
        Column(Modifier.padding(bottom = 24.dp)) {
            SheetTitle("Quality")
            val heights = PlayerHolder.heights(dd)
            SheetRow("Auto (recommended)", prefHeight == 0) {
                Graph.store.setPreferredHeight(0)
                runCatching { PlayerHolder.play(dd, PlayerHolder.defaultHeight(dd)) }.onFailure { error = errorText(it) }
                sheet = false
            }
            heights.forEach { h ->
                SheetRow("${h}p", height == h && prefHeight != 0) {
                    Graph.store.setPreferredHeight(h)
                    runCatching { PlayerHolder.play(dd, h) }.onFailure { error = errorText(it) }
                    sheet = false
                }
            }
            val tracks = PlayerHolder.audioOptions(dd)
            if (tracks.isNotEmpty()) {
                SheetTitle("Audio track")
                tracks.forEach { (id, label) ->
                    SheetRow(label, audioSel == id) {
                        runCatching { PlayerHolder.play(dd, height.takeIf { it > 0 } ?: PlayerHolder.defaultHeight(dd), audioTrackId = id) }
                            .onFailure { error = errorText(it) }
                        sheet = false
                    }
                }
            }
            SheetTitle("Playback speed")
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                SheetRow(if (sp == 1f) "Normal" else "${sp}x", speed == sp) { PlayerHolder.setSpeed(sp); sheet = false }
            }
        }
    }
}

@Composable
fun SubscribeButton(channel: com.viewtube.app.data.ChannelItem, subscribed: Boolean) {
    val shape = RoundedCornerShape(20.dp)
    val act = { Graph.store.toggleSub(channel); Unit }
    if (subscribed) OutlinedButton(onClick = act, shape = shape) { Text("Subscribed") }
    else Button(onClick = act, shape = shape, colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background
    )) { Text("Subscribe") }
}

@Composable
private fun CommentsSection(
    list: List<Comment>, loading: Boolean, notLoaded: Boolean, hasMore: Boolean,
    onLoad: () -> Unit, onMore: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text("Comments", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
        if (notLoaded) {
            OutlinedButton(onClick = onLoad, enabled = !loading) { Text(if (loading) "Loading..." else "Show comments") }
        } else if (list.isEmpty()) {
            Text("No comments available.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        } else {
            list.forEach { c ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Avatar(c.avatar, 32)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(
                            listOfNotNull(if (c.pinned) "Pinned" else null, c.author, c.published).joinToString(" • "),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(c.text, fontSize = 14.sp)
                        if (c.likes > 0 || c.replies > 0) Text(
                            listOfNotNull(if (c.likes > 0) "${formatCount(c.likes.toLong())} likes" else null,
                                if (c.replies > 0) "${c.replies} replies" else null).joinToString(" • "),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (hasMore) OutlinedButton(onClick = onMore, enabled = !loading, modifier = Modifier.padding(top = 4.dp)) {
                Text(if (loading) "Loading..." else "More comments")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SheetTitle(t: String) {
    Text(t, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun SheetRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = YtRed)
    }
}
