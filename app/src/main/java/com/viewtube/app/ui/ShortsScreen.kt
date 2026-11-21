package com.viewtube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.viewtube.app.data.ChannelItem
import com.viewtube.app.data.FeedMixer
import com.viewtube.app.data.Graph
import com.viewtube.app.data.VideoItem
import com.viewtube.app.data.errorText
import com.viewtube.app.player.PlayerHolder

/** Vertical swipe feed of short videos. One shared player sits behind a pager of overlays. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ShortsScreen(onChannel: (String) -> Unit) {
    val mixer = remember {
        FeedMixer(
            listOf("shorts", "funny shorts", "music shorts", "gaming shorts", "satisfying shorts", "viral shorts", "science shorts"),
            Graph.repo
        ) { it.isShort || it.durationSec in 1..70 }
    }
    var shorts by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var playError by remember { mutableStateOf<String?>(null) }
    val subs by Graph.store.subs.collectAsStateCompat()

    LaunchedEffect(Unit) {
        val first = runCatching { mixer.more(8) }.getOrDefault(emptyList())
        if (first.isEmpty()) error = "Couldn't load Shorts. Check your connection."
        shorts = first
    }

    val pager = rememberPagerState(pageCount = { shorts.size })

    // Load the Short that is on screen and play it on repeat.
    val currentId = shorts.getOrNull(pager.settledPage)?.id
    LaunchedEffect(currentId) {
        val v = shorts.firstOrNull { it.id == currentId } ?: return@LaunchedEffect
        ready = false; playError = null
        runCatching { Graph.repo.details(v.id) }
            .onSuccess { d ->
                runCatching { PlayerHolder.play(d, 720, startMs = 0, loop = true) }
                    .onSuccess { ready = true }
                    .onFailure { playError = errorText(it) }
            }
            .onFailure { playError = errorText(it) }
    }

    // Keep the feed endless.
    LaunchedEffect(pager.settledPage, shorts.size) {
        if (shorts.isNotEmpty() && pager.settledPage >= shorts.size - 3 && !loadingMore && !mixer.exhausted) {
            loadingMore = true
            val more = runCatching { mixer.more(8) }.getOrDefault(emptyList())
            val known = shorts.map { it.id }.toSet()
            shorts = shorts + more.filter { it.id !in known }
            loadingMore = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { PlayerHolder.stop() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            error != null && shorts.isEmpty() -> Message("Shorts unavailable", error)
            shorts.isEmpty() -> Loading()
            else -> {
                AndroidView(
                    factory = { c ->
                        PlayerView(c).apply {
                            player = PlayerHolder.player
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    update = { it.player = PlayerHolder.player },
                    modifier = Modifier.fillMaxSize()
                )
                VerticalPager(pager, Modifier.fillMaxSize()) { page ->
                    val v = shorts[page]
                    Box(Modifier.fillMaxSize().clickable { PlayerHolder.toggle() }) {
                        if (page == pager.settledPage && !ready && playError == null) Loading()
                        if (page == pager.settledPage && playError != null) {
                            Text("Couldn't play this one. Swipe up for the next.", color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                        }
                        Column(
                            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000))))
                                .padding(start = 12.dp, end = 12.dp, top = 40.dp, bottom = 16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(v.channelAvatar, 32) { v.channelUrl?.let(onChannel) }
                                Text(
                                    v.channelName, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                                )
                                val chUrl = v.channelUrl
                                if (chUrl != null) {
                                    val id = chUrl.substringAfterLast('/')
                                    SubscribeButton(ChannelItem(id, chUrl, v.channelName, v.channelAvatar), subs.any { it.id == id })
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(v.title, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
