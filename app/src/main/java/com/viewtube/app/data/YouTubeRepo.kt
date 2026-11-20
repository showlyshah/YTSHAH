package com.viewtube.app.data

import android.util.Xml
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.OffsetDateTime

/** A page of results plus (if there is more) a function that loads the next page. */
class Paged<T>(val items: List<T>, val next: (suspend () -> Paged<T>)?)

class ChannelPage(val channel: ChannelItem, val videos: Paged<VideoItem>)

private val VIDEO_ID = Regex("""(?:[?&]v=|youtu\.be/|/shorts/|/embed/|/live/)([\w-]{11})""")
fun videoIdFromUrl(url: String): String? = VIDEO_ID.find(url)?.groupValues?.get(1)

const val FILTER_VIDEOS = "videos"
const val FILTER_CHANNELS = "channels"

class YouTubeRepo(private val http: OkHttpClient) {
    private val yt = ServiceList.YouTube

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    // ---------------------------------------------------------------- search

    suspend fun suggestions(query: String): List<String> = io {
        runCatching { yt.suggestionExtractor.suggestionList(query) }.getOrDefault(emptyList())
    }

    /** [filter] is FILTER_VIDEOS or FILTER_CHANNELS, or null for everything. */
    suspend fun search(query: String, filter: String? = null): Paged<Item> = io {
        val handler = if (filter == null) yt.searchQHFactory.fromQuery(query)
        else yt.searchQHFactory.fromQuery(query, listOf(filter), "")
        val info = SearchInfo.getInfo(yt, handler)
        searchPaged(handler, info.relatedItems, info.nextPage)
    }

    private fun searchPaged(handler: SearchQueryHandler, items: List<InfoItem>, next: Page?): Paged<Item> =
        Paged(
            items.mapNotNull { it.toItem() },
            next?.let { n ->
                suspend {
                    io {
                        val p = SearchInfo.getMoreItems(yt, handler, n)
                        searchPaged(handler, p.items, p.nextPage)
                    }
                }
            }
        )

    // -------------------------------------------------------------- trending

    suspend fun trending(): List<VideoItem> = io {
        val ex = yt.kioskList.defaultKioskExtractor
        ex.fetchPage()
        ex.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() }
    }

    // ----------------------------------------------------------------- video

    suspend fun details(id: String): VideoDetails = io {
        val info = StreamInfo.getInfo(yt, watchUrl(id))
        val chUrl = info.uploaderUrl.orEmpty()
        val channel = ChannelItem(
            id = chUrl.substringAfterLast('/'),
            url = chUrl,
            name = info.uploaderName.orEmpty(),
            avatar = info.uploaderAvatars.pick(96),
            subscribers = info.uploaderSubscriberCount,
        )
        val live = info.streamType == StreamType.LIVE_STREAM ||
            info.streamType == StreamType.AUDIO_LIVE_STREAM
        VideoDetails(
            video = VideoItem(
                id = id,
                title = info.name.orEmpty(),
                channelName = channel.name,
                channelUrl = chUrl.ifBlank { null },
                channelAvatar = channel.avatar,
                thumbnail = info.thumbnails.pick(360),
                durationSec = info.duration,
                views = info.viewCount,
                publishedText = info.textualUploadDate,
                isLive = live,
            ),
            description = descText(info.description),
            likes = info.likeCount,
            channel = channel,
            isLive = live,
            hlsUrl = info.hlsUrl?.ifBlank { null },
            muxed = info.videoStreams.orEmpty(),
            videoOnly = info.videoOnlyStreams.orEmpty(),
            audio = info.audioStreams.orEmpty(),
            related = info.relatedItems.orEmpty().filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() },
            subtitles = info.subtitles.orEmpty().filter { it.isUrl }.map {
                SubtitleTrack(it.content, it.languageTag, it.displayLanguageName, it.isAutoGenerated)
            },
        )
    }

    /** Download and parse the selected WebVTT/SRT caption track. */
    suspend fun subtitleCues(track: SubtitleTrack): List<SubtitleCue> = io {
        val request = Request.Builder().url(track.url).header("User-Agent", DownloaderImpl.USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Subtitle download failed (" + response.code + ").")
            val raw = response.body?.string().orEmpty()
            val timing = Regex("""(?m)^(\\d{1,2}:)?\\d{2}:\\d{2}[.,]\\d{3}\\s*-->\\s*(\\d{1,2}:)?\\d{2}:\\d{2}[.,]\\d{3}.*$""")
            val lines = raw.replace("\\r", "").lines()
            val out = mutableListOf<SubtitleCue>()
            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                if (timing.matches(line)) {
                    val parts = line.substringBefore(" ").split("-->")
                    val start = parseCaptionTime(parts[0].trim())
                    val end = parseCaptionTime(parts[1].trim().substringBefore(" "))
                    val textLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && lines[i].isNotBlank()) {
                        val candidate = lines[i].trim()
                        if (!candidate.startsWith("NOTE") && !candidate.matches(Regex("\\d+"))) textLines += candidate
                        i++
                    }
                    val cueText = textLines.joinToString(" ").replace(Regex("<[^>]+>"), "").trim()
                    if (cueText.isNotBlank() && end > start) out += SubtitleCue(start, end, cueText)
                }
                i++
            }
            out.take(12000)
        }
    }

    private fun parseCaptionTime(raw: String): Long {
        val normalized = raw.replace(',', '.')
        val p = normalized.split(":")
        val seconds = p.last().toDoubleOrNull() ?: return 0L
        val minutes = p.getOrNull(p.size - 2)?.toLongOrNull() ?: 0L
        val hours = p.getOrNull(p.size - 3)?.toLongOrNull() ?: 0L
        return ((hours * 3600 + minutes * 60) * 1000L) + (seconds * 1000).toLong()
    }
    // -------------------------------------------------------------- comments

    suspend fun comments(id: String): Paged<Comment> = io {
        val url = watchUrl(id)
        runCatching { CommentsInfo.getInfo(yt, url) }
            .map { info -> commentsPaged(url, info.relatedItems, info.nextPage) }
            .getOrElse { Paged(emptyList(), null) } // comments switched off, or failed to load
    }

    private fun commentsPaged(
        url: String,
        items: List<org.schabi.newpipe.extractor.comments.CommentsInfoItem>,
        next: Page?,
    ): Paged<Comment> = Paged(
        items.map {
            Comment(
                author = it.uploaderName.orEmpty(),
                avatar = it.uploaderAvatars.pick(48),
                text = descText(it.commentText),
                likes = it.likeCount,
                published = it.textualUploadDate,
                pinned = it.isPinned,
                replies = it.replyCount,
            )
        },
        next?.let { n ->
            suspend {
                io {
                    val p = CommentsInfo.getMoreItems(yt, url, n)
                    commentsPaged(url, p.items, p.nextPage)
                }
            }
        }
    )

    // --------------------------------------------------------------- channel

    suspend fun channel(url: String): ChannelPage = io {
        val info = ChannelInfo.getInfo(yt, url)
        val ch = ChannelItem(
            id = info.id.orEmpty(),
            url = info.url.orEmpty(),
            name = info.name.orEmpty(),
            avatar = info.avatars.pick(160),
            banner = info.banners.pick(280),
            subscribers = info.subscriberCount,
            description = info.description,
        )
        val tab = info.tabs.firstOrNull { "videos" in it.contentFilters }
        val videos = if (tab == null) {
            Paged(emptyList(), null)
        } else {
            val first = ChannelTabInfo.getInfo(yt, tab)
            tabPaged(tab, ch, first.relatedItems, first.nextPage)
        }
        ChannelPage(ch, videos)
    }

    private fun tabPaged(tab: ListLinkHandler, ch: ChannelItem, items: List<InfoItem>, next: Page?): Paged<VideoItem> =
        Paged(
            items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo(ch) },
            next?.let { n ->
                suspend {
                    io {
                        val p = ChannelTabInfo.getMoreItems(yt, tab, n)
                        tabPaged(tab, ch, p.items, p.nextPage)
                    }
                }
            }
        )

    // ------------------------------------------------- subscription feed (RSS)

    /** Latest uploads from the given channels, newest first, using YouTube's public RSS feeds. */
    suspend fun feed(channelIds: List<String>, perChannel: Int = 6): List<VideoItem> = coroutineScope {
        val gate = Semaphore(8)
        channelIds
            .map { id ->
                async(Dispatchers.IO) {
                    gate.withPermit { runCatching { rss(id, perChannel) }.getOrDefault(emptyList()) }
                }
            }
            .awaitAll()
            .flatten()
            .sortedByDescending { it.publishedMs }
    }

    private fun rss(channelId: String, limit: Int): List<VideoItem> {
        val req = Request.Builder()
            .url("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
            .header("User-Agent", DownloaderImpl.USER_AGENT)
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return emptyList()
            return parseAtom(r.body?.string().orEmpty(), channelId, limit)
        }
    }

    private fun parseAtom(xml: String, channelId: String, limit: Int): List<VideoItem> {
        val p = Xml.newPullParser()
        p.setInput(StringReader(xml))
        val out = mutableListOf<VideoItem>()
        var inEntry = false
        var inAuthor = false
        var id: String? = null
        var title = ""
        var author = ""
        var published = 0L
        var thumb: String? = null
        var views = -1L

        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT && out.size < limit) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "entry" -> { inEntry = true; id = null; title = ""; published = 0; thumb = null; views = -1 }
                    "author" -> inAuthor = true
                    "yt:videoId" -> if (inEntry) id = p.nextText()
                    "title" -> if (inEntry) title = p.nextText()
                    "name" -> if (inEntry && inAuthor) author = p.nextText()
                    "published" -> if (inEntry) {
                        published = runCatching { OffsetDateTime.parse(p.nextText()).toInstant().toEpochMilli() }
                            .getOrDefault(0L)
                    }
                    "media:thumbnail" -> if (inEntry) thumb = p.getAttributeValue(null, "url")
                    "media:statistics" -> if (inEntry) {
                        views = p.getAttributeValue(null, "views")?.toLongOrNull() ?: -1
                    }
                }
            } else if (ev == XmlPullParser.END_TAG) {
                when (p.name) {
                    "author" -> inAuthor = false
                    "entry" -> {
                        inEntry = false
                        id?.let {
                            out += VideoItem(
                                id = it, title = title, channelName = author,
                                channelUrl = channelUrl(channelId), channelAvatar = null,
                                thumbnail = thumb ?: "https://i.ytimg.com/vi/$it/hqdefault.jpg",
                                views = views, publishedMs = published,
                            )
                        }
                    }
                }
            }
            ev = p.next()
        }
        return out
    }

    // --------------------------------------------------------------- mapping

    private fun InfoItem.toItem(): Item? = when (this) {
        is StreamInfoItem -> toVideo()
        is ChannelInfoItem -> ChannelItem(
            id = url.substringAfterLast('/'),
            url = url,
            name = name.orEmpty(),
            avatar = thumbnails.pick(96),
            subscribers = subscriberCount,
            description = description,
        )
        else -> null // playlists are not shown
    }

    private fun StreamInfoItem.toVideo(fallback: ChannelItem? = null): VideoItem? {
        val id = videoIdFromUrl(url) ?: return null
        return VideoItem(
            id = id,
            title = name.orEmpty(),
            channelName = uploaderName?.ifBlank { null } ?: fallback?.name.orEmpty(),
            channelUrl = uploaderUrl?.ifBlank { null } ?: fallback?.url,
            channelAvatar = uploaderAvatars.pick(48) ?: fallback?.avatar,
            thumbnail = thumbnails.pick(360),
            durationSec = duration,
            views = viewCount,
            publishedText = textualUploadDate,
            isLive = streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM,
            isShort = isShortFormContent || url.contains("/shorts/"),
        )
    }

    private fun List<Image>?.pick(minHeight: Int): String? {
        if (isNullOrEmpty()) return null
        return filter { it.height >= minHeight }.minByOrNull { it.height }?.url
            ?: maxByOrNull { it.height }?.url
    }

    private fun descText(d: Description?): String {
        if (d == null) return ""
        return if (d.type == Description.HTML) {
            HtmlCompat.fromHtml(d.content.orEmpty(), HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
        } else d.content.orEmpty()
    }
}
