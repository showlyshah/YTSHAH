package com.viewtube.app.data

import java.util.Locale

/**
 * Private, on-device recommendation ranking. It uses only metadata already stored in the app;
 * it does not upload watch history or infer sensitive interests.
 */
object LocalRecommendationEngine {
    private val tokenPattern = Regex("[\\p{L}\\p{N}]{3,}")

    fun rank(
        candidates: List<VideoItem>,
        history: List<VideoItem>,
        subscriptions: List<ChannelItem>,
    ): List<VideoItem> {
        val unique = candidates.distinctBy { it.id }
        if (unique.size < 2 || history.isEmpty()) return unique

        val interests = HashMap<String, Double>()
        history.take(60).forEachIndexed { index, video ->
            val weight = 1.0 / (1.0 + index * 0.08)
            (tokens(video.title) + tokens(video.channelName)).forEach { token ->
                interests[token] = (interests[token] ?: 0.0) + weight
            }
        }
        val followed = subscriptions.map { it.name.trim().lowercase(Locale.ROOT) }.toSet()

        return unique.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<VideoItem>> { indexed ->
                    val video = indexed.value
                    val topicScore = (tokens(video.title) + tokens(video.channelName))
                        .distinct().sumOf { interests[it] ?: 0.0 }
                    val creatorScore = if (video.channelName.trim().lowercase(Locale.ROOT) in followed) 2.5 else 0.0
                    val freshnessScore = if (video.publishedMs > 0L) 0.15 else 0.0
                    topicScore + creatorScore + freshnessScore
                }.thenBy { it.index }
            )
            .map { it.value }
    }

    fun topicsFrom(videos: List<VideoItem>, limit: Int = 12): List<String> {
        val counts = linkedMapOf<String, Int>()
        videos.take(100).forEach { video ->
            tokens(video.title).forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        return counts.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    private fun tokens(value: String): List<String> =
        tokenPattern.findAll(value.lowercase(Locale.ROOT)).map { it.value }.toList()
}

/** A small local-only diagnostic journal. Messages are redacted before persistence. */
class DiagnosticJournal(private val store: android.content.SharedPreferences) {
    data class Event(val component: String, val errorType: String, val message: String, val timeMs: Long)

    @Synchronized
    fun record(component: String, error: Throwable) {
        val events = read().toMutableList()
        events.add(
            Event(
                component = component.take(48),
                errorType = error.javaClass.simpleName.take(80),
                message = redact(error.message.orEmpty()).take(240),
                timeMs = System.currentTimeMillis(),
            )
        )
        val json = org.json.JSONArray()
        events.takeLast(MAX_EVENTS).forEach { event ->
            json.put(org.json.JSONObject().apply {
                put("component", event.component)
                put("type", event.errorType)
                put("message", event.message)
                put("time", event.timeMs)
            })
        }
        store.edit().putString(KEY_EVENTS, json.toString()).apply()
    }

    @Synchronized
    fun read(): List<Event> = runCatching {
        val array = org.json.JSONArray(store.getString(KEY_EVENTS, "[]"))
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Event(item.optString("component"), item.optString("type"), item.optString("message"), item.optLong("time"))
        }
    }.getOrDefault(emptyList())

    fun clear() { store.edit().remove(KEY_EVENTS).apply() }

    private fun redact(raw: String): String = raw
        .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "[url]")
        .replace(Regex("(?i)(token|password|api[_-]?key|authorization)\\s*[:=]\\s*[^\\s,;]+"), "$1=[redacted]")

    private companion object {
        const val KEY_EVENTS = "diagnostic_events"
        const val MAX_EVENTS = 100
    }
}
