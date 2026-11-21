package com.viewtube.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.hardware.display.DisplayManager
import android.view.Display
import kotlin.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
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
import com.viewtube.app.data.SubtitleTrack
import com.viewtube.app.data.OnDeviceSubtitleTranslator
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
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var fullscreen by remember { mutableStateOf(false) }
    val immersivePlayer = fullscreen || landscape
    val hdrDisplaySupported = remember(ctx) {
        runCatching {
            val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            dm.getDisplay(Display.DEFAULT_DISPLAY)?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
        }.getOrDefault(false)
    }
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf<VideoDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var comments by remember { mutableStateOf<Paged<Comment>?>(null) }
    var commentList by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var loadingComments by remember { mutableStateOf(false) }
    var showDesc by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var subtitleSheet by remember { mutableStateOf(false) }
    var optionsSheet by remember { mutableStateOf(false) }
    var queueSheet by remember { mutableStateOf(false) }
    var aiSheet by remember { mutableStateOf(false) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiText by remember { mutableStateOf<String?>(null) }
    var aiQuestion by remember { mutableStateOf("") }
    var useCloudForAi by remember { mutableStateOf(false) }
    var gestureFeedback by remember { mutableStateOf<String?>(null) }
    var translationEnabled by remember { mutableStateOf(false) }
    var captionsEnabled by remember { mutableStateOf(true) }
    var showEqualizer by remember { mutableStateOf(false) }
    var equalizer by remember { mutableStateOf<android.media.audiofx.Equalizer?>(null) }
    var eqLevels by remember { mutableStateOf<List<Int>>(emptyList()) }
    var equalizerActive by remember { mutableStateOf(false) }
    var selectedSubtitleTrack by remember { mutableStateOf<SubtitleTrack?>(null) }
    val translationLanguages = remember { com.google.mlkit.nl.translate.TranslateLanguage.getAllLanguages().sortedBy { java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale.getDefault()) } }
    var translationTarget by remember { mutableStateOf(java.util.Locale.getDefault().language.takeIf { it in translationLanguages } ?: "en") }
    var translatedCaption by remember { mutableStateOf<String?>(null) }
    var translationError by remember { mutableStateOf<String?>(null) }
    val audioSel by PlayerHolder.audioTrack.collectAsStateCompat()
    val speed by PlayerHolder.speed.collectAsStateCompat()
    val looping by PlayerHolder.looping.collectAsStateCompat()
    val repeatMode by PlayerHolder.repeatMode.collectAsStateCompat()
    val sleepRemaining by PlayerHolder.sleepRemainingMs.collectAsStateCompat()
    val audioOnly by PlayerHolder.audioOnly.collectAsStateCompat()
    val prefHeight by Graph.store.preferredHeight.collectAsStateCompat()
    val prefAudioBitrate by Graph.store.preferredAudioBitrate.collectAsStateCompat()
    val height by PlayerHolder.height.collectAsStateCompat()
    val notice by PlayerHolder.skipNotice.collectAsStateCompat()
    val subs by Graph.store.subs.collectAsStateCompat()
    val later by Graph.store.later.collectAsStateCompat()
    val queue by Graph.store.queue.collectAsStateCompat()

    LaunchedEffect(videoId, attempt) {
        error = null
        comments = null; commentList = emptyList(); showDesc = false
        // Re-opening the video that is already playing (from the mini player): keep it going.
        val cur = PlayerHolder.current.value
        if (cur != null && cur.video.id == videoId) { details = cur; selectedSubtitleTrack = cur.subtitles.firstOrNull(); return@LaunchedEffect }
        details = null
        runCatching { Graph.repo.details(videoId) }
            .onSuccess { d ->
                details = d
                selectedSubtitleTrack = d.subtitles.firstOrNull()
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

    LaunchedEffect(videoId, captionsEnabled, translationEnabled, selectedSubtitleTrack, translationTarget) {
        translatedCaption = null
        translationError = null
        val track = selectedSubtitleTrack
        if (!captionsEnabled || track == null) return@LaunchedEffect
        var translator: OnDeviceSubtitleTranslator? = null
        try {
            val cues = Graph.repo.subtitleCues(track)
            val sourceCode = com.google.mlkit.nl.translate.TranslateLanguage.fromLanguageTag(track.languageTag)
            val targetCode = com.google.mlkit.nl.translate.TranslateLanguage.fromLanguageTag(translationTarget)
            val shouldTranslate = translationEnabled && sourceCode != null && targetCode != null && sourceCode != targetCode
            val activeTranslator = if (shouldTranslate) OnDeviceSubtitleTranslator.create(track.languageTag, translationTarget) else null
            translator = activeTranslator
            var previousText: String? = null
            while (true) {
                val position = PlayerHolder.player.currentPosition
                val sourceText = cues.firstOrNull { position >= it.startMs && position < it.endMs }?.text
                if (sourceText != previousText) {
                    previousText = sourceText
                    translatedCaption = when {
                        sourceText.isNullOrBlank() -> null
                        activeTranslator != null -> activeTranslator.translate(sourceText)
                        else -> sourceText
                    }
                }
                kotlinx.coroutines.delay(180)
            }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            translationError = error.message ?: "Subtitle loading or translation failed."
            translatedCaption = null
        } finally {
            translator?.close()
        }
    }

    LaunchedEffect(showEqualizer) {
        if (showEqualizer) {
            val effect = PlayerHolder.getEqualizer()
            equalizer = effect
            equalizerActive = runCatching { effect?.enabled == true }.getOrDefault(false)
            eqLevels = effect?.let { eq -> (0 until eq.numberOfBands.toInt()).map { eq.getBandLevel(it.toShort()).toInt() } }.orEmpty()
        }
    }

    LaunchedEffect(gestureFeedback) {
        if (gestureFeedback != null) {
            kotlinx.coroutines.delay(1200)
            gestureFeedback = null
        }
    }

    // Fullscreen = landscape + hidden system bars.
    DisposableEffect(immersivePlayer, fullscreen) {
        val act = ctx.activity()
        if (act != null) {
            val ctl = WindowInsetsControllerCompat(act.window, act.window.decorView)
            if (fullscreen) act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else if (!landscape) act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (immersivePlayer) {
                ctl.hide(WindowInsetsCompat.Type.systemBars())
                ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else ctl.show(WindowInsetsCompat.Type.systemBars())
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
        Box(mod.background(Color.Black).pointerInput(fullscreen) {
            var drag = 0f
            detectVerticalDragGestures(onVerticalDrag = { _, amount ->
                drag += amount
                if (immersivePlayer && drag > 70f) { onBack(); drag = 0f }
            }, onDragEnd = { drag = 0f })
        }) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        player = PlayerHolder.player
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowSubtitleButton(false)
                    }
                },
                update = { it.player = PlayerHolder.player },
                modifier = Modifier.fillMaxSize()
            )
            if (!immersivePlayer) IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White)
            }
            Box(
                Modifier.align(Alignment.CenterStart).fillMaxWidth(0.18f).fillMaxHeight().padding(top = 56.dp, bottom = 56.dp)
                    .pointerInput(videoId) {
                        var accumulated = 0f
                        detectVerticalDragGestures { _, amount ->
                            accumulated += amount
                            if (kotlin.math.abs(accumulated) >= 28f) {
                                ctx.activity()?.let { activity ->
                                    val params = activity.window.attributes
                                    val old = params.screenBrightness.takeIf { it >= 0f } ?: 0.5f
                                    params.screenBrightness = (old - accumulated / 500f).coerceIn(0.05f, 1f)
                                    activity.window.attributes = params
                                    gestureFeedback = "Brightness ${(params.screenBrightness * 100).toInt()}%"
                                }
                                accumulated = 0f
                            }
                        }
                    }
            )
            Box(
                Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.18f).fillMaxHeight().padding(top = 56.dp, bottom = 56.dp)
                    .pointerInput(videoId) {
                        var accumulated = 0f
                        detectVerticalDragGestures { _, amount ->
                            accumulated += amount
                            if (kotlin.math.abs(accumulated) >= 28f) {
                                val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                audio.adjustStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    if (accumulated < 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                                    0
                                )
                                gestureFeedback = "Volume ${audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}%"
                                accumulated = 0f
                            }
                        }
                    }
            )
            Row(Modifier.align(Alignment.TopEnd).padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { sheet = true }, modifier = Modifier.size(38.dp)) { Text("HD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                IconButton(onClick = { subtitleSheet = true }, modifier = Modifier.size(38.dp)) { Text("CC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                IconButton(onClick = { showEqualizer = true }, modifier = Modifier.size(38.dp)) { Text("EQ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                IconButton(onClick = { optionsSheet = true }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Player options", tint = Color.White, modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = { PlayerHolder.playNext { nextId -> onVideo(nextId) } }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Play next recommended video", tint = Color.White, modifier = Modifier.size(21.dp))
                }
                IconButton(onClick = {
                    if (immersivePlayer) {
                        fullscreen = false
                        ctx.activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        fullscreen = true
                        ctx.activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }, modifier = Modifier.size(38.dp)) {
                    Icon(if (immersivePlayer) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (immersivePlayer) "Exit fullscreen" else "Fullscreen",
                        tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            gestureFeedback?.let {
                Text(
                    it, color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                        .clip(RoundedCornerShape(16.dp)).background(Color(0xCC000000))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            translatedCaption?.let { caption ->
                Text(
                    caption, color = Color.White, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 22.dp, vertical = 58.dp)
                        .clip(RoundedCornerShape(10.dp)).background(Color(0xD9000000))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
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

    if (immersivePlayer) {
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
                                label = { Text(if (height > 0) "${height}p" else "Quality · Speed") }
                            )
                            Spacer(Modifier.width(8.dp))
                            AssistChip(onClick = { queueSheet = true }, label = { Text("Queue ${queue.size}") })
                            Spacer(Modifier.width(8.dp))
                            AssistChip(onClick = { aiSheet = true; aiText = null }, label = { Text("AI insights") })
                        }

                        Row(Modifier.padding(top = 4.dp)) {
                            AssistChip(
                                onClick = { PlayerHolder.setAudioOnly(!audioOnly) },
                                label = { Text(if (audioOnly) "Audio only: On" else "Audio only") }
                            )
                            Spacer(Modifier.width(8.dp))
                            AssistChip(
                                onClick = { PlayerHolder.setLooping(!looping) },
                                label = { Text(if (looping) "Loop: On" else "Loop video") }
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
                                val p = Graph.repo.comments(d.video.id)
                                comments = p; commentList = p.items
                                loadingComments = false
                            }
                        },
                        onMore = {
                            val n = comments?.next ?: return@CommentsSection
                            scope.launch {
                                loadingComments = true
                                runCatching { n() }.onSuccess { comments = it; commentList = commentList + it.items }
                                loadingComments = false
                            }
                        }
                    )
                }

                item { Text("Up next", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp)) }
                items(d.related, key = { it.id }) { v ->
                    Column(Modifier.fillMaxWidth()) {
                        VideoRow(v) { onVideo(v.id) }
                        OutlinedButton(
                            onClick = { Graph.store.enqueue(v) },
                            modifier = Modifier.padding(start = 12.dp, bottom = 10.dp)
                        ) { Text(if (queue.any { it.id == v.id }) "Move to end of queue" else "Add to queue") }
                    }
                }
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
            SheetRow("Max available", prefHeight == -1) {
                Graph.store.setPreferredHeight(-1)
                runCatching { PlayerHolder.play(dd, PlayerHolder.defaultHeight(dd)) }.onFailure { error = errorText(it) }
                sheet = false
            }
            heights.forEach { h ->
                SheetRow("${h}p", height == h && prefHeight == h) {
                    Graph.store.setPreferredHeight(h)
                    runCatching { PlayerHolder.play(dd, h) }.onFailure { error = errorText(it) }
                    sheet = false
                }
            }
            SheetTitle("Audio quality")
            listOf(0, 320000, 256000, 192000, 128000).forEach { bitrate ->
                SheetRow(if (bitrate == 0) "Highest available" else "$bitrate kbps or nearest available", prefAudioBitrate == bitrate) {
                    Graph.store.setPreferredAudioBitrate(bitrate)
                    runCatching {
                        PlayerHolder.play(dd, height.takeIf { it > 0 } ?: PlayerHolder.defaultHeight(dd), audioTrackId = audioSel)
                    }.onFailure { error = errorText(it) }
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
            SheetTitle("Playback mode")
            listOf("off" to "Play one", "one" to "Repeat one", "continuous" to "Play continuously").forEach { (mode, label) ->
                SheetRow(label, repeatMode == mode) { PlayerHolder.setRepeatMode(mode) }
            }
            SheetTitle("Sleep timer")
            val remainingMinutes = sleepRemaining?.let { (it / 60000L).toInt().coerceAtLeast(1) }
            Text(if (remainingMinutes != null) "Stops playback in about $remainingMinutes min" else "No sleep timer set",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(15, 30, 45, 60, 90).forEach { mins ->
                SheetRow("$mins minutes", remainingMinutes == mins) { PlayerHolder.setSleepTimer(mins) }
            }
            if (sleepRemaining != null) SheetRow("Cancel sleep timer", false) { PlayerHolder.cancelSleepTimer() }
            if (hdrDisplaySupported) {
                SheetTitle("HDR")
                Text("HDR display detected. HDR playback is enabled automatically when the source provides a compatible HDR stream; SDR streams remain SDR.",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SheetTitle("Playback speed")
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                SheetRow(if (sp == 1f) "Normal" else "${sp}x", speed == sp) { PlayerHolder.setSpeed(sp); sheet = false }
            }
        }
    }

    if (optionsSheet) ModalBottomSheet(onDismissRequest = { optionsSheet = false }) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            SheetTitle("Player options")
            SheetRow("Video quality and audio tracks", false) { optionsSheet = false; sheet = true }
            SheetRow("Subtitles and translation", captionsEnabled) { optionsSheet = false; subtitleSheet = true }
            SheetRow("Audio equalizer", equalizerActive) { optionsSheet = false; showEqualizer = true }
            SheetRow("Play-next queue (${queue.size})", queue.isNotEmpty()) { optionsSheet = false; queueSheet = true }
            SheetRow("AI insights", false) { optionsSheet = false; aiSheet = true; aiText = null }
            SheetRow(if (audioOnly) "Turn video back on" else "Audio only", audioOnly) { PlayerHolder.setAudioOnly(!audioOnly); optionsSheet = false }
            SheetRow(if (looping) "Turn loop off" else "Loop video", looping) { PlayerHolder.setLooping(!looping); optionsSheet = false }
        }
    }

    if (subtitleSheet && dd != null) ModalBottomSheet(onDismissRequest = { subtitleSheet = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            SheetTitle("Subtitles & AI translation")
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show subtitles", fontWeight = FontWeight.Medium)
                    Text("Display the selected caption track", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = captionsEnabled, onCheckedChange = { captionsEnabled = it })
            }
            if (dd.subtitles.isEmpty()) {
                Text("No subtitle tracks are available for this video.", modifier = Modifier.padding(vertical = 8.dp),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                SheetTitle("Caption language")
                dd.subtitles.forEach { track ->
                    SheetRow(track.languageName + if (track.autoGenerated) " · auto" else "", selectedSubtitleTrack?.url == track.url) {
                        selectedSubtitleTrack = track
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Translate subtitles", fontWeight = FontWeight.Medium)
                        Text("On-device AI · supported languages", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = translationEnabled, onCheckedChange = { translationEnabled = it })
                }
                SheetTitle("Show subtitles in")
                LazyColumn(Modifier.height(260.dp)) {
                    items(translationLanguages) { code ->
                        val label = java.util.Locale.forLanguageTag(code).getDisplayName(java.util.Locale.getDefault())
                        SheetRow(label.replaceFirstChar { it.uppercase() }, translationTarget == code) { translationTarget = code }
                    }
                }
            }
            translationError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)) }
        }
    }

    if (showEqualizer) ModalBottomSheet(onDismissRequest = { showEqualizer = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Audio equalizer", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text("Live equalizer on the player's audio session. Changes apply while the video keeps playing.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            val eq = equalizer
            if (eq == null) {
                Text("The device did not provide an equalizer for this playback session.", color = MaterialTheme.colorScheme.error)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Equalizer enabled", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Switch(checked = equalizerActive, onCheckedChange = { enabled ->
                        equalizerActive = PlayerHolder.setEqualizerEnabled(enabled)
                    })
                }
                if (eq.numberOfPresets.toInt() > 0) {
                    Text("Presets", fontWeight = FontWeight.Medium)
                    LazyColumn(Modifier.height(150.dp)) {
                        items((0 until eq.numberOfPresets.toInt()).toList()) { index ->
                            val name = runCatching { eq.getPresetName(index.toShort()).toString() }.getOrDefault("Preset $index")
                            SheetRow(name, false) {
                                runCatching {
                                    PlayerHolder.applyEqualizerPreset(index)
                                    eqLevels = (0 until eq.numberOfBands.toInt()).map { eq.getBandLevel(it.toShort()).toInt() }
                                }
                            }
                        }
                    }
                }
                Text("Frequency bands", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                (0 until eq.numberOfBands.toInt()).forEach { index ->
                    val range = eq.bandLevelRange
                    val current = eqLevels.getOrNull(index) ?: eq.getBandLevel(index.toShort()).toInt()
                    val frequency = runCatching { eq.getCenterFreq(index.toShort()) / 1000 }.getOrDefault(0)
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text("${frequency} Hz · ${(current / 100f).toInt()} dB", fontSize = 12.sp)
                        Slider(
                            value = current / 100f,
                            onValueChange = { value ->
                                val level = (value * 100f).toInt()
                                if (PlayerHolder.setEqualizerBand(index, level)) {
                                    eqLevels = eqLevels.toMutableList().also { list ->
                                        while (list.size <= index) list.add(0)
                                        list[index] = level
                                    }
                                }
                            },
                            valueRange = (range[0] / 100f)..(range[1] / 100f)
                        )
                    }
                }
                OutlinedButton(onClick = {
                    equalizerActive = PlayerHolder.setEqualizerEnabled(false)
                    showEqualizer = false
                }, modifier = Modifier.fillMaxWidth()) { Text("Turn off equalizer") }
            }
        }
    }

    if (queueSheet) ModalBottomSheet(onDismissRequest = { queueSheet = false }) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Play-next queue (${queue.size})", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (queue.isNotEmpty()) Text(
                    "Clear",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable { Graph.store.clearQueue() }.padding(8.dp)
                )
            }
            if (queue.isEmpty()) {
                Text("Your queue is empty. Add videos from the Up next list.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.height(420.dp)) {
                    items(queue, key = { it.id }) { item ->
                        Column {
                            VideoRow(item) { queueSheet = false; onVideo(item.id) }
                            OutlinedButton(
                                onClick = { Graph.store.removeFromQueue(item.id) },
                                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                            ) { Text("Remove from queue") }
                        }
                    }
                }
            }
        }
    }

    if (aiSheet && dd != null) ModalBottomSheet(onDismissRequest = { aiSheet = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Video intelligence", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text(
                "Analysis uses the video description available to ViewTube, not a guaranteed full transcript. " +
                    "Cloud processing is optional and requires both the global setting and your approval here.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Allow cloud for this request", modifier = Modifier.weight(1f))
                Switch(checked = useCloudForAi, onCheckedChange = { useCloudForAi = it })
            }
            OutlinedButton(
                enabled = !aiBusy,
                onClick = {
                    scope.launch {
                        aiBusy = true
                        aiText = runCatching {
                            Graph.ai.execute(com.viewtube.app.data.AiRequest(
                                task = com.viewtube.app.data.AiTask.VIDEO_SUMMARY,
                                prompt = "Summarize the main points in this video's available description.",
                                videoTitle = dd.video.title,
                                sourceText = dd.description,
                                allowCloud = useCloudForAi && Graph.store.cloudAiEnabled.value
                            ))?.let { "${it.text}\\n\\nSource: ${it.provider}" }
                                ?: "There isn't enough description text to summarize."
                        }.getOrElse { "AI request failed: ${it.message}" }
                        aiBusy = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (aiBusy) "Analysing..." else "Summarize video") }
            OutlinedTextField(
                value = aiQuestion,
                onValueChange = { aiQuestion = it },
                label = { Text("Ask a question about this video") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !aiBusy
            )
            OutlinedButton(
                enabled = !aiBusy && aiQuestion.isNotBlank(),
                onClick = {
                    scope.launch {
                        aiBusy = true
                        aiText = runCatching {
                            Graph.ai.execute(com.viewtube.app.data.AiRequest(
                                task = com.viewtube.app.data.AiTask.VIDEO_QUESTION,
                                prompt = aiQuestion,
                                videoTitle = dd.video.title,
                                sourceText = dd.description,
                                allowCloud = useCloudForAi && Graph.store.cloudAiEnabled.value
                            ))?.let { "${it.text}\\n\\nSource: ${it.provider}" }
                                ?: "No answer could be generated from the available description."
                        }.getOrElse { "AI request failed: ${it.message}" }
                        aiBusy = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Ask ViewTube AI") }
            aiText?.let { Text(it, modifier = Modifier.padding(vertical = 12.dp), fontSize = 14.sp) }
            if (useCloudForAi && !Graph.store.cloudAiEnabled.value) {
                Text("Cloud AI is disabled in Account & settings. This request will stay on-device.", color = MaterialTheme.colorScheme.tertiary, fontSize = 12.sp)
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
