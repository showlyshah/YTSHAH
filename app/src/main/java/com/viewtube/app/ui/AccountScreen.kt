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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viewtube.app.data.Graph
import com.viewtube.app.data.SubscriptionImport
import com.viewtube.app.data.errorText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AccountScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = Graph.store
    val name by store.profileName.collectAsStateCompat()
    val server by store.pipedServer.collectAsStateCompat()
    val pipedUser by store.pipedUser.collectAsStateCompat()
    val sponsor by store.sponsorSkip.collectAsStateCompat()
    val autoplay by store.autoplayNext.collectAsStateCompat()
    val personalized by store.personalizedRecommendations.collectAsStateCompat()
    val subs by store.subs.collectAsStateCompat()

    var nameField by remember(name) { mutableStateOf(name) }
    var serverField by remember(server) { mutableStateOf(server) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }

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
                val restored = store.importBackup(raw)
                "Backup restored. Imported ${restored} saved items and subscriptions."
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

        Section("Discovery") {
            ToggleRow("Personalized recommendations", "Uses watch history locally on this phone; disabled by default", personalized) { store.setPersonalizedRecommendations(it) }
        }

        Section("Playback") {
            ToggleRow("Skip sponsor & promo segments", "Uses the community SponsorBlock list", sponsor) { store.setSponsorSkip(it) }
            ToggleRow("Autoplay next video", null, autoplay) { store.setAutoplayNext(it) }
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
