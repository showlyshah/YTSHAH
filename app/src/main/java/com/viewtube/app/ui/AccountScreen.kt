package com.viewtube.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viewtube.app.data.Graph
import com.viewtube.app.data.FeedDiagnostics
import com.viewtube.app.data.RuntimeDiagnostics
import com.viewtube.app.CrashReportRecorder
import com.viewtube.app.data.SubscriptionImport
import com.viewtube.app.player.PlayerHolder
import com.viewtube.app.data.errorText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AccountScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val store = Graph.store
    val name by store.profileName.collectAsStateCompat()
    val server by store.pipedServer.collectAsStateCompat()
    val pipedUser by store.pipedUser.collectAsStateCompat()
    val sponsor by store.sponsorSkip.collectAsStateCompat()
    val autoplay by store.autoplayNext.collectAsStateCompat()
    val personalized by store.personalizedRecommendations.collectAsStateCompat()
    val cloudAiEnabled by store.cloudAiEnabled.collectAsStateCompat()
    val themeMode by store.themeMode.collectAsStateCompat()
    val dataSaver by store.dataSaver.collectAsStateCompat()
    val preferredHeight by store.preferredHeight.collectAsStateCompat()
    val preferredAudioBitrate by store.preferredAudioBitrate.collectAsStateCompat()
    val subs by store.subs.collectAsStateCompat()
    val history by store.history.collectAsStateCompat()
    val later by store.later.collectAsStateCompat()
    val queue by store.queue.collectAsStateCompat()

    var nameField by remember(name) { mutableStateOf(name) }
    var serverField by remember(server) { mutableStateOf(server) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    var pendingBackup by remember { mutableStateOf<String?>(null) }
    var aiBaseUrl by remember { mutableStateOf(Graph.secure.get(com.viewtube.app.data.OpenAiCompatibleProvider.KEY_BASE_URL) ?: com.viewtube.app.data.OpenAiCompatibleProvider.DEFAULT_BASE_URL) }
    var aiModel by remember { mutableStateOf(Graph.secure.get(com.viewtube.app.data.OpenAiCompatibleProvider.KEY_MODEL) ?: com.viewtube.app.data.OpenAiCompatibleProvider.DEFAULT_MODEL) }
    var aiKey by remember { mutableStateOf("") }
    var cacheBytes by remember { mutableStateOf(PlayerHolder.playbackCacheBytes()) }
    var crashReport by remember { mutableStateOf(CrashReportRecorder.read(ctx)) }

    val diagnosticExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = runCatching {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(Graph.diagnostics.exportJson()) }
                        ?: error("Couldn't open the selected destination.")
                }
                "Redacted diagnostic report exported."
            }.getOrElse { "Diagnostic export failed: ${it.message ?: "unknown error"}" }
        }
    }
    val backupExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = runCatching {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.exportBackup()) }
                        ?: error("Couldn't open the selected destination.")
                }
                "ViewTube backup exported."
            }.getOrElse { "Backup export failed: ${it.message ?: "unknown error"}" }
        }
    }
    val backupImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = runCatching {
                val raw = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Couldn't read the selected file.")
                }
                require(raw.length <= 5_000_000) { "Backup file is too large." }
                org.json.JSONObject(raw).also {
                    require(it.optInt("version", -1) == 1) { "Unsupported backup version." }
                }
                pendingBackup = raw
                "Backup file validated. Confirm replacement below."
            }.getOrElse { "Backup restore failed: ${it.message ?: "invalid backup"}" }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = runCatching {
                val text = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                }
                val found = SubscriptionImport.parse(text)
                if (found.isEmpty()) "No YouTube channels found in that file."
                else {
                    val added = store.addSubs(found)
                    "Imported $added new channel(s)."
                }
            }.getOrElse { "That file couldn't be read. Use subscriptions.csv from Google Takeout." }
        }
    }

    fun auth(register: Boolean) {
        if (user.isBlank() || pass.isBlank()) { status = "Enter a username and password."; return }
        store.setPipedServer(serverField)
        scope.launch {
            busy = true; status = null
            runCatching {
                if (register) Graph.piped.register(user.trim(), pass) else Graph.piped.signIn(user.trim(), pass)
                val added = Graph.piped.sync()
                pass = ""
                status = "Signed in. Synced subscriptions ($added new)."
            }.onFailure { status = errorText(it) }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Account & settings", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        Section("Your profile") {
            OutlinedTextField(nameField, { nameField = it; store.setProfileName(it) }, label = { Text("Display name (stays on this phone)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("${subs.size} subscriptions on this device", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Section("Sync across devices (optional)") {
            if (pipedUser != null) {
                Text("Signed in as $pipedUser", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            status = runCatching { "Synced. ${Graph.piped.sync()} new channel(s)." }.getOrElse { errorText(it) }
                            busy = false
                        }
                    }) { Text("Sync now") }
                    OutlinedButton(onClick = { Graph.piped.signOut(); status = "Signed out." }) { Text("Sign out") }
                }
            } else {
                Text(
                    "Sign in with a Piped account to keep your subscriptions on all your devices. " +
                        "This is a separate account, not your Google login. Google doesn't allow other apps to " +
                        "sign in to YouTube, and you should never type your Google password into one.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(serverField, { serverField = it }, label = { Text("Piped server") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = { auth(false) }) { Text(if (busy) "Please wait..." else "Sign in") }
                    OutlinedButton(enabled = !busy, onClick = { auth(true) }) { Text("Create account") }
                }
            }
        }

        Section("Bring your YouTube subscriptions") {
            Text(
                "1. Open takeout.google.com in a browser and sign in to Google there.\n" +
                    "2. Choose only \"YouTube and YouTube Music\" > subscriptions, then export.\n" +
                    "3. Unzip it and pick subscriptions.csv below. (NewPipe backups work too.)",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Import subscriptions file") }
        }

        Section("Appearance") {
            Text("Choose how ViewTube looks.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system", "light", "dark").forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { store.setThemeMode(mode) },
                        label = { Text(mode.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }

        Section("Discovery") {
            ToggleRow("Personalized recommendations", "Uses watch history locally on this phone; disabled by default", personalized) { store.setPersonalizedRecommendations(it) }
        }

        Section("Playback") {
            ToggleRow("Skip sponsor & promo segments", "Uses the community SponsorBlock list", sponsor) { store.setSponsorSkip(it) }
            ToggleRow("Autoplay next video", null, autoplay) { store.setAutoplayNext(it) }
            ToggleRow("Data saver", "Uses lower automatic quality on metered networks; manual Max overrides this", dataSaver) { store.setDataSaver(it) }
            Text("Preferred playback quality")
            listOf(-1, 0, 360, 480, 720, 1080, 1440, 2160).chunked(4).forEach { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.forEach { h ->
                        FilterChip(
                            selected = preferredHeight == h,
                            onClick = { store.setPreferredHeight(h) },
                            label = { Text(when (h) { -1 -> "Max"; 0 -> "Auto"; else -> "${h}p" }) }
                        )
                    }
                }
            }
            Text("Preferred audio quality")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 320000, 256000, 192000, 128000).forEach { bitrate ->
                    FilterChip(
                        selected = preferredAudioBitrate == bitrate,
                        onClick = { store.setPreferredAudioBitrate(bitrate) },
                        label = { Text(if (bitrate == 0) "Highest" else "${bitrate / 1000} kbps") }
                    )
                }
            }
        }

        Section("Hybrid AI intelligence") {
            Text(
                "Local topic analysis and description-based assistance work on-device. A cloud provider is optional. " +
                    "Your API key is encrypted with Android Keystore and is never included in backups.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                aiBaseUrl, { aiBaseUrl = it },
                label = { Text("OpenAI-compatible HTTPS endpoint") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                aiModel, { aiModel = it }, label = { Text("Model name") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                aiKey, { aiKey = it }, label = { Text("API key (leave blank to keep saved key)") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    runCatching {
                        require(aiBaseUrl.startsWith("https://", true)) { "Use an HTTPS endpoint." }
                        require(aiModel.isNotBlank()) { "Enter a model name." }
                        Graph.secure.put(com.viewtube.app.data.OpenAiCompatibleProvider.KEY_BASE_URL, aiBaseUrl.trim())
                        Graph.secure.put(com.viewtube.app.data.OpenAiCompatibleProvider.KEY_MODEL, aiModel.trim())
                        if (aiKey.isNotBlank()) Graph.secure.put(com.viewtube.app.data.OpenAiCompatibleProvider.KEY_API_KEY, aiKey.trim())
                        aiKey = ""
                        status = "AI provider settings saved securely."
                    }.onFailure { status = it.message ?: "Could not save AI settings." }
                }) { Text("Save AI settings") }
                OutlinedButton(onClick = {
                    Graph.secure.put(com.viewtube.app.data.OpenAiCompatibleProvider.KEY_API_KEY, null)
                    aiKey = ""
                    status = "Saved AI API key removed."
                }) { Text("Remove key") }
            }
            ToggleRow(
                "Enable optional cloud AI",
                "Cloud requests still require explicit approval for each AI action.",
                cloudAiEnabled
            ) { store.setCloudAiEnabled(it) }
        }

        Section("Your viewing insights") {
            val topics = com.viewtube.app.data.LocalRecommendationEngine.topicsFrom(history, 6)
            Text("${history.size} videos in local watch history · ${later.size} saved · ${queue.size} queued")
            Text(
                "Top topics from video titles: " + (topics.joinToString(", ").ifBlank { "Not enough history yet" }),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "These statistics are calculated on this device. They are not uploaded.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Section("Diagnostics & crash reports") {
            Text(
                "Full runtime trace records app startup, navigation, network request/response codes and timings, extractor results, feed pagination, player states and errors. It is plain text copied to the clipboard; no log file is created and nothing is uploaded.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(RuntimeDiagnostics.report(ctx)))
                    status = "Complete runtime diagnostic report copied. Paste it here."
                }) { Text("Copy full diagnostic report") }
                OutlinedButton(onClick = {
                    RuntimeDiagnostics.clear(ctx)
                    status = "Full runtime diagnostic history cleared."
                }) { Text("Clear full trace") }
            }
            Text(
                "${Graph.diagnostics.read().size} recent redacted diagnostic events stored locally. Fatal crashes are saved automatically on this device.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { diagnosticExport.launch("viewtube-diagnostics.json") }) { Text("Export diagnostics") }
                OutlinedButton(onClick = { Graph.diagnostics.clear(); status = "Diagnostic history cleared." }) { Text("Clear logs") }
            }
            Text(
                "Home and Shorts have a separate local recorder. It logs source attempts, response counts, timings, pagination and errors. Nothing is uploaded automatically.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(FeedDiagnostics.report()))
                    status = "Home & Shorts feed report copied. Paste it here."
                }) { Text("Copy feed report") }
                OutlinedButton(onClick = {
                    FeedDiagnostics.clear()
                    status = "Home & Shorts feed logs cleared."
                }) { Text("Clear feed logs") }
            }
            Text(
                if (crashReport != null) "A saved crash report is ready to copy and paste into the bug conversation."
                else "No fatal crash report has been recorded on this installation yet.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = crashReport != null,
                    onClick = {
                        val report = crashReport ?: CrashReportRecorder.read(ctx)
                        if (report != null) {
                            clipboard.setText(AnnotatedString(report))
                            status = "Crash report copied. Paste it into the bug conversation."
                        } else status = "No crash report is available."
                    }
                ) { Text("Copy crash report") }
                OutlinedButton(
                    enabled = crashReport != null,
                    onClick = {
                        CrashReportRecorder.clear(ctx)
                        crashReport = null
                        status = "Saved crash report cleared."
                    }
                ) { Text("Clear crash report") }
            }
        }

        Section("Storage & data") {
            Text(
                "Temporary stream cache: ${"%.1f".format(cacheBytes / (1024.0 * 1024.0))} MB. " +
                    "ViewTube keeps a bounded cache to reduce repeat network usage; this is not a permanent download library.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = {
                PlayerHolder.clearPlaybackCache()
                cacheBytes = PlayerHolder.playbackCacheBytes()
                status = "Temporary playback cache cleared."
            }) { Text("Clear playback cache") }
        }

        Section("Backup & restore") {
            Text(
                "Export or restore your subscriptions, history, Watch later, queue, settings and playback positions. " +
                    "Piped sign-in credentials are never included.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { backupExport.launch("viewtube-backup.json") }) { Text("Export backup") }
                OutlinedButton(onClick = { backupImport.launch(arrayOf("application/json", "text/*")) }) { Text("Restore backup") }
            }
        }

        Section("Privacy") {
            Text(
                "History, subscriptions and settings are saved only on this phone. Nothing is sent anywhere " +
                    "unless you sign in to a sync server above. The sync sign-in token is stored encrypted.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { confirmWipe = true }) { Text("Delete all my data from this phone") }
        }

        Section("Help improve ViewTube") {
            Text(
                "Something not working? Tell us. YouTube changes often, and reports are how fixes get made.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/showlyshah/YTSHAH/issues/new?template=bug_report.yml&labels=bug"))
                )
            }) { Text("Report a bug") }
        }

        status?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.primary) }
    }

    pendingBackup?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingBackup = null },
            title = { Text("Restore ViewTube backup?") },
            text = { Text("This replaces the current subscriptions, history, Watch later, queue and settings with the selected backup. Export your current data first if you may need it.") },
            confirmButton = {
                TextButton(onClick = {
                    status = runCatching {
                        val restored = store.importBackup(backup)
                        "Backup restored. Imported ${restored} saved items and subscriptions."
                    }.getOrElse { "Backup restore failed: ${it.message ?: "invalid backup"}" }
                    pendingBackup = null
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingBackup = null }) { Text("Cancel") } }
        )
    }

    if (confirmWipe) AlertDialog(
        onDismissRequest = { confirmWipe = false },
        title = { Text("Delete all data?") },
        text = { Text("This removes your subscriptions, history, saved videos, settings and sign-in from this phone.") },
        confirmButton = { TextButton(onClick = { Graph.piped.signOut(); store.wipeAll(); Graph.secure.clear(); confirmWipe = false; status = "All data deleted." }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } }
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        content()
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
