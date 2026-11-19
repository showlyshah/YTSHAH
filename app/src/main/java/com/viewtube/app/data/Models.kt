package com.viewtube.app.data

import org.json.JSONObject
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

/** Anything that can appear in a list of results. */
sealed interface Item

data class VideoItem(
    val id: String,
    val title: String,
    val channelName: String,
    val channelUrl: String?,
    val channelAvatar: String?,
    val thumbnail: String?,
    val durationSec: Long = -1,
    val views: Long = -1,
    val publishedText: String? = null,
    val publishedMs: Long = 0,
    val isLive: Boolean = false,
) : Item {
    val url: String get() = watchUrl(id)

    fun toJson(extra: Map<String, Any> = emptyMap()): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("channelName", channelName)
        put("channelUrl", channelUrl ?: ""); put("channelAvatar", channelAvatar ?: "")
        put("thumbnail", thumbnail ?: ""); put("durationSec", durationSec)
        put("views", views); put("publishedText", publishedText ?: "")
        put("publishedMs", publishedMs); put("isLive", isLive)
        extra.forEach { (k, v) -> put(k, v) }
    }

    companion object {
        fun fromJson(o: JSONObject) = VideoItem(
            id = o.getString("id"),
            title = o.optString("title"),
            channelName = o.optString("channelName"),
            channelUrl = o.optString("channelUrl").ifBlank { null },
            channelAvatar = o.optString("channelAvatar").ifBlank { null },
            thumbnail = o.optString("thumbnail").ifBlank { null },
            durationSec = o.optLong("durationSec", -1),
            views = o.optLong("views", -1),
            publishedText = o.optString("publishedText").ifBlank { null },
            publishedMs = o.optLong("publishedMs", 0),
            isLive = o.optBoolean("isLive", false),
        )
    }
}

data class ChannelItem(
    val id: String,
    val url: String,
    val name: String,
    val avatar: String?,
    val banner: String? = null,
    val subscribers: Long = -1,
    val description: String? = null,
) : Item {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("url", url); put("name", name); put("avatar", avatar ?: "")
    }

    companion object {
        fun fromJson(o: JSONObject) = ChannelItem(
            id = o.getString("id"),
            url = o.optString("url").ifBlank { channelUrl(o.getString("id")) },
            name = o.optString("name"),
            avatar = o.optString("avatar").ifBlank { null },
        )
    }
}

data class Comment(
    val author: String,
    val avatar: String?,
    val text: String,
    val likes: Int,
    val published: String?,
    val pinned: Boolean,
    val replies: Int,
)

/** One selectable quality in the player. */
data class Quality(val label: String, val height: Int, val video: VideoStream)

data class VideoDetails(
    val video: VideoItem,
    val description: String,
    val likes: Long,
    val channel: ChannelItem,
    val isLive: Boolean,
    val hlsUrl: String?,
    val muxed: List<VideoStream>,       // video + audio in one stream (usually up to 360p/720p)
    val videoOnly: List<VideoStream>,   // needs a separate audio stream (up to 4K)
    val audio: List<AudioStream>,
    val related: List<VideoItem>,
)

fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"
fun channelUrl(id: String) = "https://www.youtube.com/channel/$id"
