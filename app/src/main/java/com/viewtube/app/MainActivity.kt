package com.viewtube.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.viewtube.app.data.videoIdFromUrl
import com.viewtube.app.player.PlayerHolder
import com.viewtube.app.ui.ChannelScreen
import com.viewtube.app.ui.CreatorsScreen
import com.viewtube.app.ui.HomeScreen
import com.viewtube.app.ui.MiniPlayer
import com.viewtube.app.ui.SearchScreen
import com.viewtube.app.ui.SettingsScreen
import com.viewtube.app.ui.ShortsScreen
import com.viewtube.app.ui.SubscriptionsScreen
import com.viewtube.app.ui.VideoListScreen
import com.viewtube.app.ui.ViewTubeTheme
import com.viewtube.app.ui.WatchScreen
import com.viewtube.app.ui.YouScreen
import com.viewtube.app.ui.YtRed

@UnstableApi
class MainActivity : ComponentActivity() {

    private var pendingVideo by mutableStateOf<String?>(null)
    private var onWatchScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingVideo = videoFromIntent(intent)

        setContent {
            ViewTubeTheme {
                Surface(Modifier.fillMaxSize()) {
                    // Needed on Android 13+ to show the playback notification.
                    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                    LaunchedEffect(Unit) {
                        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                            != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    App()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingVideo = videoFromIntent(intent)
    }

    private fun videoFromIntent(i: Intent?): String? {
        i ?: return null
        val text = when (i.action) {
            Intent.ACTION_VIEW -> i.dataString
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return null
        return videoIdFromUrl(text)
    }

    // Swiping home while a video is open shrinks it into a floating window.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (onWatchScreen && PlayerHolder.player.isPlaying) {
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun App() {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route.orEmpty()
        onWatchScreen = route.startsWith("watch/")

        LaunchedEffect(pendingVideo) {
            pendingVideo?.let { nav.navigate("watch/$it"); pendingVideo = null }
        }

        val tabs = listOf(
            Triple("home", "Home", Icons.Default.Home),
            Triple("shorts", "Shorts", Icons.Default.SmartDisplay),
            Triple("subs", "Subscriptions", Icons.Default.Subscriptions),
            Triple("creators", "Creators", Icons.Default.People),
            Triple("you", "You", Icons.Default.AccountCircle),
        )
        val showBar = route in tabs.map { it.first }

        Scaffold(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    if (!route.startsWith("watch/") && route != "shorts") MiniPlayer { id -> nav.navigate("watch/$id") }
                    if (showBar) NavigationBar(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                        tabs.forEach { (r, label, icon) ->
                            NavigationBarItem(
                                selected = route == r,
                                onClick = {
                                    nav.navigate(r) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true; restoreState = true
                                    }
                                },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = YtRed, selectedTextColor = YtRed,
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding()).statusBarsPadding()) {
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onSearch = { nav.navigate("search") },
                            onAccount = { nav.navigate("you") },
                            onVideo = { nav.navigate("watch/${it.id}") },
                            onChannel = { nav.openChannel(it) },
                        )
                    }
                    composable("subs") {
                        SubscriptionsScreen(
                            onSearch = { nav.navigate("search") },
                            onAccount = { nav.navigate("you") },
                            onVideo = { nav.navigate("watch/${it.id}") },
                            onChannel = { nav.openChannel(it) },
                        )
                    }
                    composable("shorts") { ShortsScreen(onSearch = { nav.navigate("search") }, onAccount = { nav.navigate("you") }, onChannel = { nav.openChannel(it) }, onVideo = { nav.navigate("watch/${it.id}") }) }
                    composable("creators") {
                        CreatorsScreen(
                            onSearch = { nav.navigate("search") },
                            onAccount = { nav.navigate("you") },
                            onChannel = { nav.openChannel(it) },
                        )
                    }
                    composable("you") {
                        YouScreen(
                            onVideo = { nav.navigate("watch/${it.id}") },
                            onHistory = { nav.navigate("history") },
                            onWatchLater = { nav.navigate("watchlater") },
                            onSubscriptions = { nav.navigate("subs") },
                            onSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("history") { VideoListScreen("History", true, { nav.popBackStack() }, { nav.navigate("watch/${it.id}") }) }
                    composable("watchlater") { VideoListScreen("Watch later", false, { nav.popBackStack() }, { nav.navigate("watch/${it.id}") }) }
                    composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                    composable("search?q={q}", arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })) {
                        SearchScreen(
                            initialQuery = Uri.decode(it.arguments?.getString("q").orEmpty()),
                            onBack = { nav.popBackStack() },
                            onVideo = { v -> nav.navigate("watch/${v.id}") },
                            onChannel = { u -> nav.openChannel(u) },
                        )
                    }
                    composable("channel/{url}", arguments = listOf(navArgument("url") { type = NavType.StringType })) {
                        ChannelScreen(
                            url = Uri.decode(it.arguments?.getString("url").orEmpty()),
                            onBack = { nav.popBackStack() },
                            onVideo = { v -> nav.navigate("watch/${v.id}") },
                            onChannel = { u -> nav.openChannel(u) },
                        )
                    }
                    composable("watch/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                        WatchScreen(
                            videoId = it.arguments?.getString("id").orEmpty(),
                            onBack = { nav.popBackStack() },
                            onVideo = { id -> nav.navigate("watch/$id") { popUpTo(it.destination.id) { inclusive = true } } },
                            onChannel = { u -> nav.openChannel(u) },
                        )
                    }
                }
            }
        }
    }

    private fun NavHostController.openChannel(url: String) = navigate("channel/${Uri.encode(url)}")
}
