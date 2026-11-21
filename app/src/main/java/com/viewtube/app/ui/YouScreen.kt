package com.viewtube.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.viewtube.app.data.Graph
import com.viewtube.app.data.SubscriptionImport
import com.viewtube.app.data.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BUG_URL = "https://github.com/showlyshah/YTSHAH/issues/new?template=bug_report.yml&labels=bug"

/** Returns a function that opens the file picker and imports the chosen subscriptions file. */
@Composable
private fun rememberImporter(): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val msg = runCatching {
                val text = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                }
                val found = SubscriptionImport.parse(text)
                if (found.isEmpty()) "No YouTube channels found in that file."
                else "Imported ${Graph.store.addSubs(found)} new channel(s)."
            }.getOrElse { "That file couldn't be read. Use subscriptions.csv from Google Takeout." }
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }
    return { picker.launch(arrayOf("*/*")) }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.width(24.dp))
        Column(Modifier.padding(start = 24.dp)) {
            Text(title, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The "You" tab, laid out like the YouTube app's profile page. */
@Composable
fun YouScreen(
    onVideo: (VideoItem) -> Unit,
    onHistory: () -> Unit,
    onWatchLater: () -> Unit,
    onSubscriptions: () -> Unit,
    onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val name by Graph.store.profileName.collectAsStateCompat()
    val subs by Graph.store.subs.collectAsStateCompat()
    val history by Graph.store.history.collectAsStateCompat()
    val later by Graph.store.later.collectAsStateCompat()
    val doImport = rememberImporter()
    var editName by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(null, 72)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(name.ifBlank { "Guest" }, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${subs.size} subscriptions • stored on this phone", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Edit name", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { editName = true }.padding(top = 4.dp))
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        }

        // History strip
        Row(Modifier.fillMaxWidth().clickable(onClick = onHistory).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("History", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("View all", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
        }
        if (history.isEmpty()) {
            Text("Videos you watch will show up here.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(history.take(12), key = { it.id }) { v ->
                    Column(Modifier.width(160.dp).clickable { onVideo(v) }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            AsyncImage(model = v.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Text(v.title, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        Text(v.channelName, fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Playlists strip
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 4.dp)) {
            Text("Playlists", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        MenuRow(Icons.Default.WatchLater, "Watch later", "${later.size} video" + if (later.size == 1) "" else "s", onWatchLater)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        MenuRow(Icons.Default.History, "History", null, onHistory)
        MenuRow(Icons.Default.Subscriptions, "Your subscriptions", "${subs.size} channels", onSubscriptions)
        MenuRow(Icons.Default.Subscriptions, "Import subscriptions", "From Google Takeout or a NewPipe backup", doImport)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        MenuRow(Icons.Default.Settings, "Settings", "Quality, autoplay, privacy", onSettings)
        MenuRow(Icons.Default.BugReport, "Help & feedback", "Report a bug") {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BUG_URL)))
        }
        MenuRow(Icons.Default.Info, "About ViewTube", null) { about = true }
    }

    if (editName) {
        var field by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Your name") },
            text = { OutlinedTextField(field, { field = it }, singleLine = true, label = { Text("Shown only on this phone") }) },
            confirmButton = { TextButton(onClick = { Graph.store.setProfileName(field); editName = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancel") } }
        )
    }
    if (about) AlertDialog(
        onDismissRequest = { about = false },
        title = { Text("ViewTube") },
        text = { Text("An ad-free YouTube viewer powered by NewPipeExtractor. Your profile, history and subscriptions stay on this phone. Not affiliated with YouTube or Google.") },
        confirmButton = { TextButton(onClick = { about = false }) { Text("OK") } }
    )
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val store = Graph.store
    val sponsor by store.sponsorSkip.collectAsStateCompat()
    val autoplay by store.autoplayNext.collectAsStateCompat()
    val pref by store.preferredHeight.collectAsStateCompat()
    val doImport = rememberImporter()
    val ctx = LocalContext.current
    var qualityDialog by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        MenuRow(Icons.Default.Settings, "Default video quality", if (pref == 0) "Auto (480p on mobile data, 720p on Wi-Fi)" else "${pref}p") { qualityDialog = true }
        SwitchRow("Autoplay next video", "Plays the next suggestion when a video ends", autoplay) { store.setAutoplayNext(it) }
        SwitchRow("Skip sponsor & promo segments", "Uses the community SponsorBlock list", sponsor) { store.setSponsorSkip(it) }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        MenuRow(Icons.Default.Subscriptions, "Import subscriptions", "subscriptions.csv from Google Takeout, or a NewPipe backup", doImport)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        MenuRow(Icons.Default.History, "Clear watch history") { store.clearHistory(); Toast.makeText(ctx, "Watch history cleared", Toast.LENGTH_SHORT).show() }
        MenuRow(Icons.Default.History, "Clear search history") { store.clearSearches(); Toast.makeText(ctx, "Search history cleared", Toast.LENGTH_SHORT).show() }
        MenuRow(Icons.Default.Delete, "Delete all my data", "Subscriptions, history, saved videos and settings") { confirmWipe = true }
    }

    if (qualityDialog) AlertDialog(
        onDismissRequest = { qualityDialog = false },
        title = { Text("Default video quality") },
        text = {
            Column {
                listOf(0, 144, 240, 360, 480, 720, 1080, 1440, 2160).forEach { h ->
                    Text(
                        (if (h == 0) "Auto" else "${h}p") + if (h == pref) "  ✓" else "",
                        modifier = Modifier.fillMaxWidth().clickable { store.setPreferredHeight(h); qualityDialog = false }.padding(vertical = 10.dp),
                        fontWeight = if (h == pref) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { qualityDialog = false }) { Text("Close") } }
    )
    if (confirmWipe) AlertDialog(
        onDismissRequest = { confirmWipe = false },
        title = { Text("Delete all data?") },
        text = { Text("This removes your subscriptions, history, saved videos and settings from this phone.") },
        confirmButton = { TextButton(onClick = { store.wipeAll(); confirmWipe = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } }
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** History and Watch later lists. */
@Composable
fun VideoListScreen(title: String, history: Boolean, onBack: () -> Unit, onVideo: (VideoItem) -> Unit) {
    val hist by Graph.store.history.collectAsStateCompat()
    val later by Graph.store.later.collectAsStateCompat()
    val shown = if (history) hist else later
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (history && hist.isNotEmpty()) TextButton(onClick = { Graph.store.clearHistory() }) { Text("Clear all") }
        }
        if (shown.isEmpty()) {
            Message(
                if (history) "No watch history" else "Nothing saved",
                if (history) "Videos you watch will show up here." else "Use the ⋮ menu on a video to save it for later."
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { v -> VideoRow(v) { onVideo(v) } }
            }
        }
    }
}
