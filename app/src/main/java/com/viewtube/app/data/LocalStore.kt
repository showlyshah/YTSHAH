package com.viewtube.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything personal lives here, on the device: profile name, subscriptions, watch history,
 * "watch later" and settings. These records remain local unless the user explicitly syncs
 * subscriptions with a configured Piped account.
 */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("viewtube", Context.MODE_PRIVATE)

    private val _subs = MutableStateFlow(loadChannels("subs"))
    private val _history = MutableStateFlow(loadVideos("history"))
    private val _later = MutableStateFlow(loadVideos("later"))
    private val _profile = MutableStateFlow(prefs.getString("profile_name", "").orEmpty())
    private val _searches = MutableStateFlow(loadStrings("searches"))
    private val _prefHeight = MutableStateFlow(prefs.getInt("pref_height", 0))
    private val _sponsorSkip = MutableStateFlow(prefs.getBoolean("sponsor_skip", true))
    private val _autoplay = MutableStateFlow(prefs.getBoolean("autoplay_next", true))
    private val _pipedServer = MutableStateFlow(prefs.getString("piped_server", "https://pipedapi.adminforge.de").orEmpty())
    private val _pipedUser = MutableStateFlow(prefs.getString("piped_user", "")?.ifBlank { null })
    private val positions = runCatching { JSONObject(prefs.getString("positions", "{}") ?: "{}") }.getOrElse { JSONObject() }

    val subs: StateFlow<List<ChannelItem>> = _subs.asStateFlow()
    val history: StateFlow<List<VideoItem>> = _history.asStateFlow()
    val later: StateFlow<List<VideoItem>> = _later.asStateFlow()
    val profileName: StateFlow<String> = _profile.asStateFlow()
    val searchHistory: StateFlow<List<String>> = _searches.asStateFlow()
    /** Preferred video height in pixels, 0 = automatic. */
    val preferredHeight: StateFlow<Int> = _prefHeight.asStateFlow()
    val sponsorSkip: StateFlow<Boolean> = _sponsorSkip.asStateFlow()
    val autoplayNext: StateFlow<Boolean> = _autoplay.asStateFlow()
    val pipedServer: StateFlow<String> = _pipedServer.asStateFlow()
    val pipedUser: StateFlow<String?> = _pipedUser.asStateFlow()

    // ---------------------------------------------------------- subscriptions

    fun isSubscribed(channelId: String) = _subs.value.any { it.id == channelId }

    /** Returns true if the channel is now subscribed. */
    fun toggleSub(channel: ChannelItem): Boolean {
        val now = !isSubscribed(channel.id)
        _subs.value = if (now) _subs.value + channel else _subs.value.filterNot { it.id == channel.id }
        saveChannels("subs", _subs.value)
        return now
    }

    /** Adds channels that are not already subscribed. Returns how many were new. */
    fun addSubs(channels: List<ChannelItem>): Int {
        val known = _subs.value.map { it.id }.toSet()
        val fresh = channels.filter { it.id !in known }.distinctBy { it.id }
        if (fresh.isNotEmpty()) {
            _subs.value = _subs.value + fresh
            saveChannels("subs", _subs.value)
        }
        return fresh.size
    }

    // ---------------------------------------------------------------- history

    fun addHistory(video: VideoItem) {
        _history.value = (listOf(video) + _history.value.filterNot { it.id == video.id }).take(300)
        saveVideos("history", _history.value)
    }

    fun isWatched(id: String) = _history.value.any { it.id == id }

    fun removeFromHistory(id: String) {
        _history.value = _history.value.filterNot { it.id == id }
        saveVideos("history", _history.value)
    }

    fun clearHistory() {
        _history.value = emptyList()
        saveVideos("history", emptyList())
        positions.keys().asSequence().toList().forEach { positions.remove(it) }
        prefs.edit().putString("positions", "{}").apply()
    }

    fun savePosition(videoId: String, ms: Long) {
        if (ms < 5_000) {
            positions.remove(videoId)
        } else {
            positions.put(videoId, ms)
            // Keep resume metadata bounded even if the user watches thousands of videos.
            val keys = positions.keys().asSequence().toList()
            keys.take((keys.size - MAX_SAVED_POSITIONS).coerceAtLeast(0)).forEach { positions.remove(it) }
        }
        prefs.edit().putString("positions", positions.toString()).apply()
    }

    fun positionOf(videoId: String): Long = positions.optLong(videoId, 0)

    // ------------------------------------------------------------ watch later

    fun isLater(id: String) = _later.value.any { it.id == id }

    fun toggleLater(video: VideoItem) {
        _later.value = if (isLater(video.id)) _later.value.filterNot { it.id == video.id }
        else listOf(video) + _later.value
        saveVideos("later", _later.value)
    }

    // --------------------------------------------------------------- settings

    fun setProfileName(name: String) { _profile.value = name.trim(); prefs.edit().putString("profile_name", name.trim()).apply() }
    fun setPreferredHeight(h: Int) { _prefHeight.value = h; prefs.edit().putInt("pref_height", h).apply() }
    fun setSponsorSkip(on: Boolean) { _sponsorSkip.value = on; prefs.edit().putBoolean("sponsor_skip", on).apply() }
    fun setAutoplayNext(on: Boolean) { _autoplay.value = on; prefs.edit().putBoolean("autoplay_next", on).apply() }
    fun setPipedServer(server: String) { _pipedServer.value = server.trim().trimEnd('/'); prefs.edit().putString("piped_server", _pipedServer.value).apply() }
    fun setPipedUser(user: String?) { _pipedUser.value = user?.trim()?.takeIf { it.isNotEmpty() }; prefs.edit().putString("piped_user", _pipedUser.value.orEmpty()).apply() }

    // --------------------------------------------------------- search history

    fun addSearch(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        _searches.value = (listOf(t) + _searches.value.filterNot { it.equals(t, true) }).take(50)
        saveStrings("searches", _searches.value)
    }

    fun removeSearch(q: String) {
        _searches.value = _searches.value.filterNot { it == q }
        saveStrings("searches", _searches.value)
    }

    fun clearSearches() { _searches.value = emptyList(); saveStrings("searches", emptyList()) }

    /** Deletes all personal data from this device. */
    fun wipeAll() {
        prefs.edit().clear().apply()
        _subs.value = emptyList(); _history.value = emptyList(); _later.value = emptyList()
        _profile.value = ""; _searches.value = emptyList(); _prefHeight.value = 0
        _sponsorSkip.value = true; _autoplay.value = true
        _pipedServer.value = "https://pipedapi.adminforge.de"; _pipedUser.value = null
        positions.keys().asSequence().toList().forEach { positions.remove(it) }
    }

    // ---------------------------------------------------------------- storage

    private fun loadVideos(key: String): List<VideoItem> = runCatching {
        val a = JSONArray(prefs.getString(key, "[]"))
        (0 until a.length()).map { VideoItem.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun saveVideos(key: String, list: List<VideoItem>) =
        prefs.edit().putString(key, JSONArray(list.map { it.toJson() }).toString()).apply()

    private fun loadChannels(key: String): List<ChannelItem> = runCatching {
        val a = JSONArray(prefs.getString(key, "[]"))
        (0 until a.length()).map { ChannelItem.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun saveChannels(key: String, list: List<ChannelItem>) =
        prefs.edit().putString(key, JSONArray(list.map { it.toJson() }).toString()).apply()

    private fun loadStrings(key: String): List<String> = runCatching {
        val a = JSONArray(prefs.getString(key, "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    private fun saveStrings(key: String, list: List<String>) =
        prefs.edit().putString(key, JSONArray(list).toString()).apply()

    private companion object { const val MAX_SAVED_POSITIONS = 300 }
}
