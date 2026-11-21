package com.viewtube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.viewtube.app.player.PlayerHolder

/** Small bar shown above the bottom tabs while a video is playing in the background. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MiniPlayer(onOpen: (String) -> Unit) {
    val d by PlayerHolder.current.collectAsStateCompat()
    val playing by PlayerHolder.playing.collectAsStateCompat()
    val video = d?.video ?: return

    Row(
        Modifier.fillMaxWidth().height(56.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onOpen(video.id) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(100.dp).aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surface)) {
            AsyncImage(
                model = video.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(video.channelName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { PlayerHolder.toggle() }) {
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (playing) "Pause" else "Play")
        }
        IconButton(onClick = { PlayerHolder.stop() }) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
    }
}
