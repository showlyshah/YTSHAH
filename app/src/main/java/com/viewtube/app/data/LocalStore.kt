package com.viewtube.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

const val DEFAULT_PIPED_SERVER = "https://pipedapi.kavin.rocks"

/**
 * Everything personal lives here, on the device: profile name, subscriptions, watch history,
 * "watch later" and settings. Nothing is sent anywhere unless the user signs in to a Piped server.
 */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("viewtube", Context.MODE_PRIVATE)

    private val _subs = MutableStateFlow(loadChannels("subs"))
    private val _history = MutableStateFlow(loadVideos("history"))
    private val _later = MutableStateFlow(loadVideos("later"))
    private val _profile = MutableStateFlow(prefs.getString("profile_name", "").orEmpty())
    private val _server = MutableStateFlow(prefs.getString("piped_server", DEFAULT_PIPED_SERVER) ?: DEFAULT_PIPED_SERVER)
    private val _pipedUser = MutableStateFlow(prefs.getString("piped_user", null))
    private val _sponsorSkip = MutableStateFlow(prefs.getBoolean("sponsor_skip", true))
    private val _autoplay = MutableStateFlow(prefs.getBoolean("autoplay_next", true))
    private val positions = JSONObject(prefs.getString("positions", "{}") ?: "{}")

    val subs: StateFlow<List<ChannelItem>> = _subs.asStateFlow()
    val history: StateFlow<List<VideoItem>> = _history.asStateFlow()
    val later: StateFlow<List<VideoItem>> = _later.asStateFlow()
    val profileName: StateFlow<String> = _profile.asStateFlow()
    val pipedServer: StateFlow<String> = _server.asStateFlow()
    val pipedUser: StateFlow<String?> = _pipedUser.asStateFlow()
    val sponsorSkip: StateFlow<Boolean> = _sponsorSkip.asStateFlow()
    val autoplayNext: StateFlow<Boolean> = _autoplay.asStateFlow()

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
        if (ms < 5_000) positions.remove(videoId) else positions.put(videoId, ms)
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
    fun setPipedServer(url: String) {
        val clean = url.trim().trimEnd('/').ifBlank { DEFAULT_PIPED_SERVER }
        _server.value = clean; prefs.edit().putString("piped_server", clean).apply()
    }
    fun setPipedUser(user: String?) { _pipedUser.value = user; prefs.edit().putString("piped_user", user).apply() }
    fun setSponsorSkip(on: Boolean) { _sponsorSkip.value = on; prefs.edit().putBoolean("sponsor_skip", on).apply() }
    fun setAutoplayNext(on: Boolean) { _autoplay.value = on; prefs.edit().putBoolean("autoplay_next", on).apply() }

    /** Deletes all personal data from this device. */
    fun wipeAll() {
        prefs.edit().clear().apply()
        _subs.value = emptyList(); _history.value = emptyList(); _later.value = emptyList()
        _profile.value = ""; _server.value = DEFAULT_PIPED_SERVER; _pipedUser.value = null
        _sponsorSkip.value = true; _autoplay.value = true
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
}
